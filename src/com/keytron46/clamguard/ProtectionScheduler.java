package com.keytron46.clamguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import java.io.File;

public final class ProtectionScheduler {
    public static final String PREFS_NAME = "clamguard_prefs";
    public static final String KEY_FRESHCLAM = "freshclam_path";
    public static final String KEY_DATABASE = "database_path";
    public static final String KEY_AUTO_UPDATE = "auto_update_enabled";
    public static final String KEY_LAST_DB_UPDATE = "last_db_update";
    public static final String ACTION_DAILY_UPDATE = "com.keytron46.clamguard.action.DAILY_UPDATE";

    private static final long UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private ProtectionScheduler() {
    }

    public static void sync(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_AUTO_UPDATE, true);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = buildPendingIntent(context);
        alarmManager.cancel(pendingIntent);
        if (!enabled) {
            return;
        }

        long triggerAt = System.currentTimeMillis() + (30L * 60L * 1000L);
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                UPDATE_INTERVAL_MS,
                pendingIntent
        );
    }

    public static void markDatabaseUpdated(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_DB_UPDATE, System.currentTimeMillis())
                .apply();
    }

    public static void runAutoUpdateIfDue(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_AUTO_UPDATE, true)) {
            return;
        }
        if (!isNetworkAvailable(appContext)) {
            return;
        }

        long lastUpdate = prefs.getLong(KEY_LAST_DB_UPDATE, 0L);
        if (lastUpdate > 0L && System.currentTimeMillis() - lastUpdate < UPDATE_INTERVAL_MS) {
            return;
        }

        try {
            RuntimeAssetsManager.ensureInstalled(appContext);
        } catch (Exception ignored) {
            return;
        }

        String freshclam = prefs.getString(KEY_FRESHCLAM, RuntimeAssetsManager.getFreshclamPath(appContext));
        String database = prefs.getString(KEY_DATABASE, RuntimeAssetsManager.getDatabasePath(appContext));
        if (TextUtils.isEmpty(freshclam) || TextUtils.isEmpty(database)) {
            return;
        }
        if (!new File(freshclam).exists()) {
            return;
        }

        String command = shellQuote(freshclam)
                + " --stdout --datadir=" + shellQuote(database)
                + " --config-file=" + shellQuote(RuntimeAssetsManager.getFreshclamConfigPath(appContext));
        String runtimeRoot = RuntimeAssetsManager.getRuntimeRoot(appContext);
        String nativeLibDir = appContext.getApplicationInfo().nativeLibraryDir;
        command = "LD_LIBRARY_PATH=" + shellQuote(nativeLibDir) + " "
                + "HOME=" + shellQuote(runtimeRoot) + " "
                + "SSL_CERT_FILE=" + shellQuote(runtimeRoot + "/usr/etc/tls/cert.pem") + " "
                + "OPENSSL_CONF=" + shellQuote(runtimeRoot + "/usr/etc/tls/openssl.cnf") + " "
                + command;
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                markDatabaseUpdated(appContext);
            }
        } catch (Exception ignored) {
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, UpdateReceiver.class);
        intent.setAction(ACTION_DAILY_UPDATE);
        return PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
