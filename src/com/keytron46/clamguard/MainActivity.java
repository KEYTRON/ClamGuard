package com.keytron46.clamguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.io.OutputStreamWriter;
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
    private static final String KEY_FRESHCLAM = "freshclam_path";
    private static final String KEY_DATABASE = "database_path";
    private static final String KEY_TARGET = "target_path";
    private static final String KEY_QUARANTINE = "quarantine_path";
    private static final String KEY_LAST_SCAN = "last_scan";
    private static final String KEY_LAST_SCAN_RESULT = "last_scan_result";
    private static final String KEY_LAST_THREAT_COUNT = "last_threat_count";
    private static final String KEY_IGNORED_THREATS = "ignored_threats";
    private static final String KEY_AUTO_UPDATE = "auto_update_enabled";

    private static final String MODULE_ROOT = "/data/adb/modules/clamguard";
    private static final String DEFAULT_TARGET = "/sdcard";
    private static final String QUICK_SCAN_TARGET = "/sdcard/Download";
    private static final String LEGACY_ROOT = "/data/local/tmp/clamav";
    private static final String SCAN_TARGETS_FILE = "scan-targets.txt";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> uniqueThreats = new LinkedHashSet<String>();
    private final List<String> threats = new ArrayList<String>();

    private EditText clamscanPathView;
    private EditText freshclamPathView;
    private EditText databasePathView;
    private EditText targetPathView;
    private EditText quarantinePathView;
    private CheckBox autoUpdateCheckBox;
    private LinearLayout settingsContainer;
    private LinearLayout threatCardView;
    private LinearLayout threatContainerView;
    private TextView heroTitleView;
    private TextView heroMessageView;
    private TextView protectionChipView;
    private TextView threatHeadlineView;
    private TextView threatMessageView;
    private TextView threatSummaryView;
    private TextView threatEmptyView;
    private TextView rootStatusValueView;
    private TextView moduleStatusValueView;
    private TextView databaseStatusValueView;
    private TextView lastScanValueView;
    private TextView statusTextView;
    private TextView logTextView;
    private ProgressBar scanProgressView;
    private Button saveButton;
    private Button resetDefaultsButton;
    private Button clearIgnoredButton;
    private Button checkButton;
    private Button updateDbButton;
    private Button quickScanButton;
    private Button fullScanButton;
    private Button toggleSettingsButton;

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
        updateDashboardFromState();
        renderThreats();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                prepareRuntimeAndRefresh();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPreferences();
        loadLastScanState();
        renderThreats();
        if (!busy && (!runtimePrepared || !moduleReady || !databaseReady)) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!busy) {
                        prepareRuntimeAndRefresh();
                    }
                }
            }, 250);
        }
    }

    private void bindViews() {
        clamscanPathView = (EditText) findViewById(R.id.clamscan_path);
        freshclamPathView = (EditText) findViewById(R.id.freshclam_path);
        databasePathView = (EditText) findViewById(R.id.database_path);
        targetPathView = (EditText) findViewById(R.id.target_path);
        quarantinePathView = (EditText) findViewById(R.id.quarantine_path);
        autoUpdateCheckBox = (CheckBox) findViewById(R.id.auto_update_checkbox);
        settingsContainer = (LinearLayout) findViewById(R.id.settings_container);
        threatCardView = (LinearLayout) findViewById(R.id.threat_card);
        threatContainerView = (LinearLayout) findViewById(R.id.threat_container);
        heroTitleView = (TextView) findViewById(R.id.hero_title);
        heroMessageView = (TextView) findViewById(R.id.hero_message);
        protectionChipView = (TextView) findViewById(R.id.protection_chip);
        threatHeadlineView = (TextView) findViewById(R.id.threat_headline);
        threatMessageView = (TextView) findViewById(R.id.threat_message);
        threatSummaryView = (TextView) findViewById(R.id.threat_summary);
        threatEmptyView = (TextView) findViewById(R.id.threat_empty);
        rootStatusValueView = (TextView) findViewById(R.id.root_status_value);
        moduleStatusValueView = (TextView) findViewById(R.id.module_status_value);
        databaseStatusValueView = (TextView) findViewById(R.id.database_status_value);
        lastScanValueView = (TextView) findViewById(R.id.last_scan_value);
        statusTextView = (TextView) findViewById(R.id.status_text);
        logTextView = (TextView) findViewById(R.id.log_text);
        scanProgressView = (ProgressBar) findViewById(R.id.scan_progress);
        saveButton = (Button) findViewById(R.id.save_button);
        resetDefaultsButton = (Button) findViewById(R.id.reset_defaults_button);
        clearIgnoredButton = (Button) findViewById(R.id.clear_ignored_button);
        checkButton = (Button) findViewById(R.id.check_button);
        updateDbButton = (Button) findViewById(R.id.update_db_button);
        quickScanButton = (Button) findViewById(R.id.quick_scan_button);
        fullScanButton = (Button) findViewById(R.id.full_scan_button);
        toggleSettingsButton = (Button) findViewById(R.id.toggle_settings_button);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String defaultClamscan = RuntimeAssetsManager.getClamscanPath(this);
        String defaultFreshclam = RuntimeAssetsManager.getFreshclamPath(this);
        String defaultDatabase = RuntimeAssetsManager.getDatabasePath(this);
        String defaultQuarantine = RuntimeAssetsManager.getQuarantinePath(this);

        String clamscan = normalizeStoredPath(prefs.getString(KEY_CLAMSCAN, defaultClamscan), "", defaultClamscan);
        String freshclam = normalizeStoredPath(prefs.getString(KEY_FRESHCLAM, defaultFreshclam), "", defaultFreshclam);
        String database = normalizeStoredPath(prefs.getString(KEY_DATABASE, defaultDatabase), "", defaultDatabase);
        String target = normalizeStoredPath(prefs.getString(KEY_TARGET, DEFAULT_TARGET), "", DEFAULT_TARGET);
        String quarantine = normalizeStoredPath(prefs.getString(KEY_QUARANTINE, defaultQuarantine), "", defaultQuarantine);
        boolean autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE, true);

        clamscanPathView.setText(clamscan);
        freshclamPathView.setText(freshclam);
        databasePathView.setText(database);
        targetPathView.setText(target);
        quarantinePathView.setText(quarantine);
        autoUpdateCheckBox.setChecked(autoUpdate);
        savePreferences();
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_CLAMSCAN, textOf(clamscanPathView));
        editor.putString(KEY_FRESHCLAM, textOf(freshclamPathView));
        editor.putString(KEY_DATABASE, textOf(databasePathView));
        editor.putString(KEY_TARGET, textOf(targetPathView));
        editor.putString(KEY_QUARANTINE, textOf(quarantinePathView));
        editor.putBoolean(KEY_AUTO_UPDATE, autoUpdateCheckBox.isChecked());
        editor.apply();
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
        if ("clean".equals(lastScanResult)) {
            resultLabel = getString(R.string.last_scan_result_clean);
        } else if ("threats".equals(lastScanResult)) {
            resultLabel = getString(R.string.last_scan_result_threats, lastThreatCount);
        }
        lastScanValueView.setText(lastScan + "\n" + resultLabel);
    }

    private void saveLastScanState(String resultType, int threatCount) {
        String timestamp = new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date());
        lastScanResult = resultType;
        lastThreatCount = threatCount;
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_LAST_SCAN, timestamp);
        editor.putString(KEY_LAST_SCAN_RESULT, resultType);
        editor.putInt(KEY_LAST_THREAT_COUNT, threatCount);
        editor.apply();
        loadLastScanState();
    }

    private void wireButtons() {
        toggleSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                appendLog(getString(R.string.config_saved_log));
                toast(getString(R.string.saved_message));
            }
        });

        resetDefaultsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clamscanPathView.setText(RuntimeAssetsManager.getClamscanPath(MainActivity.this));
                freshclamPathView.setText(RuntimeAssetsManager.getFreshclamPath(MainActivity.this));
                databasePathView.setText(RuntimeAssetsManager.getDatabasePath(MainActivity.this));
                targetPathView.setText(DEFAULT_TARGET);
                quarantinePathView.setText(RuntimeAssetsManager.getQuarantinePath(MainActivity.this));
                autoUpdateCheckBox.setChecked(true);
                savePreferences();
                appendLog(getString(R.string.config_saved_log));
            }
        });

        clearIgnoredButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearIgnoredThreats();
                appendLog(getString(R.string.ignored_cleared_log));
                toast(getString(R.string.ignored_cleared_toast));
            }
        });

        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                prepareRuntimeAndRefresh();
            }
        });

        updateDbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                runShellTask(getString(R.string.status_updating_db), buildFreshclamCommand(), false, false, null);
            }
        });

        quickScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                runShellTask(getString(R.string.status_quick_scanning), buildScanCommand(QUICK_SCAN_TARGET), true, true, null);
            }
        });

        fullScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                runShellTask(getString(R.string.status_scanning), buildScanCommand(textOf(targetPathView)), true, true, null);
            }
        });
    }

    private void prepareRuntimeAndRefresh() {
        if (busy) {
            return;
        }

        setBusy(true, getString(R.string.status_preparing_engine));
        appendLog(getString(R.string.status_preparing_engine));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    RuntimeAssetsManager.ensureInstalled(MainActivity.this);
                    runtimePrepared = true;
                } catch (IOException e) {
                    runtimePrepared = false;
                    error = e.getMessage();
                }

                final String finalError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalError != null) {
                            moduleReady = false;
                            databaseReady = false;
                            setBusy(false, getString(R.string.status_runtime_error));
                            appendLog(getString(R.string.runtime_prepare_failed_prefix) + finalError);
                            updateDashboardFromState();
                            return;
                        }

                        loadPreferences();
                        setBusy(false, getString(R.string.status_idle));
                        runShellTask(getString(R.string.status_checking), buildEnvironmentCheckCommand(), false, false, null);
                    }
                });
            }
        });
    }

    private void runShellTask(final String status, final String command, final boolean parseThreats, final boolean clearThreatsFirst, final Runnable onSuccess) {
        if (TextUtils.isEmpty(command)) {
            toast(getString(R.string.missing_path_message));
            return;
        }

        setBusy(true, status);
        appendLog("$ " + command);

        if (clearThreatsFirst) {
            uniqueThreats.clear();
            threats.clear();
            renderThreats();
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                StringBuilder captured = new StringBuilder();
                int exitCode = -1;

                try {
                    process = new ProcessBuilder("/system/bin/sh", "-c", command)
                            .redirectErrorStream(true)
                            .start();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        captured.append(line).append('\n');
                        final String emittedLine = line;
                        if (parseThreats && emittedLine.endsWith(" FOUND")) {
                            String threatPath = emittedLine.substring(0, emittedLine.length() - " FOUND".length());
                            if (!isIgnoredThreat(threatPath)) {
                                uniqueThreats.add(threatPath);
                            }
                        }
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                appendLog(emittedLine);
                            }
                        });
                    }
                    exitCode = process.waitFor();
                } catch (IOException e) {
                    captured.append("IO error: ").append(e.getMessage()).append('\n');
                } catch (InterruptedException e) {
                    captured.append("Interrupted.").append('\n');
                    Thread.currentThread().interrupt();
                } finally {
                    if (process != null) {
                        process.destroy();
                    }
                }

                final int finalExitCode = exitCode;
                final String finalOutput = captured.toString();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (parseThreats) {
                            threats.clear();
                            threats.addAll(uniqueThreats);
                            renderThreats();
                            if (finalExitCode == 0) {
                                saveLastScanState(threats.isEmpty() ? "clean" : "threats", threats.size());
                            } else {
                                saveLastScanState("error", 0);
                            }
                        } else if (command.equals(buildEnvironmentCheckCommand())) {
                            parseEnvironmentOutput(finalOutput);
                        } else if (command.equals(buildFreshclamCommand()) && finalExitCode == 0) {
                            databaseReady = true;
                            databaseStatusValueView.setText(getString(R.string.value_ready));
                            ProtectionScheduler.markDatabaseUpdated(MainActivity.this);
                        }

                        String finishStatus = parseThreats
                                ? getString(R.string.status_done_with_threats, finalExitCode, threats.size())
                                : getString(R.string.status_done, finalExitCode);
                        setBusy(false, finishStatus);
                        updateDashboardFromState();

                        if (finalExitCode == 0 && onSuccess != null) {
                            onSuccess.run();
                        }
                        if (finalExitCode != 0 && finalOutput.length() == 0) {
                            appendLog(getString(R.string.root_pending_message));
                        }
                    }
                });
            }
        });
    }

    private void parseEnvironmentOutput(String output) {
        rootReady = ShellUtils.hasKnownSuBinary();
        moduleReady = output.contains("CG_ENGINE=1") && output.contains("CG_CLAMSCAN=1") && output.contains("CG_FRESHCLAM=1");
        databaseReady = output.contains("CG_DATABASE=1") && output.contains("CG_MAIN=1") && output.contains("CG_DAILY=1");

        rootStatusValueView.setText(rootReady ? getString(R.string.value_ready) : getString(R.string.value_missing));
        moduleStatusValueView.setText(moduleReady ? getString(R.string.value_ready) : getString(R.string.value_missing));
        databaseStatusValueView.setText(databaseReady ? getString(R.string.value_ready) : getString(R.string.value_missing));

        if (moduleReady && databaseReady) {
            appendLog(getString(R.string.summary_env_ready));
        } else {
            appendLog(getString(R.string.summary_env_broken));
        }
    }

    private void updateDashboardFromState() {
        rootStatusValueView.setText(rootReady ? getString(R.string.value_ready) : rootStatusValueView.getText());
        moduleStatusValueView.setText(moduleReady ? getString(R.string.value_ready) : moduleStatusValueView.getText());
        databaseStatusValueView.setText(databaseReady ? getString(R.string.value_ready) : databaseStatusValueView.getText());

        boolean hasLiveThreats = !threats.isEmpty();
        boolean hasThreatHistory = "threats".equals(lastScanResult) && lastThreatCount > 0;
        boolean hasCleanHistory = "clean".equals(lastScanResult);
        boolean hasCompletedScan = hasThreatHistory || hasCleanHistory || "error".equals(lastScanResult);

        if (!moduleReady || !databaseReady) {
            heroTitleView.setText(getString(R.string.hero_title_default));
            heroMessageView.setText(getString(R.string.hero_message_default));
            protectionChipView.setBackgroundResource(R.drawable.pill_pending);
            protectionChipView.setText(getString(R.string.chip_pending));
            threatCardView.setBackgroundResource(R.drawable.card_alert);
            threatHeadlineView.setText(getString(R.string.threat_headline_default));
            threatMessageView.setText(getString(R.string.summary_env_broken));
            updateActionAvailability();
            return;
        }

        if (!rootReady) {
            heroTitleView.setText(getString(R.string.hero_title_ready));
            heroMessageView.setText(getString(R.string.hero_message_no_root_optional));
            protectionChipView.setBackgroundResource(R.drawable.pill_safe);
            protectionChipView.setText(getString(R.string.chip_safe));
            threatCardView.setBackgroundResource(R.drawable.card_surface);
            threatHeadlineView.setText(getString(R.string.threat_headline_ready));
            threatMessageView.setText(getString(R.string.summary_root_optional));
            updateActionAvailability();
            return;
        }

        if (hasLiveThreats || hasThreatHistory) {
            heroTitleView.setText(getString(R.string.hero_title_warning));
            heroMessageView.setText(getString(R.string.hero_message_warning));
            protectionChipView.setBackgroundResource(R.drawable.pill_warning);
            protectionChipView.setText(getString(R.string.chip_warning));
            threatCardView.setBackgroundResource(R.drawable.card_alert);
            threatHeadlineView.setText(getString(R.string.summary_threats_found, hasLiveThreats ? threats.size() : lastThreatCount));
            threatMessageView.setText(hasLiveThreats
                    ? getString(R.string.threat_list_hint)
                    : getString(R.string.threat_history_hint, lastThreatCount));
            updateActionAvailability();
            return;
        }

        if (!hasCompletedScan) {
            heroTitleView.setText(getString(R.string.hero_title_ready));
            heroMessageView.setText(getString(R.string.hero_message_ready));
            protectionChipView.setBackgroundResource(R.drawable.pill_pending);
            protectionChipView.setText(getString(R.string.chip_pending));
            threatCardView.setBackgroundResource(R.drawable.card_surface);
            threatHeadlineView.setText(getString(R.string.threat_headline_ready));
            threatMessageView.setText(getString(R.string.threat_message_ready));
            updateActionAvailability();
            return;
        }

        if (hasCleanHistory) {
            heroTitleView.setText(getString(R.string.hero_title_safe));
            heroMessageView.setText(getString(R.string.hero_message_safe));
            protectionChipView.setBackgroundResource(R.drawable.pill_safe);
            protectionChipView.setText(getString(R.string.chip_safe));
            threatCardView.setBackgroundResource(R.drawable.card_surface);
            threatHeadlineView.setText(getString(R.string.summary_no_threats));
            threatMessageView.setText(getString(R.string.quick_scan_target_label));
        }
        updateActionAvailability();
    }

    private void renderThreats() {
        threatContainerView.removeAllViews();
        if (threats.isEmpty()) {
            threatEmptyView.setVisibility(View.VISIBLE);
            if ("threats".equals(lastScanResult) && lastThreatCount > 0) {
                threatSummaryView.setText(getString(R.string.threat_history_hint, lastThreatCount));
                threatEmptyView.setText(getString(R.string.threat_empty_history));
            } else {
                threatSummaryView.setText(getString(R.string.threat_summary_idle));
                threatEmptyView.setText(getString(R.string.threat_empty));
            }
            updateDashboardFromState();
            return;
        }

        threatEmptyView.setVisibility(View.GONE);
        threatSummaryView.setText(getString(R.string.summary_threats_found, threats.size()));
        for (final String threatPath : threats) {
            TextView threatItem = new TextView(this);
            threatItem.setText(threatPath);
            threatItem.setTextColor(getColor(R.color.text_primary));
            threatItem.setTypeface(Typeface.DEFAULT_BOLD);
            threatItem.setPadding(dp(12), dp(12), dp(12), dp(12));
            threatItem.setBackgroundResource(R.drawable.tile_surface);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(8);
            threatItem.setLayoutParams(params);
            threatItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showThreatActionDialog(threatPath);
                }
            });
            threatContainerView.addView(threatItem);
        }
        updateDashboardFromState();
    }

    private void showThreatActionDialog(final String threatPath) {
        final String[] actions = new String[] {
                getString(R.string.action_quarantine),
                getString(R.string.action_delete),
                getString(R.string.action_ignore),
                getString(R.string.action_cancel)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.threat_dialog_title))
                .setMessage(threatPath)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        final String destination = buildQuarantineDestination(threatPath);
                        runShellTask(getString(R.string.status_quarantine), buildQuarantineCommand(threatPath, destination), false, false, new Runnable() {
                            @Override
                            public void run() {
                                uniqueThreats.remove(threatPath);
                                threats.remove(threatPath);
                                renderThreats();
                                appendLog(getString(R.string.moved_to_prefix) + destination);
                            }
                        });
                    } else if (which == 1) {
                        runShellTask(getString(R.string.status_delete), buildDeleteCommand(threatPath), false, false, new Runnable() {
                            @Override
                            public void run() {
                                uniqueThreats.remove(threatPath);
                                threats.remove(threatPath);
                                renderThreats();
                                appendLog(getString(R.string.deleted_prefix) + threatPath);
                            }
                        });
                    } else if (which == 2) {
                        ignoreThreat(threatPath);
                        uniqueThreats.remove(threatPath);
                        threats.remove(threatPath);
                        renderThreats();
                        appendLog(getString(R.string.ignored_prefix) + threatPath);
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private String buildEnvironmentCheckCommand() {
        String clamscan = textOf(clamscanPathView);
        String freshclam = textOf(freshclamPathView);
        String database = textOf(databasePathView);
        return "([ -d " + shellQuote(RuntimeAssetsManager.getRuntimeRoot(this)) + " ] && echo CG_ENGINE=1 || echo CG_ENGINE=0) && "
                + "([ -x " + shellQuote(clamscan) + " ] && echo CG_CLAMSCAN=1 || echo CG_CLAMSCAN=0) && "
                + "([ -x " + shellQuote(freshclam) + " ] && echo CG_FRESHCLAM=1 || echo CG_FRESHCLAM=0) && "
                + "([ -d " + shellQuote(database) + " ] && echo CG_DATABASE=1 || echo CG_DATABASE=0) && "
                + "([ -f " + shellQuote(database + "/main.cvd") + " ] && echo CG_MAIN=1 || echo CG_MAIN=0) && "
                + "([ -f " + shellQuote(database + "/daily.cvd") + " ] && echo CG_DAILY=1 || echo CG_DAILY=0)";
    }

    private String buildFreshclamCommand() {
        String freshclam = textOf(freshclamPathView);
        String database = textOf(databasePathView);
        if (TextUtils.isEmpty(freshclam) || TextUtils.isEmpty(database)) {
            return "";
        }
        return buildRuntimeEnvPrefix()
                + shellQuote(freshclam)
                + " --stdout --datadir=" + shellQuote(database)
                + " --config-file=" + shellQuote(RuntimeAssetsManager.getFreshclamConfigPath(this));
    }

    private String buildScanCommand(String targetPath) {
        List<String> targets = collectScanTargets(targetPath);
        if (targets.isEmpty()) {
            return "";
        }
        return buildMultiTargetScanCommand(targets);
    }

    private String buildQuarantineCommand(String sourcePath, String destinationPath) {
        String quarantineDir = textOf(quarantinePathView);
        return "mkdir -p " + shellQuote(quarantineDir)
                + " && mv " + shellQuote(sourcePath) + " " + shellQuote(destinationPath);
    }

    private String buildDeleteCommand(String sourcePath) {
        return "rm -f " + shellQuote(sourcePath);
    }

    private String buildQuarantineDestination(String threatPath) {
        String quarantineDir = textOf(quarantinePathView);
        String baseName = new File(threatPath).getName();
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return quarantineDir + "/" + timestamp + "-" + baseName;
    }

    private List<String> collectScanTargets(String primaryTarget) {
        LinkedHashSet<String> targets = new LinkedHashSet<String>();
        if (!TextUtils.isEmpty(primaryTarget)) {
            File primary = new File(primaryTarget);
            if (primary.exists()) {
                targets.add(primary.getAbsolutePath());
            }
        }

        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            if (app == null || getPackageName().equals(app.packageName)) {
                continue;
            }
            String source = !TextUtils.isEmpty(app.publicSourceDir) ? app.publicSourceDir : app.sourceDir;
            if (TextUtils.isEmpty(source)) {
                continue;
            }
            File apk = new File(source);
            if (apk.exists() && apk.canRead()) {
                targets.add(apk.getAbsolutePath());
            }
        }
        return new ArrayList<String>(targets);
    }

    private String buildMultiTargetScanCommand(List<String> targets) {
        String clamscan = textOf(clamscanPathView);
        String database = textOf(databasePathView);
        if (TextUtils.isEmpty(clamscan) || TextUtils.isEmpty(database) || targets.isEmpty()) {
            return "";
        }

        File targetsFile = new File(getFilesDir(), SCAN_TARGETS_FILE);
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new java.io.FileOutputStream(targetsFile), "UTF-8");
            for (String target : targets) {
                writer.write(target);
                writer.write('\n');
            }
        } catch (IOException e) {
            appendLog(getString(R.string.scan_targets_failed_prefix) + e.getMessage());
            return "";
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }

        return "result=0; "
                + "while IFS= read -r target; do "
                + "[ -e \"$target\" ] || continue; "
                + buildRuntimeEnvPrefix()
                + shellQuote(clamscan)
                + " -r --infected --suppress-ok-results --database=" + shellQuote(database)
                + " \"$target\"; "
                + "code=$?; "
                + "if [ \"$code\" -gt \"$result\" ]; then result=\"$code\"; fi; "
                + "done < " + shellQuote(targetsFile.getAbsolutePath()) + "; "
                + "exit \"$result\"";
    }

    private String buildRuntimeEnvPrefix() {
        String runtimeRoot = RuntimeAssetsManager.getRuntimeRoot(this);
        String nativeLibDir = getApplicationInfo().nativeLibraryDir;
        String certFile = runtimeRoot + "/usr/etc/tls/cert.pem";
        String opensslConf = runtimeRoot + "/usr/etc/tls/openssl.cnf";
        return "LD_LIBRARY_PATH=" + shellQuote(nativeLibDir) + " "
                + "HOME=" + shellQuote(runtimeRoot) + " "
                + "SSL_CERT_FILE=" + shellQuote(certFile) + " "
                + "OPENSSL_CONF=" + shellQuote(opensslConf) + " ";
    }

    private void setBusy(boolean busy, String status) {
        this.busy = busy;
        saveButton.setEnabled(!busy);
        resetDefaultsButton.setEnabled(!busy);
        checkButton.setEnabled(!busy);
        toggleSettingsButton.setEnabled(!busy);
        statusTextView.setText(status);
        scanProgressView.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            updateDbButton.setEnabled(false);
            quickScanButton.setEnabled(false);
            fullScanButton.setEnabled(false);
        } else {
            updateActionAvailability();
        }
    }

    private void updateActionAvailability() {
        checkButton.setText(getString(R.string.check_button));
        boolean engineReady = moduleReady;
        updateDbButton.setEnabled(engineReady);
        quickScanButton.setEnabled(engineReady && databaseReady);
        fullScanButton.setEnabled(engineReady && databaseReady);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String normalizeStoredPath(String value, String legacyValue, String defaultValue) {
        if (TextUtils.isEmpty(value)) {
            return defaultValue;
        }
        if (!TextUtils.isEmpty(legacyValue) && legacyValue.equals(value)) {
            return defaultValue;
        }
        if (value.startsWith(MODULE_ROOT)) {
            return defaultValue;
        }
        if (value.startsWith(LEGACY_ROOT)) {
            return defaultValue;
        }
        String runtimeRoot = RuntimeAssetsManager.getRuntimeRoot(this);
        if (value.startsWith(runtimeRoot + "/bin")) {
            return defaultValue;
        }
        if (value.startsWith("/data/app/") && (value.endsWith("/libclamscan_exec.so") || value.endsWith("/libfreshclam_exec.so"))) {
            return defaultValue;
        }
        return value;
    }

    private boolean isIgnoredThreat(String threatPath) {
        return getIgnoredThreats().contains(threatPath);
    }

    private Set<String> getIgnoredThreats() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_IGNORED_THREATS, new HashSet<String>());
        return new HashSet<String>(stored);
    }

    private void ignoreThreat(String threatPath) {
        Set<String> ignored = getIgnoredThreats();
        ignored.add(threatPath);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_IGNORED_THREATS, ignored)
                .apply();
    }

    private void clearIgnoredThreats() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_IGNORED_THREATS)
                .apply();
    }

    private String textOf(EditText view) {
        return view.getText().toString().trim();
    }

    private void appendLog(String line) {
        String placeholder = getString(R.string.logs_placeholder);
        CharSequence existing = logTextView.getText();
        if (existing == null || existing.length() == 0 || placeholder.contentEquals(existing)) {
            logTextView.setText(line);
        } else {
            logTextView.append("\n" + line);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
