package com.keytron.clamguard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;

public class AppInstallReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "clamguard_protection";

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            final String packageName = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
            if (TextUtils.isEmpty(packageName) || context.getPackageName().equals(packageName)) {
                return;
            }

            final PendingResult pendingResult = goAsync();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PackageManager pm = context.getPackageManager();
                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                        String apkPath = appInfo.sourceDir;
                        if (!TextUtils.isEmpty(apkPath) && new File(apkPath).exists()) {
                            scanApk(context, apkPath, packageName);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (pendingResult != null) {
                            pendingResult.finish();
                        }
                    }
                }
            }).start();
        }
    }

    private void scanApk(Context context, String apkPath, String packageName) {
        SharedPreferences prefs = context.getSharedPreferences(ProtectionScheduler.PREFS_NAME, Context.MODE_PRIVATE);
        
        String clamscanPath = prefs.getString("clamscan_path", RuntimeAssetsManager.getClamscanPath(context));
        String databasePath = prefs.getString("database_path", RuntimeAssetsManager.getDatabasePath(context));

        ArrayList<ScanPlanner.ScanItem> items = new ArrayList<ScanPlanner.ScanItem>();
        File file = new File(apkPath);
        items.add(new ScanPlanner.ScanItem(apkPath, file.length(), file.lastModified()));
        ScanPlanner.ScanPlan plan = new ScanPlanner.ScanPlan(items, file.length());

        ClamScanner.Result result = ClamScanner.scanPlan(context, clamscanPath, databasePath, plan, null, null);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Real-time Protection", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_clamguard)
               .setAutoCancel(true)
               .setContentIntent(pi);

        if (result.threats != null && !result.threats.isEmpty()) {
            builder.setContentTitle("Threat detected in app!")
                   .setContentText("ClamGuard found " + result.threats.size() + " threats in " + packageName)
                   .setStyle(new android.app.Notification.BigTextStyle().bigText("Path: " + apkPath + "\nRun full scan to take action."));
        } else if (result.exitCode == 0) {
            builder.setContentTitle("App is Safe")
                   .setContentText(packageName + " passed ClamGuard security check.");
        } else {
            return; // Error scanning, do not disturb user
        }

        nm.notify(packageName.hashCode(), builder.build());
    }
}