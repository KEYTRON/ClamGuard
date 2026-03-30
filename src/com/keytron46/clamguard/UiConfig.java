package com.keytron46.clamguard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public final class UiConfig {
    public static final String PREFS_NAME = "clamguard_prefs";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_LANGUAGE_CODE = "language_code";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String LANG_SYSTEM = "system";
    public static final String LANG_RU = "ru";
    public static final String LANG_EN = "en";

    private UiConfig() {
    }

    public static Context wrap(Context base) {
        SharedPreferences prefs = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String themeMode = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM);
        String languageCode = prefs.getString(KEY_LANGUAGE_CODE, LANG_SYSTEM);

        Configuration config = new Configuration(base.getResources().getConfiguration());
        applyThemeMode(config, themeMode, base.getResources().getConfiguration());
        applyLanguage(config, languageCode);
        return base.createConfigurationContext(config);
    }

    public static void applyTheme(Activity activity) {
        activity.setTheme(R.style.ClamGuardTheme);
    }

    public static String getThemeMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, String mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_MODE, mode)
                .apply();
    }

    public static String getLanguageCode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_CODE, LANG_SYSTEM);
    }

    public static void setLanguageCode(Context context, String code) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE_CODE, code)
                .apply();
    }

    private static void applyThemeMode(Configuration target, String themeMode, Configuration source) {
        int currentNight = source.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int targetNight = currentNight;
        if (THEME_LIGHT.equals(themeMode)) {
            targetNight = Configuration.UI_MODE_NIGHT_NO;
        } else if (THEME_DARK.equals(themeMode)) {
            targetNight = Configuration.UI_MODE_NIGHT_YES;
        }
        target.uiMode = (target.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | targetNight;
    }

    private static void applyLanguage(Configuration config, String languageCode) {
        if (LANG_SYSTEM.equals(languageCode)) {
            return;
        }

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new android.os.LocaleList(locale));
        }
    }
}
