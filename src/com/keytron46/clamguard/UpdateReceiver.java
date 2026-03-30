package com.keytron46.clamguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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

        final PendingResult pendingResult = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ProtectionScheduler.ACTION_DAILY_UPDATE.equals(action)) {
                        ProtectionScheduler.runAutoUpdateIfDue(context);
                    } else if (ProtectionScheduler.ACTION_BACKGROUND_SCAN.equals(action)) {
                        ProtectionScheduler.runBackgroundScanIfDue(context);
                    }
                } finally {
                    pendingResult.finish();
                }
            }
        }, "clamguard-background-work").start();
    }
}
