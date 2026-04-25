package com.keytron.clamguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "clamguard_prefs";
    private static final String KEY_CLAMSCAN = "clamscan_path";
    private static final String KEY_DATABASE = "database_path";
    private static final String KEY_TARGET = "target_path";
    private static final String KEY_LAST_SCAN = "last_scan";
    private static final String KEY_LAST_SCAN_RESULT = "last_scan_result";
    private static final String KEY_LAST_THREAT_COUNT = "last_threat_count";
    private static final String KEY_IGNORED_THREATS = "ignored_threats";
    private static final String KEY_AUTO_UPDATE = "auto_update_enabled";

    private static final String MODULE_ROOT = "/data/adb/modules/clamguard";
    private static final String DEFAULT_TARGET = "/sdcard";
    private static final String QUICK_SCAN_TARGET = "/sdcard/Download";
    private static final String LEGACY_ROOT = "/data/local/tmp/clamav";

    private static final int REQ_NOTIFICATIONS = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> uniqueThreats = new LinkedHashSet<String>();
    private final List<String> threats = new ArrayList<String>();
    private final Runnable scanStateRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshManualScanState();
            if (busy) {
                mainHandler.postDelayed(this, 1000L);
            }
        }
    };

    private View tabHome, tabScan, tabSettings;
    private View navHomeBtn, navScanBtn, navSettingsBtn;

    private EditText clamscanPathView, databasePathView, targetPathView;
    private CheckBox autoUpdateCheckBox;
    private LinearLayout threatCardView, threatContainerView;
    private TextView heroTitleView, heroMessageView, protectionChipView;
    private TextView threatHeadlineView, threatSummaryView, threatEmptyView;
    private TextView rootStatusValueView, moduleStatusValueView, databaseStatusValueView, lastScanValueView;
    private TextView statusTextView, scanModeView, scanProgressPercentView, scanProgressDetailsView;
    private TextView backgroundMonitorView, logTextView;
    private ProgressBar scanProgressView;
    private Button saveButton, checkButton, updateDbButton, quickScanButton, fullScanButton, selectiveScanButton;
    private RadarView radarView;

    // Permissions/Settings Views
    private TextView permRootStatus, permNotifyStatus, permFilesStatus, permOverlayStatus, permAdminStatus;
    private TextView themeSummary, languageSummary, ignoredSummary;
    private Button clearIgnoredBtn, quarantineBtn;

    private boolean rootReady = false;
    private boolean moduleReady = false;
    private boolean databaseReady = false;
    private boolean busy = false;
    private boolean runtimePrepared = false;
    private String lastScanResult = "";
    private int lastThreatCount = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiConfig.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiConfig.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        loadPreferences();
        loadLastScanState();
        wireButtons();
        setupNavigation();

        setProgressPresentation(
                getString(R.string.scan_mode_ready),
                getString(R.string.progress_percent_waiting),
                getString(R.string.progress_ready_idle),
                true
        );
        refreshManualScanState();
        updateDashboardFromState();
        renderThreats();

        mainHandler.post(() -> prepareRuntimeAndRefresh());
    }

    private void setupNavigation() {
        navHomeBtn = findViewById(R.id.nav_home_btn);
        navScanBtn = findViewById(R.id.nav_scan_btn);
        navSettingsBtn = findViewById(R.id.nav_settings_btn);

        navHomeBtn.setOnClickListener(v -> switchTab(R.id.nav_home));
        navScanBtn.setOnClickListener(v -> switchTab(R.id.nav_scan));
        navSettingsBtn.setOnClickListener(v -> switchTab(R.id.nav_settings));

        switchTab(R.id.nav_home);
    }

    private void switchTab(int id) {
        tabHome.setVisibility(id == R.id.nav_home ? View.VISIBLE : View.GONE);
        tabScan.setVisibility(id == R.id.nav_scan ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(id == R.id.nav_settings ? View.VISIBLE : View.GONE);

        updateNavButton(navHomeBtn, id == R.id.nav_home);
        updateNavButton(navScanBtn, id == R.id.nav_scan);
        updateNavButton(navSettingsBtn, id == R.id.nav_settings);

        if (id == R.id.nav_settings) {
            updatePermissionSummary();
            updateThemeSummary();
            updateLanguageSummary();
            updateIgnoredSummary();
        }
    }

    private void updateNavButton(View btn, boolean active) {
        int color = active ? getColor(R.color.accent) : getColor(R.color.text_muted);
        LinearLayout layout = (LinearLayout) btn;
        android.widget.ImageView icon = (android.widget.ImageView) layout.getChildAt(0);
        TextView text = (TextView) layout.getChildAt(1);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        text.setTextColor(color);
        text.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPreferences();
        loadLastScanState();
        renderThreats();
        updatePermissionSummary();
        updateThemeSummary();
        updateLanguageSummary();
        updateIgnoredSummary();

        refreshManualScanState();
        if (busy) {
            mainHandler.removeCallbacks(scanStateRefreshRunnable);
            mainHandler.postDelayed(scanStateRefreshRunnable, 1000L);
            updateDashboardFromState();
            return;
        }

        if (!busy && (!runtimePrepared || !moduleReady || !databaseReady)) {
            mainHandler.postDelayed(() -> {
                if (!busy) prepareRuntimeAndRefresh();
            }, 250);
        } else {
            updateDashboardFromState();
        }
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(scanStateRefreshRunnable);
        super.onPause();
    }

    private void bindViews() {
        tabHome = findViewById(R.id.tab_home);
        tabScan = findViewById(R.id.tab_scan);
        tabSettings = findViewById(R.id.tab_settings);

        clamscanPathView = findViewById(R.id.clamscan_path);
        databasePathView = findViewById(R.id.database_path);
        targetPathView = findViewById(R.id.target_path);
        autoUpdateCheckBox = findViewById(R.id.auto_update_checkbox);

        threatCardView = findViewById(R.id.threat_card);
        threatContainerView = findViewById(R.id.threat_container);
        heroTitleView = findViewById(R.id.hero_title);
        heroMessageView = findViewById(R.id.hero_message);
        protectionChipView = findViewById(R.id.protection_chip);
        threatHeadlineView = findViewById(R.id.threat_headline);
        threatSummaryView = findViewById(R.id.threat_summary);
        threatEmptyView = findViewById(R.id.threat_empty);
        rootStatusValueView = findViewById(R.id.root_status_value);
        moduleStatusValueView = findViewById(R.id.module_status_value);
        databaseStatusValueView = findViewById(R.id.database_status_value);
        lastScanValueView = findViewById(R.id.last_scan_value);
        statusTextView = findViewById(R.id.status_text);
        scanModeView = findViewById(R.id.scan_mode_badge);
        scanProgressPercentView = findViewById(R.id.scan_progress_percent);
        scanProgressDetailsView = findViewById(R.id.scan_progress_details);
        backgroundMonitorView = findViewById(R.id.background_monitor_status);
        logTextView = findViewById(R.id.log_text);
        scanProgressView = findViewById(R.id.scan_progress);
        saveButton = findViewById(R.id.save_button);
        checkButton = findViewById(R.id.check_button);
        updateDbButton = findViewById(R.id.update_db_button);
        quickScanButton = findViewById(R.id.quick_scan_button);
        fullScanButton = findViewById(R.id.full_scan_button);
        selectiveScanButton = findViewById(R.id.selective_scan_button);
        radarView = findViewById(R.id.radar_view);

        // Settings Tabs Views
        permRootStatus = findViewById(R.id.settings_root_status);
        permNotifyStatus = findViewById(R.id.settings_notifications_status);
        permFilesStatus = findViewById(R.id.settings_files_status);
        permOverlayStatus = findViewById(R.id.settings_overlay_status);
        permAdminStatus = findViewById(R.id.settings_admin_status);
        themeSummary = findViewById(R.id.settings_theme_summary);
        languageSummary = findViewById(R.id.settings_language_summary);
        ignoredSummary = findViewById(R.id.settings_ignored_summary);
        clearIgnoredBtn = findViewById(R.id.settings_clear_ignored_button);
        quarantineBtn = findViewById(R.id.settings_quarantine_button);
    }

    private void wireButtons() {
        saveButton.setOnClickListener(v -> {
            savePreferences();
            appendLog(getString(R.string.config_saved_log));
            toast(getString(R.string.saved_message));
        });

        checkButton.setOnClickListener(v -> {
            savePreferences();
            prepareRuntimeAndRefresh();
        });

        updateDbButton.setOnClickListener(v -> {
            savePreferences();
            runDatabaseUpdate();
        });

        quickScanButton.setOnClickListener(v -> {
            savePreferences();
            startPlannedScan(getString(R.string.scan_mode_quick), getString(R.string.status_planning_quick_scan),
                    getString(R.string.status_quick_scanning), QUICK_SCAN_TARGET, false, 0L, false);
        });

        fullScanButton.setOnClickListener(v -> {
            savePreferences();
            startPlannedScan(getString(R.string.scan_mode_full), getString(R.string.status_planning_full_scan),
                    getString(R.string.status_scanning), textOf(targetPathView), true, 0L, true);
        });

        selectiveScanButton.setOnClickListener(v -> showSelectiveScanDialog());

        quarantineBtn.setOnClickListener(v -> startActivity(new Intent(this, QuarantineActivity.class)));

        clearIgnoredBtn.setOnClickListener(v -> {
            clearIgnoredThreats();
            updateIgnoredSummary();
            toast(getString(R.string.ignored_cleared_toast));
        });

        // Permissions
        findViewById(R.id.settings_request_root_button).setOnClickListener(v -> requestRoot());
        findViewById(R.id.settings_notifications_button).setOnClickListener(v -> requestNotifications());
        findViewById(R.id.settings_files_button).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        findViewById(R.id.settings_overlay_button).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        findViewById(R.id.settings_admin_button).setOnClickListener(v -> {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, ClamGuardAdminReceiver.class));
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.permission_admin_button));
            startActivity(intent);
        });

        // Themes
        findViewById(R.id.settings_theme_system_button).setOnClickListener(v -> applyThemeMode(UiConfig.THEME_SYSTEM));
        findViewById(R.id.settings_theme_light_button).setOnClickListener(v -> applyThemeMode(UiConfig.THEME_LIGHT));
        findViewById(R.id.settings_theme_dark_button).setOnClickListener(v -> applyThemeMode(UiConfig.THEME_DARK));

        // Language
        findViewById(R.id.settings_language_system_button).setOnClickListener(v -> applyLanguage(UiConfig.LANG_SYSTEM));
        findViewById(R.id.settings_language_ru_button).setOnClickListener(v -> applyLanguage(UiConfig.LANG_RU));
        findViewById(R.id.settings_language_en_button).setOnClickListener(v -> applyLanguage(UiConfig.LANG_EN));
    }

    private void applyThemeMode(String mode) {
        UiConfig.setThemeMode(this, mode);
        recreate();
    }

    private void applyLanguage(String code) {
        UiConfig.setLanguageCode(this, code);
        recreate();
    }

    private void updatePermissionSummary() {
        permRootStatus.setText("Root: " + (ShellUtils.hasKnownSuBinary() ? "Доступен" : "Не найден"));
        permNotifyStatus.setText("Уведомления: " + (hasNotifications() ? "Разрешены" : "Запрещены"));
        permFilesStatus.setText("Доступ к файлам: " + (Environment.isExternalStorageManager() ? "Полный" : "Ограничен"));
        permOverlayStatus.setText("Поверх окон: " + (Settings.canDrawOverlays(this) ? "Разрешено" : "Запрещено"));

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        boolean isAdmin = dpm != null && dpm.isAdminActive(new ComponentName(this, ClamGuardAdminReceiver.class));
        permAdminStatus.setText("Администратор: " + (isAdmin ? "Активен" : "Не активен"));
    }

    private boolean hasNotifications() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && nm.areNotificationsEnabled();
        }
        return nm.areNotificationsEnabled();
    }

    private void requestRoot() {
        new Thread(() -> {
            boolean granted = false;
            try {
                Process p = ShellUtils.newRootProcess("id").start();
                granted = p.waitFor() == 0;
            } catch (Exception ignored) {}
            final boolean res = granted;
            runOnUiThread(() -> {
                toast(res ? "Root доступ получен" : "Root доступ отклонен");
                updatePermissionSummary();
            });
        }).start();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        }
    }

    private void updateThemeSummary() {
        String mode = UiConfig.getThemeMode(this);
        themeSummary.setText("Текущая тема: " + (UiConfig.THEME_LIGHT.equals(mode) ? "светлая" : UiConfig.THEME_DARK.equals(mode) ? "тёмная" : "системная"));
    }

    private void updateLanguageSummary() {
        String code = UiConfig.getLanguageCode(this);
        languageSummary.setText("Текущий язык: " + (UiConfig.LANG_RU.equals(code) ? "русский" : UiConfig.LANG_EN.equals(code) ? "английский" : "системный"));
    }

    private void updateIgnoredSummary() {
        int count = getIgnoredThreats().size();
        ignoredSummary.setText("Игнорируемые угрозы: " + count);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String defaultClamscan = RuntimeAssetsManager.getClamscanPath(this);
        String defaultDatabase = RuntimeAssetsManager.getDatabasePath(this);

        clamscanPathView.setText(normalizeStoredPath(prefs.getString(KEY_CLAMSCAN, defaultClamscan), defaultClamscan));
        databasePathView.setText(normalizeStoredPath(prefs.getString(KEY_DATABASE, defaultDatabase), defaultDatabase));
        targetPathView.setText(prefs.getString(KEY_TARGET, DEFAULT_TARGET));
        autoUpdateCheckBox.setChecked(prefs.getBoolean(KEY_AUTO_UPDATE, true));
    }

    private void savePreferences() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_CLAMSCAN, textOf(clamscanPathView))
                .putString(KEY_DATABASE, textOf(databasePathView))
                .putString(KEY_TARGET, textOf(targetPathView))
                .putBoolean(KEY_AUTO_UPDATE, autoUpdateCheckBox.isChecked())
                .apply();
        ProtectionScheduler.sync(this);
    }

    private void loadLastScanState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastScan = prefs.getString(KEY_LAST_SCAN, "");
        lastScanResult = prefs.getString(KEY_LAST_SCAN_RESULT, "");
        lastThreatCount = prefs.getInt(KEY_LAST_THREAT_COUNT, 0);

        if (TextUtils.isEmpty(lastScan)) {
            lastScanValueView.setText(getString(R.string.value_not_scanned));
            return;
        }

        String resultLabel = getString(R.string.last_scan_result_error);
        if ("clean".equals(lastScanResult)) resultLabel = getString(R.string.last_scan_result_clean);
        else if ("threats".equals(lastScanResult)) resultLabel = getString(R.string.last_scan_result_threats, lastThreatCount);
        lastScanValueView.setText(lastScan + "\n" + resultLabel);
    }

    private void saveLastScanState(String resultType, int threatCount) {
        String timestamp = new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date());
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_SCAN, timestamp)
                .putString(KEY_LAST_SCAN_RESULT, resultType)
                .putInt(KEY_LAST_THREAT_COUNT, threatCount)
                .apply();
        loadLastScanState();
    }

    private void prepareRuntimeAndRefresh() {
        if (busy) return;
        setBusy(true, getString(R.string.status_preparing_engine), false);
        executor.execute(() -> {
            String error = null;
            try {
                RuntimeAssetsManager.ensureInstalled(this);
                runtimePrepared = true;
            } catch (IOException e) {
                runtimePrepared = false;
                error = e.getMessage();
            }
            final String finalError = error;
            mainHandler.post(() -> {
                if (finalError != null) {
                    setBusy(false, getString(R.string.status_runtime_error), false);
                    appendLog("Runtime error: " + finalError);
                } else {
                    setBusy(false, getString(R.string.status_idle), false);
                    runShellTask(getString(R.string.status_checking), buildEnvironmentCheckCommand(), false, false, null);
                }
            });
        });
    }

    private void runDatabaseUpdate() {
        setBusy(true, getString(R.string.status_updating_db), false);
        executor.execute(() -> {
            final DatabaseUpdateRunner.Result result = DatabaseUpdateRunner.run(this, RuntimeAssetsManager.getFreshclamPath(this), textOf(databasePathView), line -> mainHandler.post(() -> appendLog(line)));
            mainHandler.post(() -> {
                databaseReady = (result.exitCode == 0);
                setBusy(false, databaseReady ? "Базы обновлены" : "Ошибка обновления баз", false);
                updateDashboardFromState();
            });
        });
    }

    private void startPlannedScan(final String modeLabel, final String planningStatus, final String scanningStatus, final String targetPath, final boolean includeInstalledApks, final long modifiedAfter, final boolean enableBackgroundMonitor) {
        setBusy(true, planningStatus, true);
        setProgressPresentation(modeLabel, getString(R.string.progress_percent_waiting), planningStatus, true);
        uniqueThreats.clear();
        threats.clear();
        renderThreats();
        ForegroundScanService.startManualScan(this, modeLabel, targetPath, includeInstalledApks, modifiedAfter, enableBackgroundMonitor);
        mainHandler.removeCallbacks(scanStateRefreshRunnable);
        mainHandler.postDelayed(scanStateRefreshRunnable, 1000L);
    }

    private void showSelectiveScanDialog() {
        final EditText input = new EditText(this);
        input.setHint("/sdcard/Documents");
        input.setText(textOf(targetPathView));
        new AlertDialog.Builder(this).setTitle("Выборочная проверка").setView(input).setPositiveButton("Запустить", (d, w) -> {
            String path = input.getText().toString().trim();
            if (!TextUtils.isEmpty(path)) startPlannedScan("Выборочная", "Планирование...", "Сканирование...", path, false, 0L, false);
        }).setNegativeButton("Отмена", null).show();
    }

    private void runShellTask(String status, String command, boolean parseThreats, boolean clearThreatsFirst, Runnable onSuccess) {
        setBusy(true, status, false);
        executor.execute(() -> {
            int code = -1;
            StringBuilder output = new StringBuilder();
            try {
                Process p = ShellUtils.newRootProcess(command).redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String l;
                while ((l = r.readLine()) != null) {
                    output.append(l).append("\n");
                    final String line = l;
                    mainHandler.post(() -> appendLog(line));
                }
                code = p.waitFor();
            } catch (Exception e) { output.append(e.getMessage()); }
            final int fCode = code; final String fOut = output.toString();
            mainHandler.post(() -> {
                if (command.contains("CG_ENGINE")) parseEnvironmentOutput(fOut);
                setBusy(false, fCode == 0 ? "Готово" : "Завершено с кодом " + fCode, false);
                updateDashboardFromState();
                if (fCode == 0 && onSuccess != null) onSuccess.run();
            });
        });
    }

    private void parseEnvironmentOutput(String output) {
        rootReady = ShellUtils.hasKnownSuBinary();
        moduleReady = output.contains("CG_ENGINE=1") && output.contains("CG_CLAMSCAN=1");
        databaseReady = output.contains("CG_DATABASE=1") && output.contains("CG_MAIN=1");
        rootStatusValueView.setText(rootReady ? "Ready" : "Missing");
        moduleStatusValueView.setText(moduleReady ? "Ready" : "Missing");
        databaseStatusValueView.setText(databaseReady ? "Ready" : "Missing");
    }

    private void updateDashboardFromState() {
        boolean hasThreats = !threats.isEmpty() || ("threats".equals(lastScanResult) && lastThreatCount > 0);
        heroTitleView.setText(hasThreats ? "Угроза обнаружена!" : "Устройство защищено");
        heroMessageView.setText(hasThreats ? "Внимание: найдены подозрительные файлы." : "Сканирование не выявило активных угроз.");
        protectionChipView.setText(hasThreats ? "ОПАСНО" : "БЕЗОПАСНО");
        protectionChipView.setBackgroundResource(hasThreats ? R.drawable.pill_warning : R.drawable.pill_safe);
        updateBackgroundMonitorSummary();
        updateActionAvailability();
    }

    private void refreshManualScanState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean scanRunning = prefs.getBoolean(ScanState.KEY_MANUAL_SCAN_RUNNING, false);
        String result = prefs.getString(ScanState.KEY_MANUAL_SCAN_RESULT, "");
        if (!scanRunning && TextUtils.isEmpty(result)) {
            return;
        }

        String mode = prefs.getString(ScanState.KEY_MANUAL_SCAN_MODE, getString(R.string.scan_mode_ready));
        String status = prefs.getString(ScanState.KEY_MANUAL_SCAN_STATUS, getString(R.string.status_idle));
        long scannedBytes = prefs.getLong(ScanState.KEY_MANUAL_SCAN_SCANNED_BYTES, 0L);
        long totalBytes = prefs.getLong(ScanState.KEY_MANUAL_SCAN_TOTAL_BYTES, 0L);
        int scannedFiles = prefs.getInt(ScanState.KEY_MANUAL_SCAN_SCANNED_FILES, 0);
        int totalFiles = prefs.getInt(ScanState.KEY_MANUAL_SCAN_TOTAL_FILES, 0);
        String currentPath = prefs.getString(ScanState.KEY_MANUAL_SCAN_CURRENT_PATH, "");

        if (scanRunning) {
            this.busy = true;
            statusTextView.setText(status);
            scanProgressView.setVisibility(View.VISIBLE);
            scanProgressView.setIndeterminate(totalBytes <= 0L);
            scanProgressView.setMax(1000);
            if (totalBytes > 0L) {
                scanProgressView.setProgress((int) Math.min(1000L, scannedBytes * 1000L / totalBytes));
            }
            scanModeView.setText(mode);
            scanProgressPercentView.setText(totalBytes > 0L ? (scannedBytes * 100L / totalBytes) + "%" : getString(R.string.progress_percent_waiting));
            String details = formatBytes(scannedBytes) + " / " + formatBytes(totalBytes) + " · " + scannedFiles + " / " + totalFiles + " файлов";
            if (!TextUtils.isEmpty(currentPath)) {
                details += "\n" + currentPath;
            }
            scanProgressDetailsView.setText(details);
            radarView.setActive(true);
            updateActionAvailability();
            return;
        }

        if (busy) {
            setBusy(false, status, false);
        }
        Set<String> storedThreats = prefs.getStringSet(ScanState.KEY_MANUAL_SCAN_THREATS, new HashSet<String>());
        uniqueThreats.clear();
        uniqueThreats.addAll(storedThreats);
        threats.clear();
        threats.addAll(uniqueThreats);
        renderThreats();
        loadLastScanState();
        if ("clean".equals(result)) {
            setProgressPresentation(mode, getString(R.string.progress_percent_done), getString(R.string.status_scan_complete_clean), false);
        } else if ("threats".equals(result)) {
            setProgressPresentation(mode, getString(R.string.progress_percent_done), getString(R.string.status_scan_complete_threats, threats.size()), false);
        } else if ("error".equals(result)) {
            setProgressPresentation(mode, getString(R.string.progress_percent_error), status, false);
        }
    }

    private void updateBackgroundMonitorSummary() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(ProtectionScheduler.KEY_BACKGROUND_MONITOR_ENABLED, false)) {
            backgroundMonitorView.setText(getString(R.string.background_monitor_disabled));
            return;
        }

        String target = prefs.getString(ProtectionScheduler.KEY_BACKGROUND_MONITOR_TARGET, DEFAULT_TARGET);
        String status = prefs.getString(ProtectionScheduler.KEY_LAST_BACKGROUND_STATUS, "");
        long statusAt = prefs.getLong(ProtectionScheduler.KEY_LAST_BACKGROUND_STATUS_AT, 0L);
        String text = getString(R.string.background_monitor_enabled, target);
        if (!TextUtils.isEmpty(status)) {
            String at = statusAt > 0L
                    ? new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date(statusAt))
                    : "";
            text += "\n" + (TextUtils.isEmpty(at) ? status : at + ": " + status);
        }
        backgroundMonitorView.setText(text);
    }

    private void renderThreats() {
        threatContainerView.removeAllViews();
        threatEmptyView.setVisibility(threats.isEmpty() ? View.VISIBLE : View.GONE);
        for (final String path : threats) {
            TextView tv = new TextView(this);
            tv.setText(path);
            tv.setPadding(dp(12), dp(12), dp(12), dp(12));
            tv.setBackgroundResource(R.drawable.tile_surface);
            tv.setOnClickListener(v -> showThreatActionDialog(path));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
            threatContainerView.addView(tv, lp);
        }
    }

    private void showThreatActionDialog(String path) {
        new AlertDialog.Builder(this).setTitle("Действие с угрозой").setItems(new String[]{"Карантин", "Удалить", "Игнорировать"}, (d, w) -> {
            if (w == 0) {
                String dest = RuntimeAssetsManager.getQuarantinePath(this) + "/" + System.currentTimeMillis() + "-" + new File(path).getName();
                runShellTask("В карантин", "mkdir -p " + shellQuote(RuntimeAssetsManager.getQuarantinePath(this)) + " && mv " + shellQuote(path) + " " + shellQuote(dest), false, false, () -> QuarantineManager.addItem(this, path, dest, "Threat"));
            } else if (w == 1) {
                runShellTask("Удаление", "rm -f " + shellQuote(path), false, false, null);
            }
        }).show();
    }

    private String buildEnvironmentCheckCommand() {
        return "([ -d " + shellQuote(RuntimeAssetsManager.getRuntimeRoot(this)) + " ] && echo CG_ENGINE=1 || echo CG_ENGINE=0) && "
                + "([ -x " + shellQuote(textOf(clamscanPathView)) + " ] && echo CG_CLAMSCAN=1 || echo CG_CLAMSCAN=0) && "
                + "([ -d " + shellQuote(textOf(databasePathView)) + " ] && echo CG_DATABASE=1 || echo CG_DATABASE=0) && "
                + "([ -f " + shellQuote(textOf(databasePathView) + "/main.cvd") + " ] && echo CG_MAIN=1 || echo CG_MAIN=0)";
    }

    private void setProgressPresentation(String mode, String percent, String detail, boolean indeterminate) {
        scanModeView.setText(mode);
        scanProgressPercentView.setText(percent);
        scanProgressDetailsView.setText(detail);
        scanProgressView.setIndeterminate(indeterminate);
    }

    private void setBusy(boolean busy, String status, boolean showRadar) {
        this.busy = busy;
        statusTextView.setText(status);
        scanProgressView.setVisibility(busy ? View.VISIBLE : View.GONE);
        radarView.setActive(showRadar && busy);
        updateActionAvailability();
    }

    private void updateActionAvailability() {
        boolean canAction = !busy;
        quickScanButton.setEnabled(canAction); fullScanButton.setEnabled(canAction);
        selectiveScanButton.setEnabled(canAction); updateDbButton.setEnabled(canAction);
        quickScanButton.setAlpha(canAction ? 1f : 0.5f);
        fullScanButton.setAlpha(canAction ? 1f : 0.5f);
        selectiveScanButton.setAlpha(canAction ? 1f : 0.5f);
        updateDbButton.setAlpha(canAction ? 1f : 0.5f);
    }

    private void updateScanProgress(String mode, long sb, long tb, int sf, int tf, ScanPlanner.ScanItem item) {
        scanModeView.setText(mode);
        scanProgressPercentView.setText(tb > 0 ? (sb * 100 / tb) + "%" : "...");
        scanProgressDetailsView.setText(formatBytes(sb) + " / " + formatBytes(tb) + " · " + sf + " / " + tf + " файлов");
        if (tb > 0) scanProgressView.setProgress((int)(sb * 1000 / tb));
    }

    private String formatBytes(long v) {
        if (v < 1024) return v + " B";
        int exp = (int) (Math.log(v) / Math.log(1024));
        return String.format("%.1f %sB", v / Math.pow(1024, exp), "KMGTPE".charAt(exp-1));
    }

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }
    private String shellQuote(String s) { return "'" + s.replace("'", "'\"'\"'") + "'"; }
    private String textOf(EditText v) { return v.getText().toString().trim(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void appendLog(String l) { logTextView.append("\n" + l); }

    private String normalizeStoredPath(String v, String def) {
        if (TextUtils.isEmpty(v) || v.contains(MODULE_ROOT) || v.contains(LEGACY_ROOT)) return def;
        if (v.startsWith("/data/app/") && (v.endsWith("/libclamscan_exec.so") || v.endsWith("/libfreshclam_exec.so"))) return def;
        return v;
    }

    private Set<String> getIgnoredThreats() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getStringSet(KEY_IGNORED_THREATS, new HashSet<>());
    }

    private void clearIgnoredThreats() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_IGNORED_THREATS).apply();
    }
}
