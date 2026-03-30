package com.keytron46.clamguard;

import java.io.File;

public final class ShellUtils {
    private static final String[] SU_CANDIDATES = new String[] {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system_ext/bin/su",
            "su"
    };

    private ShellUtils() {
    }

    public static String findSuBinary() {
        for (String candidate : SU_CANDIDATES) {
            if ("su".equals(candidate)) {
                return candidate;
            }
            if (new File(candidate).exists()) {
                return candidate;
            }
        }
        return "su";
    }

    public static boolean hasKnownSuBinary() {
        for (String candidate : SU_CANDIDATES) {
            if ("su".equals(candidate)) {
                continue;
            }
            if (new File(candidate).exists()) {
                return true;
            }
        }
        return false;
    }

    public static ProcessBuilder newRootProcess(String command) {
        return new ProcessBuilder(findSuBinary(), "-c", command);
    }
}
