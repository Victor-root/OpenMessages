/*
 * Copyright 2014 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.transaction;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Inbox;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.mms.MmsConfig;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.DeliveryInd;
import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.ReadOrigInd;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.SmsManagerFactory;
import timber.log.Timber;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.provider.Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION;
import static android.provider.Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION;
import static com.google.android.mms.pdu_alt.PduHeaders.MESSAGE_TYPE_DELIVERY_IND;
import static com.google.android.mms.pdu_alt.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
import static com.google.android.mms.pdu_alt.PduHeaders.MESSAGE_TYPE_READ_ORIG_IND;

/**
 * Receives the WAP push of an MMS, stores what it announces and has the system download the
 * message itself.
 */
public class PushReceiver extends BroadcastReceiver {

    static final String[] PROJECTION = new String[] {
            Mms.CONTENT_LOCATION,
            Mms.LOCKED
    };

    static final int COLUMN_CONTENT_LOCATION      = 0;

    /**
     * Group messaging is always on: messages to several people are kept in one conversation.
     */
    private static final boolean GROUP_MMS = true;

    private static Set<String> downloadedUrls = new HashSet<String>();
    private static final ExecutorService PUSH_RECEIVER_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.v(intent.getAction() + " " + intent.getType());
        if ((intent.getAction().equals(WAP_PUSH_DELIVER_ACTION) || intent.getAction().equals(WAP_PUSH_RECEIVED_ACTION))
                && ContentType.MMS_MESSAGE.equals(intent.getType())) {
            Timber.v("Received PUSH Intent: %s", intent);

            MmsConfig.init(context);
            PendingResult pendingResult = goAsync();
            PUSH_RECEIVER_EXECUTOR.execute(() -> {
                try {
                    receivePush(context, intent);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }

    private static void receivePush(Context context, Intent intent) {
        Timber.v("receiving a new mms message");

        // Get raw PDU push-data from the message and parse it
        byte[] pushData = intent.getByteArrayExtra("data");
        PduParser parser = new PduParser(pushData);
        GenericPdu pdu = parser.parse();

        if (null == pdu) {
            Timber.e("Invalid PUSH data");
            return;
        }

        PduPersister p = PduPersister.getPduPersister(context);
        ContentResolver cr = context.getContentResolver();
        int type = pdu.getMessageType();
        long threadId = -1;
        int subId = intent.getIntExtra("subscription", Settings.DEFAULT_SUBSCRIPTION_ID);

        try {
            switch (type) {
                case MESSAGE_TYPE_DELIVERY_IND:
                case MESSAGE_TYPE_READ_ORIG_IND: {
                    threadId = findThreadId(context, pdu, type);
                    if (threadId == -1) {
                        // The associated SendReq isn't found, therefore skip
                        // processing this PDU.
                        break;
                    }

                    Uri uri = p.persist(pdu, Uri.parse("content://mms/inbox"), true,
                            GROUP_MMS, null, subId);
                    // Update thread ID for ReadOrigInd & DeliveryInd.
                    ContentValues values = new ContentValues(1);
                    values.put(Mms.THREAD_ID, threadId);
                    SqliteWrapper.update(context, cr, uri, values, null, null);
                    break;
                }
                case MESSAGE_TYPE_NOTIFICATION_IND: {
                    NotificationInd nInd = (NotificationInd) pdu;

                    Bundle configOverrides = SmsManagerFactory.createSmsManager(subId).getCarrierConfigValues();
                    boolean appendTransactionId = configOverrides.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID);

                    if (appendTransactionId) {
                        Timber.v("appending the transaction ID, based on the SMS manager overrides");
                    }

                    if (MmsConfig.getTransIdEnabled() || appendTransactionId) {
                        byte [] contentLocation = nInd.getContentLocation();
                        if ('=' == contentLocation[contentLocation.length - 1]) {
                            byte [] transactionId = nInd.getTransactionId();
                            byte [] contentLocationWithId = new byte [contentLocation.length
                                                                      + transactionId.length];
                            System.arraycopy(contentLocation, 0, contentLocationWithId,
                                    0, contentLocation.length);
                            System.arraycopy(transactionId, 0, contentLocationWithId,
                                    contentLocation.length, transactionId.length);
                            nInd.setContentLocation(contentLocationWithId);
                        }
                    }

                    // Save the pdu. If we can start downloading the real pdu immediately,
                    // don't allow persist() to create a thread for the notificationInd
                    // because it causes UI jank.
                    Uri uri = p.persist(pdu, Inbox.CONTENT_URI, isDataSuspended(context), GROUP_MMS,
                            null, subId);

                    String location;
                    try {
                        location = getContentLocation(context, uri);
                    } catch (MmsException ex) {
                        location = p.getContentLocationFromPduHeader(pdu);
                        if (TextUtils.isEmpty(location)) {
                            throw ex;
                        }
                    }

                    if (downloadedUrls.contains(location)) {
                        Timber.v("already added this download, don't download again");
                        return;
                    } else {
                        downloadedUrls.add(location);
                    }

                    DownloadManager.getInstance().downloadMultimediaMessage(context, location, uri, subId);
                    break;
                }
                default:
                    Timber.e("Received unrecognized PDU.");
            }
        } catch (MmsException e) {
            Timber.e(e, "Failed to save the data from PUSH: type=%s", type);
        } catch (RuntimeException e) {
            Timber.e(e, "Unexpected RuntimeException.");
        }

        Timber.v("PUSH Intent processed.");
    }

    /**
     * Whether mobile data is suspended, in which case the message can't be downloaded right away
     */
    private static boolean isDataSuspended(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getDataState() == TelephonyManager.DATA_SUSPENDED;
    }

    public static String getContentLocation(Context context, Uri uri)
            throws MmsException {
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uri, PROJECTION, null, null, null);

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    String location = cursor.getString(COLUMN_CONTENT_LOCATION);
                    cursor.close();
                    return location;
                }
            } finally {
                cursor.close();
            }
        }

        throw new MmsException("Cannot get X-Mms-Content-Location from: " + uri);
    }

    private static long findThreadId(Context context, GenericPdu pdu, int type) {
        String messageId;

        if (type == MESSAGE_TYPE_DELIVERY_IND) {
            messageId = new String(((DeliveryInd) pdu).getMessageId());
        } else {
            messageId = new String(((ReadOrigInd) pdu).getMessageId());
        }

        StringBuilder sb = new StringBuilder('(');
        sb.append(Mms.MESSAGE_ID);
        sb.append('=');
        sb.append(DatabaseUtils.sqlEscapeString(messageId));
        sb.append(" AND ");
        sb.append(Mms.MESSAGE_TYPE);
        sb.append('=');
        sb.append(PduHeaders.MESSAGE_TYPE_SEND_REQ);
        // TODO ContentResolver.query() appends closing ')' to the selection argument
        // sb.append(')');

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            Mms.CONTENT_URI, new String[] { Mms.THREAD_ID },
                            sb.toString(), null, null);
        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    cursor.close();
                    return id;
                }
            } finally {
                cursor.close();
            }
        }

        return -1;
    }
}
