package com.keytron46.clamguard;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final String PREFS_NAME = "clamguard_prefs";
    private static final String KEY_CLAMSCAN = "clamscan_path";
    private static final String KEY_FRESHCLAM = "freshclam_path";
    private static final String KEY_DATABASE = "database_path";
    private static final String KEY_TARGET = "target_path";
    private static final String KEY_QUARANTINE = "quarantine_path";
    private static final String KEY_IGNORED_THREATS = "ignored_threats";
    private static final String KEY_AUTO_UPDATE = "auto_update_enabled";

    private static final String MODULE_ROOT = "/data/adb/modules/clamguard";
    private static final String DEFAULT_TARGET = "/sdcard";
    private static final String LEGACY_ROOT = "/data/local/tmp/clamav";

    private EditText clamscanPathView;
    private EditText freshclamPathView;
    private EditText databasePathView;
    private EditText targetPathView;
    private EditText quarantinePathView;
    private CheckBox autoUpdateCheckBox;
    private TextView ignoredSummaryView;
    private TextView themeSummaryView;
    private TextView languageSummaryView;
    private TextView permissionsSummaryView;
    private TextView rootStatusView;
    private TextView notificationsStatusView;
    private TextView filesStatusView;
    private TextView overlayStatusView;
    private TextView adminStatusView;

    private static final int REQ_NOTIFICATIONS = 1001;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiConfig.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiConfig.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();
        loadPreferences();
        wireButtons();
        updateIgnoredSummary();
    }

    private void bindViews() {
        clamscanPathView = (EditText) findViewById(R.id.settings_clamscan_path);
        freshclamPathView = (EditText) findViewById(R.id.settings_freshclam_path);
        databasePathView = (EditText) findViewById(R.id.settings_database_path);
        targetPathView = (EditText) findViewById(R.id.settings_target_path);
        quarantinePathView = (EditText) findViewById(R.id.settings_quarantine_path);
        autoUpdateCheckBox = (CheckBox) findViewById(R.id.settings_auto_update_checkbox);
        ignoredSummaryView = (TextView) findViewById(R.id.settings_ignored_summary);
        themeSummaryView = (TextView) findViewById(R.id.settings_theme_summary);
        languageSummaryView = (TextView) findViewById(R.id.settings_language_summary);
        permissionsSummaryView = (TextView) findViewById(R.id.settings_permissions_summary);
        rootStatusView = (TextView) findViewById(R.id.settings_root_status);
        notificationsStatusView = (TextView) findViewById(R.id.settings_notifications_status);
        filesStatusView = (TextView) findViewById(R.id.settings_files_status);
        overlayStatusView = (TextView) findViewById(R.id.settings_overlay_status);
        adminStatusView = (TextView) findViewById(R.id.settings_admin_status);
    }

    private void wireButtons() {
        Button backButton = (Button) findViewById(R.id.settings_back_button);
        Button saveButton = (Button) findViewById(R.id.settings_save_button);
        Button resetButton = (Button) findViewById(R.id.settings_reset_button);
        Button clearIgnoredButton = (Button) findViewById(R.id.settings_clear_ignored_button);
        Button themeSystemButton = (Button) findViewById(R.id.settings_theme_system_button);
        Button themeLightButton = (Button) findViewById(R.id.settings_theme_light_button);
        Button themeDarkButton = (Button) findViewById(R.id.settings_theme_dark_button);
        Button languageSystemButton = (Button) findViewById(R.id.settings_language_system_button);
        Button languageRuButton = (Button) findViewById(R.id.settings_language_ru_button);
        Button languageEnButton = (Button) findViewById(R.id.settings_language_en_button);
        Button requestRootButton = (Button) findViewById(R.id.settings_request_root_button);
        Button notificationsButton = (Button) findViewById(R.id.settings_notifications_button);
        Button filesButton = (Button) findViewById(R.id.settings_files_button);
        Button overlayButton = (Button) findViewById(R.id.settings_overlay_button);
        Button adminButton = (Button) findViewById(R.id.settings_admin_button);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                toast(getString(R.string.saved_message));
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clamscanPathView.setText(RuntimeAssetsManager.getClamscanPath(SettingsActivity.this));
                freshclamPathView.setText(RuntimeAssetsManager.getFreshclamPath(SettingsActivity.this));
                databasePathView.setText(RuntimeAssetsManager.getDatabasePath(SettingsActivity.this));
                targetPathView.setText(DEFAULT_TARGET);
                quarantinePathView.setText(RuntimeAssetsManager.getQuarantinePath(SettingsActivity.this));
                autoUpdateCheckBox.setChecked(true);
                savePreferences();
                toast(getString(R.string.saved_message));
            }
        });

        clearIgnoredButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .remove(KEY_IGNORED_THREATS)
                        .apply();
                updateIgnoredSummary();
                toast(getString(R.string.ignored_cleared_toast));
            }
        });

        themeSystemButton.setOnClickListener(new ThemeClickListener(UiConfig.THEME_SYSTEM));
        themeLightButton.setOnClickListener(new ThemeClickListener(UiConfig.THEME_LIGHT));
        themeDarkButton.setOnClickListener(new ThemeClickListener(UiConfig.THEME_DARK));
        languageSystemButton.setOnClickListener(new LanguageClickListener(UiConfig.LANG_SYSTEM));
        languageRuButton.setOnClickListener(new LanguageClickListener(UiConfig.LANG_RU));
        languageEnButton.setOnClickListener(new LanguageClickListener(UiConfig.LANG_EN));

        requestRootButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestRoot();
            }
        });

        notificationsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestNotifications();
            }
        });

        filesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        overlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        adminButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        new ComponentName(SettingsActivity.this, ClamGuardAdminReceiver.class));
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.permission_admin_button));
                startActivity(intent);
            }
        });
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String defaultClamscan = RuntimeAssetsManager.getClamscanPath(this);
        String defaultFreshclam = RuntimeAssetsManager.getFreshclamPath(this);
        String defaultDatabase = RuntimeAssetsManager.getDatabasePath(this);
        String defaultQuarantine = RuntimeAssetsManager.getQuarantinePath(this);

        clamscanPathView.setText(normalizeStoredPath(prefs.getString(KEY_CLAMSCAN, defaultClamscan), defaultClamscan));
        freshclamPathView.setText(normalizeStoredPath(prefs.getString(KEY_FRESHCLAM, defaultFreshclam), defaultFreshclam));
        databasePathView.setText(normalizeStoredPath(prefs.getString(KEY_DATABASE, defaultDatabase), defaultDatabase));
        targetPathView.setText(emptyToDefault(prefs.getString(KEY_TARGET, DEFAULT_TARGET), DEFAULT_TARGET));
        quarantinePathView.setText(emptyToDefault(prefs.getString(KEY_QUARANTINE, defaultQuarantine), defaultQuarantine));
        autoUpdateCheckBox.setChecked(prefs.getBoolean(KEY_AUTO_UPDATE, true));
    }

    private void savePreferences() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_CLAMSCAN, textOf(clamscanPathView))
                .putString(KEY_FRESHCLAM, textOf(freshclamPathView))
                .putString(KEY_DATABASE, textOf(databasePathView))
                .putString(KEY_TARGET, textOf(targetPathView))
                .putString(KEY_QUARANTINE, textOf(quarantinePathView))
                .putBoolean(KEY_AUTO_UPDATE, autoUpdateCheckBox.isChecked())
                .apply();
        ProtectionScheduler.sync(this);
    }

    private void updateIgnoredSummary() {
        int ignoredCount = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getStringSet(KEY_IGNORED_THREATS, java.util.Collections.<String>emptySet())
                .size();
        ignoredSummaryView.setText(getString(R.string.ignored_summary, ignoredCount));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateIgnoredSummary();
        updateThemeSummary();
        updateLanguageSummary();
        updatePermissionSummary();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            updatePermissionSummary();
        }
    }

    private String textOf(EditText view) {
        return view.getText().toString().trim();
    }

    private String normalizeStoredPath(String value, String defaultValue) {
        if (TextUtils.isEmpty(value) || value.startsWith(LEGACY_ROOT) || value.startsWith(MODULE_ROOT)) {
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

    private String emptyToDefault(String value, String defaultValue) {
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateThemeSummary() {
        String mode = UiConfig.getThemeMode(this);
        if (UiConfig.THEME_LIGHT.equals(mode)) {
            themeSummaryView.setText(getString(R.string.settings_theme_light));
        } else if (UiConfig.THEME_DARK.equals(mode)) {
            themeSummaryView.setText(getString(R.string.settings_theme_dark));
        } else {
            themeSummaryView.setText(getString(R.string.settings_theme_system));
        }
    }

    private void updateLanguageSummary() {
        String code = UiConfig.getLanguageCode(this);
        if (UiConfig.LANG_RU.equals(code)) {
            languageSummaryView.setText(getString(R.string.settings_language_ru));
        } else if (UiConfig.LANG_EN.equals(code)) {
            languageSummaryView.setText(getString(R.string.settings_language_en));
        } else {
            languageSummaryView.setText(getString(R.string.settings_language_system));
        }
    }

    private void updatePermissionSummary() {
        rootStatusView.setText(hasRootBinary() ? getString(R.string.permission_root_ready) : getString(R.string.permission_root_missing));
        notificationsStatusView.setText(hasNotifications() ? getString(R.string.permission_notifications_ready) : getString(R.string.permission_notifications_missing));
        filesStatusView.setText(Environment.isExternalStorageManager()
                ? getString(R.string.permission_files_ready)
                : getString(R.string.permission_files_missing));
        overlayStatusView.setText(Settings.canDrawOverlays(this)
                ? getString(R.string.permission_overlay_ready)
                : getString(R.string.permission_overlay_missing));

        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        boolean isAdmin = devicePolicyManager != null
                && devicePolicyManager.isAdminActive(new ComponentName(this, ClamGuardAdminReceiver.class));
        adminStatusView.setText(isAdmin
                ? getString(R.string.permission_admin_ready)
                : getString(R.string.permission_admin_missing));
    }

    private boolean hasNotifications() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    && notificationManager.areNotificationsEnabled();
        }
        return notificationManager.areNotificationsEnabled();
    }

    private boolean hasRootBinary() {
        return ShellUtils.hasKnownSuBinary();
    }

    private void requestRoot() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                boolean granted = false;
                try {
                    process = ShellUtils.newRootProcess("id")
                            .redirectErrorStream(true)
                            .start();
                    granted = process.waitFor() == 0;
                } catch (Exception ignored) {
                } finally {
                    if (process != null) {
                        process.destroy();
                    }
                }

                final boolean result = granted;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(result ? getString(R.string.permission_root_ready) : getString(R.string.permission_root_missing));
                        updatePermissionSummary();
                    }
                });
            }
        }, "clamguard-request-root").start();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }

        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private class ThemeClickListener implements View.OnClickListener {
        private final String mode;

        ThemeClickListener(String mode) {
            this.mode = mode;
        }

        @Override
        public void onClick(View v) {
            UiConfig.setThemeMode(SettingsActivity.this, mode);
            recreate();
        }
    }

    private class LanguageClickListener implements View.OnClickListener {
        private final String code;

        LanguageClickListener(String code) {
            this.code = code;
        }

        @Override
        public void onClick(View v) {
            UiConfig.setLanguageCode(SettingsActivity.this, code);
            recreate();
        }
    }
}
