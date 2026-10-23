package com.klinker.android.send_message;

import android.content.Context;
import android.os.Build;
import android.telephony.SmsManager;

import timber.log.Timber;

public class SmsManagerFactory {

    /**
     * The SMS manager of the subscription, or of the default one for
     * {@link Settings#DEFAULT_SUBSCRIPTION_ID}
     */
    public static SmsManager createSmsManager(Context context, int subscriptionId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SmsManager manager = context.getSystemService(SmsManager.class);
            return subscriptionId == Settings.DEFAULT_SUBSCRIPTION_ID
                    ? manager
                    : manager.createForSubscriptionId(subscriptionId);
        }
        return createLegacySmsManager(subscriptionId);
    }

    /**
     * Before Android 12, the static getters are the only way to an SMS manager
     */
    @SuppressWarnings("deprecation")
    private static SmsManager createLegacySmsManager(int subscriptionId) {
        if (subscriptionId != Settings.DEFAULT_SUBSCRIPTION_ID) {
            try {
                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
            } catch (Exception e) {
                Timber.w(e);
            }
        }
        return SmsManager.getDefault();
    }
}
