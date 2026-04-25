package com.keytron.clamguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        final String action = intent.getAction();
        if (!ProtectionScheduler.ACTION_DAILY_UPDATE.equals(action)
                && !ProtectionScheduler.ACTION_BACKGROUND_SCAN.equals(action)) {
            return;
        }

        try {
            ForegroundScanService.start(context.getApplicationContext(), action);
        } catch (RuntimeException e) {
            Log.e("ClamGuard", "Failed to start foreground background worker", e);
            ProtectionScheduler.recordBackgroundStatus(
                    context,
                    "Не удалось запустить foreground service: " + e.getClass().getSimpleName()
            );
        }
    }
}
