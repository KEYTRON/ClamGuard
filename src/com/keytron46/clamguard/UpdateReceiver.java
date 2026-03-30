package com.keytron46.clamguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !ProtectionScheduler.ACTION_DAILY_UPDATE.equals(intent.getAction())) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ProtectionScheduler.runAutoUpdateIfDue(context);
                } finally {
                    pendingResult.finish();
                }
            }
        }, "clamguard-auto-update").start();
    }
}
