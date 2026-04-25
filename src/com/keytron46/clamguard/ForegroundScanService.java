package com.keytron.clamguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForegroundScanService extends Service {
    private static final String TAG = "ClamGuard";
    private static final String CHANNEL_ID = "clamguard_background_work";
    private static final int NOTIFICATION_ID = 4601;
    private static final long PROGRESS_WRITE_INTERVAL_MS = 1000L;

    public static final String ACTION_MANUAL_SCAN = "com.keytron.clamguard.action.MANUAL_SCAN";
    public static final String EXTRA_MODE_LABEL = "mode_label";
    public static final String EXTRA_TARGET_PATH = "target_path";
    public static final String EXTRA_INCLUDE_INSTALLED_APKS = "include_installed_apks";
    public static final String EXTRA_MODIFIED_AFTER = "modified_after";
    public static final String EXTRA_ENABLE_BACKGROUND_MONITOR = "enable_background_monitor";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running;
    private long lastProgressWriteAt;

    public static void start(Context context, String action) {
        Intent intent = new Intent(context, ForegroundScanService.class);
        intent.setAction(action);
        startIntent(context, intent);
    }

    public static void startManualScan(Context context,
                                       String modeLabel,
                                       String targetPath,
                                       boolean includeInstalledApks,
                                       long modifiedAfter,
                                       boolean enableBackgroundMonitor) {
        Intent intent = new Intent(context, ForegroundScanService.class);
        intent.setAction(ACTION_MANUAL_SCAN);
        intent.putExtra(EXTRA_MODE_LABEL, modeLabel);
        intent.putExtra(EXTRA_TARGET_PATH, targetPath);
        intent.putExtra(EXTRA_INCLUDE_INSTALLED_APKS, includeInstalledApks);
        intent.putExtra(EXTRA_MODIFIED_AFTER, modifiedAfter);
        intent.putExtra(EXTRA_ENABLE_BACKGROUND_MONITOR, enableBackgroundMonitor);
        startIntent(context, intent);
    }

    private static void startIntent(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        final String action = intent != null ? intent.getAction() : null;
        if (!ProtectionScheduler.ACTION_DAILY_UPDATE.equals(action)
                && !ProtectionScheduler.ACTION_BACKGROUND_SCAN.equals(action)
                && !ACTION_MANUAL_SCAN.equals(action)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Фоновая защита активна", describeAction(action)));

        synchronized (this) {
            if (running) {
                Log.i(TAG, "Background work already running, ignoring action: " + action);
                return START_REDELIVER_INTENT;
            }
            running = true;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Foreground background work started: " + action);
                    if (ProtectionScheduler.ACTION_DAILY_UPDATE.equals(action)) {
                        ProtectionScheduler.runAutoUpdateIfDue(ForegroundScanService.this);
                    } else if (ProtectionScheduler.ACTION_BACKGROUND_SCAN.equals(action)) {
                        ProtectionScheduler.runBackgroundScanIfDue(ForegroundScanService.this);
                    } else if (ACTION_MANUAL_SCAN.equals(action)) {
                        runManualScan(intent);
                    }
                    Log.i(TAG, "Foreground background work finished: " + action);
                } catch (Throwable t) {
                    Log.e(TAG, "Foreground background work failed: " + action, t);
                    ProtectionScheduler.recordBackgroundStatus(
                            ForegroundScanService.this,
                            "Ошибка фоновой работы: " + t.getClass().getSimpleName() + ": " + t.getMessage()
                    );
                } finally {
                    synchronized (ForegroundScanService.this) {
                        running = false;
                    }
                    stopForeground(true);
                    stopSelf(startId);
                }
            }
        });
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private String describeAction(String action) {
        if (ProtectionScheduler.ACTION_DAILY_UPDATE.equals(action)) {
            return "Обновляю базы ClamAV";
        }
        if (ACTION_MANUAL_SCAN.equals(action)) {
            return "Выполняю сканирование";
        }
        return "Проверяю новые файлы";
    }

    private void runManualScan(Intent intent) {
        final String modeLabel = intent.getStringExtra(EXTRA_MODE_LABEL);
        final String targetPath = intent.getStringExtra(EXTRA_TARGET_PATH);
        final boolean includeInstalledApks = intent.getBooleanExtra(EXTRA_INCLUDE_INSTALLED_APKS, false);
        final long modifiedAfter = intent.getLongExtra(EXTRA_MODIFIED_AFTER, 0L);
        final boolean enableBackgroundMonitor = intent.getBooleanExtra(EXTRA_ENABLE_BACKGROUND_MONITOR, false);
        final SharedPreferences prefs = getSharedPreferences(ProtectionScheduler.PREFS_NAME, MODE_PRIVATE);

        String mode = TextUtils.isEmpty(modeLabel) ? "Сканирование" : modeLabel;
        String target = TextUtils.isEmpty(targetPath) ? "/sdcard" : targetPath;
        markManualScanStarted(prefs, mode, target);
        updateNotification("Сканирование ClamGuard", "Подготавливаю движок");

        try {
            RuntimeAssetsManager.ensureInstalled(this);
        } catch (Exception e) {
            finishManualScan(prefs, mode, 2, 0, "Ошибка подготовки runtime: " + e.getMessage(), new HashSet<String>());
            return;
        }

        String clamscan = prefs.getString("clamscan_path", RuntimeAssetsManager.getClamscanPath(this));
        String database = prefs.getString(ProtectionScheduler.KEY_DATABASE, RuntimeAssetsManager.getDatabasePath(this));
        if (TextUtils.isEmpty(clamscan) || TextUtils.isEmpty(database)
                || !new File(clamscan).exists() || !new File(database).exists()) {
            finishManualScan(prefs, mode, 2, 0, "clamscan или база сигнатур не найдены", new HashSet<String>());
            return;
        }
        if (!RuntimeAssetsManager.hasUsableDatabase(new File(database))) {
            finishManualScan(prefs, mode, 2, 0, "Базы сигнатур не установлены. Нажмите «Обновить базы».", new HashSet<String>());
            return;
        }

        updateManualScanStatus(prefs, mode, "Планирую файлы для проверки", 0L, 0L, 0, 0, "");
        updateNotification("Сканирование ClamGuard", "Планирую файлы для проверки");
        ScanPlanner.ScanPlan plan = ScanPlanner.buildPlan(this, target, includeInstalledApks, modifiedAfter);
        if (plan.items.isEmpty()) {
            finishManualScan(prefs, mode, 0, 0, "Нет доступных файлов для проверки", new HashSet<String>());
            return;
        }

        updateManualScanStatus(prefs, mode, "Сканирование выполняется", 0L, plan.totalBytes, 0, plan.items.size(), plan.items.get(0).path);
        updateNotification("Сканирование ClamGuard", "0% · 0 / " + plan.items.size() + " файлов");
        HashSet<String> ignoredThreats = new HashSet<String>(prefs.getStringSet("ignored_threats", new HashSet<String>()));
        ClamScanner.Result result = ClamScanner.scanPlan(this, clamscan, database, plan, ignoredThreats, new ClamScanner.ProgressCallback() {
            @Override
            public void onLog(String line) {
                Log.i(TAG, "clamscan: " + line);
            }

            @Override
            public void onProgress(long scannedBytes, long totalBytes, int scannedFiles, int totalFiles, ScanPlanner.ScanItem currentItem) {
                long now = System.currentTimeMillis();
                if (now - lastProgressWriteAt < PROGRESS_WRITE_INTERVAL_MS && scannedFiles < totalFiles) {
                    return;
                }
                lastProgressWriteAt = now;
                String currentPath = currentItem != null ? currentItem.path : "";
                updateManualScanStatus(prefs, mode, "Сканирование выполняется", scannedBytes, totalBytes, scannedFiles, totalFiles, currentPath);
                updateNotification("Сканирование ClamGuard", formatProgress(scannedBytes, totalBytes, scannedFiles, totalFiles));
            }
        });

        boolean ok = result.exitCode == 0 || result.exitCode == 1;
        if (ok && enableBackgroundMonitor) {
            ProtectionScheduler.enableBackgroundMonitor(this, target);
        }
        finishManualScan(
                prefs,
                mode,
                result.exitCode,
                result.threats.size(),
                ok ? "Сканирование завершено" : "Сканирование завершилось с ошибкой",
                new HashSet<String>(result.threats)
        );
    }

    private void markManualScanStarted(SharedPreferences prefs, String mode, String target) {
        lastProgressWriteAt = 0L;
        prefs.edit()
                .putBoolean(ScanState.KEY_MANUAL_SCAN_RUNNING, true)
                .putString(ScanState.KEY_MANUAL_SCAN_MODE, mode)
                .putString(ScanState.KEY_MANUAL_SCAN_TARGET, target)
                .putString(ScanState.KEY_MANUAL_SCAN_STATUS, "Сканирование запускается")
                .putLong(ScanState.KEY_MANUAL_SCAN_SCANNED_BYTES, 0L)
                .putLong(ScanState.KEY_MANUAL_SCAN_TOTAL_BYTES, 0L)
                .putInt(ScanState.KEY_MANUAL_SCAN_SCANNED_FILES, 0)
                .putInt(ScanState.KEY_MANUAL_SCAN_TOTAL_FILES, 0)
                .putString(ScanState.KEY_MANUAL_SCAN_CURRENT_PATH, "")
                .putString(ScanState.KEY_MANUAL_SCAN_RESULT, "running")
                .putInt(ScanState.KEY_MANUAL_SCAN_EXIT_CODE, -1)
                .putStringSet(ScanState.KEY_MANUAL_SCAN_THREATS, new HashSet<String>())
                .putLong(ScanState.KEY_MANUAL_SCAN_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    private void updateManualScanStatus(SharedPreferences prefs,
                                        String mode,
                                        String status,
                                        long scannedBytes,
                                        long totalBytes,
                                        int scannedFiles,
                                        int totalFiles,
                                        String currentPath) {
        prefs.edit()
                .putBoolean(ScanState.KEY_MANUAL_SCAN_RUNNING, true)
                .putString(ScanState.KEY_MANUAL_SCAN_MODE, mode)
                .putString(ScanState.KEY_MANUAL_SCAN_STATUS, status)
                .putLong(ScanState.KEY_MANUAL_SCAN_SCANNED_BYTES, scannedBytes)
                .putLong(ScanState.KEY_MANUAL_SCAN_TOTAL_BYTES, totalBytes)
                .putInt(ScanState.KEY_MANUAL_SCAN_SCANNED_FILES, scannedFiles)
                .putInt(ScanState.KEY_MANUAL_SCAN_TOTAL_FILES, totalFiles)
                .putString(ScanState.KEY_MANUAL_SCAN_CURRENT_PATH, currentPath)
                .putLong(ScanState.KEY_MANUAL_SCAN_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    private void finishManualScan(SharedPreferences prefs,
                                  String mode,
                                  int exitCode,
                                  int threatCount,
                                  String status,
                                  HashSet<String> threats) {
        long now = System.currentTimeMillis();
        boolean ok = exitCode == 0 || exitCode == 1;
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(ScanState.KEY_MANUAL_SCAN_RUNNING, false)
                .putString(ScanState.KEY_MANUAL_SCAN_MODE, mode)
                .putString(ScanState.KEY_MANUAL_SCAN_STATUS, status)
                .putString(ScanState.KEY_MANUAL_SCAN_RESULT, ok ? (threatCount > 0 ? "threats" : "clean") : "error")
                .putInt(ScanState.KEY_MANUAL_SCAN_EXIT_CODE, exitCode)
                .putStringSet(ScanState.KEY_MANUAL_SCAN_THREATS, threats)
                .putLong(ScanState.KEY_MANUAL_SCAN_UPDATED_AT, now);
        if (ok) {
            editor.putString(ProtectionScheduler.KEY_LAST_SCAN, new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date(now)))
                    .putString(ProtectionScheduler.KEY_LAST_SCAN_RESULT, threatCount > 0 ? "threats" : "clean")
                    .putInt(ProtectionScheduler.KEY_LAST_THREAT_COUNT, threatCount);
        }
        editor.apply();
        updateNotification("Сканирование ClamGuard", status);
    }

    private String formatProgress(long scannedBytes, long totalBytes, int scannedFiles, int totalFiles) {
        String percent = totalBytes > 0L ? String.valueOf(scannedBytes * 100L / totalBytes) + "%" : "...";
        return percent + " · " + scannedFiles + " / " + totalFiles + " файлов";
    }

    private void updateNotification(String title, String text) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text));
    }

    private Notification buildNotification(String title, String text) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_clamguard)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Фоновая защита ClamGuard",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Сканирование новых файлов и обслуживание баз ClamAV");
        notificationManager.createNotificationChannel(channel);
    }
}
