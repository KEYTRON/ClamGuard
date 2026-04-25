package com.keytron.clamguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public final class ProtectionScheduler {
    public static final String PREFS_NAME = "clamguard_prefs";
    public static final String KEY_FRESHCLAM = "freshclam_path";
    public static final String KEY_DATABASE = "database_path";
    public static final String KEY_AUTO_UPDATE = "auto_update_enabled";
    public static final String KEY_LAST_DB_UPDATE = "last_db_update";
    public static final String KEY_BACKGROUND_MONITOR_ENABLED = "background_monitor_enabled";
    public static final String KEY_BACKGROUND_MONITOR_TARGET = "background_monitor_target";
    public static final String KEY_BACKGROUND_MONITOR_SINCE = "background_monitor_since";
    public static final String KEY_LAST_BACKGROUND_SCAN = "last_background_scan";
    public static final String KEY_LAST_BACKGROUND_STATUS = "last_background_status";
    public static final String KEY_LAST_BACKGROUND_STATUS_AT = "last_background_status_at";
    public static final String KEY_LAST_SCAN = "last_scan";
    public static final String KEY_LAST_SCAN_RESULT = "last_scan_result";
    public static final String KEY_LAST_THREAT_COUNT = "last_threat_count";
    public static final String ACTION_DAILY_UPDATE = "com.keytron.clamguard.action.DAILY_UPDATE";
    public static final String ACTION_BACKGROUND_SCAN = "com.keytron.clamguard.action.BACKGROUND_SCAN";

    private static final long UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long BACKGROUND_SCAN_INTERVAL_MS = 3L * 60L * 60L * 1000L;

    private ProtectionScheduler() {
    }

    public static void sync(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_AUTO_UPDATE, true);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent updatePendingIntent = buildUpdatePendingIntent(context);
        PendingIntent backgroundPendingIntent = buildBackgroundPendingIntent(context);
        alarmManager.cancel(updatePendingIntent);
        alarmManager.cancel(backgroundPendingIntent);
        if (!enabled) {
            scheduleBackgroundScan(context, alarmManager, backgroundPendingIntent);
        } else {
            long triggerAt = System.currentTimeMillis() + (30L * 60L * 1000L);
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    UPDATE_INTERVAL_MS,
                    updatePendingIntent
            );
        }
        scheduleBackgroundScan(context, alarmManager, backgroundPendingIntent);
    }

    public static void markDatabaseUpdated(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_DB_UPDATE, System.currentTimeMillis())
                .apply();
    }

    public static void enableBackgroundMonitor(Context context, String targetPath) {
        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BACKGROUND_MONITOR_ENABLED, true)
                .putString(KEY_BACKGROUND_MONITOR_TARGET, targetPath)
                .putLong(KEY_BACKGROUND_MONITOR_SINCE, now)
                .putLong(KEY_LAST_BACKGROUND_SCAN, 0L)
                .apply();
        sync(context);
    }

    public static boolean isBackgroundMonitorEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BACKGROUND_MONITOR_ENABLED, false);
    }

    public static String getBackgroundMonitorTarget(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BACKGROUND_MONITOR_TARGET, "");
    }

    public static void recordBackgroundStatus(Context context, String status) {
        Log.i("ClamGuard", status);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_BACKGROUND_STATUS, status)
                .putLong(KEY_LAST_BACKGROUND_STATUS_AT, System.currentTimeMillis())
                .apply();
    }

    public static void runAutoUpdateIfDue(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_AUTO_UPDATE, true)) {
            recordBackgroundStatus(appContext, "Автообновление пропущено: выключено в настройках");
            return;
        }
        if (!isNetworkAvailable(appContext)) {
            recordBackgroundStatus(appContext, "Автообновление пропущено: нет сети");
            return;
        }

        long lastUpdate = prefs.getLong(KEY_LAST_DB_UPDATE, 0L);
        if (lastUpdate > 0L && System.currentTimeMillis() - lastUpdate < UPDATE_INTERVAL_MS) {
            recordBackgroundStatus(appContext, "Автообновление пропущено: базы уже проверялись недавно");
            return;
        }

        try {
            RuntimeAssetsManager.ensureInstalled(appContext);
        } catch (Exception ignored) {
            recordBackgroundStatus(appContext, "Автообновление не запущено: runtime не подготовлен");
            return;
        }

        String freshclam = prefs.getString(KEY_FRESHCLAM, RuntimeAssetsManager.getFreshclamPath(appContext));
        String database = prefs.getString(KEY_DATABASE, RuntimeAssetsManager.getDatabasePath(appContext));
        if (TextUtils.isEmpty(freshclam) || TextUtils.isEmpty(database)) {
            recordBackgroundStatus(appContext, "Автообновление не запущено: пустой путь freshclam или баз");
            return;
        }
        if (!new File(freshclam).exists()) {
            recordBackgroundStatus(appContext, "Автообновление не запущено: freshclam не найден");
            return;
        }

        String command = shellQuote(freshclam)
                + " --user root"
                + " --stdout --datadir=" + shellQuote(database)
                + " --config-file=" + shellQuote(RuntimeAssetsManager.getFreshclamConfigPath(appContext))
                + " --cvdcertsdir=" + shellQuote(RuntimeAssetsManager.getCertsPath(appContext));
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
                recordBackgroundStatus(appContext, "Автообновление завершено успешно");
            } else {
                recordBackgroundStatus(appContext, "Автообновление завершилось с кодом " + exitCode);
            }
        } catch (Exception e) {
            recordBackgroundStatus(appContext, "Автообновление упало: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static void runBackgroundScanIfDue(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_BACKGROUND_MONITOR_ENABLED, false)) {
            recordBackgroundStatus(appContext, "Фоновая проверка пропущена: режим выключен");
            return;
        }

        long lastBackgroundScan = prefs.getLong(KEY_LAST_BACKGROUND_SCAN, 0L);
        if (lastBackgroundScan > 0L && System.currentTimeMillis() - lastBackgroundScan < BACKGROUND_SCAN_INTERVAL_MS) {
            recordBackgroundStatus(appContext, "Фоновая проверка пропущена: интервал ещё не истёк");
            return;
        }

        String target = prefs.getString(KEY_BACKGROUND_MONITOR_TARGET, "/sdcard");
        long modifiedAfter = prefs.getLong(KEY_BACKGROUND_MONITOR_SINCE, 0L);
        if (TextUtils.isEmpty(target)) {
            recordBackgroundStatus(appContext, "Фоновая проверка не запущена: пустой целевой путь");
            return;
        }

        try {
            RuntimeAssetsManager.ensureInstalled(appContext);
        } catch (Exception e) {
            recordBackgroundStatus(appContext, "Фоновая проверка не запущена: runtime не подготовлен: " + e.getMessage());
            return;
        }

        String clamscan = prefs.getString("clamscan_path", RuntimeAssetsManager.getClamscanPath(appContext));
        String database = prefs.getString(KEY_DATABASE, RuntimeAssetsManager.getDatabasePath(appContext));
        if (TextUtils.isEmpty(clamscan) || TextUtils.isEmpty(database)) {
            recordBackgroundStatus(appContext, "Фоновая проверка не запущена: пустой путь clamscan или баз");
            return;
        }
        if (!new File(clamscan).exists() || !new File(database).exists()) {
            recordBackgroundStatus(appContext, "Фоновая проверка не запущена: clamscan или база не найдены");
            return;
        }

        recordBackgroundStatus(appContext, "Фоновая проверка: планирую новые файлы в " + target);
        ScanPlanner.ScanPlan plan = ScanPlanner.buildPlan(appContext, target, true, modifiedAfter);
        long now = System.currentTimeMillis();
        if (plan.items.isEmpty()) {
            prefs.edit()
                    .putLong(KEY_BACKGROUND_MONITOR_SINCE, now)
                    .putLong(KEY_LAST_BACKGROUND_SCAN, now)
                    .apply();
            recordBackgroundStatus(appContext, "Фоновая проверка завершена: новых файлов нет");
            return;
        }

        recordBackgroundStatus(appContext, "Фоновая проверка: найдено новых файлов " + plan.items.size());
        HashSet<String> ignoredThreats = new HashSet<String>(
                prefs.getStringSet("ignored_threats", new HashSet<String>())
        );
        ClamScanner.Result result = ClamScanner.scanPlan(appContext, clamscan, database, plan, ignoredThreats, null);

        SharedPreferences.Editor editor = prefs.edit();
        if (result.exitCode <= 1) {
            editor.putLong(KEY_BACKGROUND_MONITOR_SINCE, now);
            editor.putLong(KEY_LAST_BACKGROUND_SCAN, now);
            editor.putString(KEY_LAST_SCAN, new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date(now)));
            editor.putString(KEY_LAST_SCAN_RESULT, result.threats.isEmpty() ? "clean" : "threats");
            editor.putInt(KEY_LAST_THREAT_COUNT, result.threats.size());
            recordBackgroundStatus(appContext, "Фоновая проверка завершена: код " + result.exitCode + ", угроз " + result.threats.size());
        } else {
            recordBackgroundStatus(appContext, "Фоновая проверка завершилась с ошибкой: код " + result.exitCode);
        }
        editor.apply();
    }

    private static PendingIntent buildUpdatePendingIntent(Context context) {
        Intent intent = new Intent(context, UpdateReceiver.class);
        intent.setAction(ACTION_DAILY_UPDATE);
        return PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent buildBackgroundPendingIntent(Context context) {
        Intent intent = new Intent(context, UpdateReceiver.class);
        intent.setAction(ACTION_BACKGROUND_SCAN);
        return PendingIntent.getBroadcast(
                context,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void scheduleBackgroundScan(Context context, AlarmManager alarmManager, PendingIntent pendingIntent) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_BACKGROUND_MONITOR_ENABLED, false)) {
            return;
        }
        long triggerAt = System.currentTimeMillis() + (20L * 60L * 1000L);
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                BACKGROUND_SCAN_INTERVAL_MS,
                pendingIntent
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
