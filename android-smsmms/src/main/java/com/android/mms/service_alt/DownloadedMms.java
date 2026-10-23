/*
 * Copyright (C) 2015 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.service_alt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.provider.Telephony;
import android.text.TextUtils;
import com.android.mms.MmsConfig;
import timber.log.Timber;

import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.google.android.mms.util_alt.SqliteWrapper;

/**
 * Stores an MMS the system has downloaded in place of the notification that announced it
 */
public final class DownloadedMms {
    private static final String LOCATION_SELECTION =
            Telephony.Mms.MESSAGE_TYPE + "=? AND " + Telephony.Mms.CONTENT_LOCATION + " =?";

    private DownloadedMms() {
    }

    public static Uri persist(Context context, byte[] response, String locationUrl, int subId,
                              String creator) {
        Timber.d("DownloadedMms.persist");
        if (response == null || response.length < 1) {
            Timber.e("DownloadedMms.persist: empty response");
            // Update the retrieve status of the NotificationInd
            final ContentValues values = new ContentValues(1);
            values.put(Telephony.Mms.RETRIEVE_STATUS, PduHeaders.RETRIEVE_STATUS_ERROR_END);
            SqliteWrapper.update(
                    context,
                    context.getContentResolver(),
                    Telephony.Mms.CONTENT_URI,
                    values,
                    LOCATION_SELECTION,
                    new String[]{
                            Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                            locationUrl
                    });
            return null;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final GenericPdu pdu =
                    (new PduParser(response, MmsConfig.getSupportMmsContentDisposition())).parse();
            if (pdu == null || !(pdu instanceof RetrieveConf)) {
                Timber.e("DownloadedMms.persist: invalid parsed PDU");

                // Update the error type of the NotificationInd
                setErrorType(context, locationUrl, Telephony.MmsSms.ERR_TYPE_MMS_PROTO_PERMANENT);
                return null;
            }
            final RetrieveConf retrieveConf = (RetrieveConf) pdu;
            // Store the downloaded message
            final PduPersister persister = PduPersister.getPduPersister(context);
            final Uri messageUri = persister.persist(
                    pdu,
                    Telephony.Mms.Inbox.CONTENT_URI,
                    true/*createThreadId*/,
                    true/*groupMmsEnabled*/,
                    null/*preOpenedFiles*/,
                    subId);
            if (messageUri == null) {
                Timber.e("DownloadedMms.persist: can not persist message");
                return null;
            }
            // Update some of the properties of the message
            final ContentValues values = new ContentValues();
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.READ, 0);
            values.put(Telephony.Mms.SEEN, 0);
            if (!TextUtils.isEmpty(creator)) {
                values.put(Telephony.Mms.CREATOR, creator);
            }

            if (SubscriptionIdChecker.getInstance(context).canUseSubscriptionId()) {
                values.put(Telephony.Mms.SUBSCRIPTION_ID, subId);
            }

            try {
                values.put(Telephony.Mms.DATE_SENT, retrieveConf.getDate());
            } catch (Exception e) {
            }

            try {
                int rowsUpdated = SqliteWrapper.update(
                        context,
                        context.getContentResolver(),
                        messageUri,
                        values,
                        null/*where*/,
                        null/*selectionArg*/);
                if (rowsUpdated != 1) {
                    Timber.e("DownloadedMms.persist: can not update message");
                }

            } catch (SQLiteException ex) {
                // if the exception says no such column like sub_id, ignore this value and try update
                if (ex.getMessage().contains("no such column: sub_id")) {
                    // ignore this column and proceed.
                    values.remove(Telephony.Mms.SUBSCRIPTION_ID);
                    int rowsUpdated = SqliteWrapper.update(context, context.getContentResolver(), messageUri,
                            values, null/*where*/, null/*selectionArg*/);
                    Timber.i("DownloadedMms.persist: updated " + rowsUpdated + " message without sub_id info");
                } else {
                    throw ex;
                }
            }

            // Delete the corresponding NotificationInd
            SqliteWrapper.delete(context,
                    context.getContentResolver(),
                    Telephony.Mms.CONTENT_URI,
                    LOCATION_SELECTION,
                    new String[]{
                            Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                            locationUrl
                    });

            return messageUri;
        } catch (MmsException e) {
            Timber.e(e, "DownloadedMms.persist: can not persist message");
        } catch (SQLiteException e) {
            Timber.e(e, "DownloadedMms.persist: can not update message");
        } catch (RuntimeException e) {
            Timber.e(e, "DownloadedMms.persist: can not parse response");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

    private static Long getId(Context context, String location) {
        String selection = Telephony.Mms.CONTENT_LOCATION + " = ?";
        String[] selectionArgs = new String[] { location };
        Cursor c = SqliteWrapper.query(
                context, context.getContentResolver(),
                Telephony.Mms.CONTENT_URI, new String[] { Telephony.Mms._ID },
                selection, selectionArgs, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getLong(c.getColumnIndex(Telephony.Mms._ID));
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    private static void setErrorType(Context context, String locationUrl, int errorType) {
        Long msgId = getId(context, locationUrl);
        if (msgId == null) {
            return;
        }

        Uri.Builder uriBuilder = Telephony.MmsSms.PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        uriBuilder.appendQueryParameter("message", String.valueOf(msgId));

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uriBuilder.build(), null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                ContentValues values = new ContentValues();
                values.put(Telephony.MmsSms.PendingMessages.ERROR_TYPE, errorType);

                int columnIndex = cursor.getColumnIndexOrThrow(
                        Telephony.MmsSms.PendingMessages._ID);
                long id = cursor.getLong(columnIndex);

                SqliteWrapper.update(context, context.getContentResolver(),
                        Telephony.MmsSms.PendingMessages.CONTENT_URI,
                        values, Telephony.MmsSms.PendingMessages._ID + "=" + id, null);
            }
        } finally {
            cursor.close();
        }
    }
}
