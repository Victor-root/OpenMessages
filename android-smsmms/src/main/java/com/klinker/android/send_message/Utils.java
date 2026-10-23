package com.klinker.android.send_message;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.google.android.mms.util_alt.SqliteWrapper;

import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common methods to be used for data connectivity/sending messages ect
 *
 * @author Jake Klinker
 */
public class Utils {
    public static final int DEFAULT_SUBSCRIPTION_ID = 1;

    /**
     * Gets the current users phone number
     *
     * @param context is the context of the activity or service
     * @return a string of the phone number on the device
     */
    public static String getMyPhoneNumber(Context context) {
        return getMyPhoneNumberFromSubscription(context, DEFAULT_SUBSCRIPTION_ID);
    }

    /**
     * The number of this phone on the subscription, or on the default one for
     * {@link #DEFAULT_SUBSCRIPTION_ID}, or null when it isn't known
     */
    // Its replacement, SubscriptionManager.getPhoneNumber, demands READ_PHONE_NUMBERS, where this
    // one is open to the default SMS app through READ_SMS
    @SuppressWarnings("deprecation")
    public static String getMyPhoneNumberFromSubscription(Context context, int subscriptionId) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        if (DEFAULT_SUBSCRIPTION_ID != subscriptionId
                && SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            telephonyManager = telephonyManager.createForSubscriptionId(subscriptionId);
        }
        String number = telephonyManager.getLine1Number();
        return TextUtils.isEmpty(number) ? null : number;
    }

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     * @param context is the context of the activity or service
     * @param recipients is the set of people message is being sent to
     * @return the thread_id to use in the database
     */
    public static long getOrCreateThreadId(
            Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();

        for (String recipient : recipients) {
            if (isEmailAddress(recipient)) {
                recipient = extractAddrSpec(recipient);
            }

            uriBuilder.appendQueryParameter("recipient", recipient);
        }

        Uri uri = uriBuilder.build();
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uri, new String[]{"_id"}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    cursor.close();
                    return id;
                } else {

                }
            } finally {
                cursor.close();
            }
        }

        Random random = new Random();
        return random.nextLong();
        //throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

    private static boolean isEmailAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }

        String s = extractAddrSpec(address);
        Matcher match = EMAIL_ADDRESS_PATTERN.matcher(s);
        return match.matches();
    }

    private static final Pattern EMAIL_ADDRESS_PATTERN
            = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );

    private static final Pattern NAME_ADDR_EMAIL_PATTERN =
            Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

    private static String extractAddrSpec(String address) {
        Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

        if (match.matches()) {
            return match.group(2);
        }
        return address;
    }

    public static int getDefaultSubscriptionId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return SmsManager.getDefaultSmsSubscriptionId();
        } else {
            return DEFAULT_SUBSCRIPTION_ID;
        }
    }
}
