package com.keytron46.clamguard;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DatabaseUpdateRunner {
    public interface Callback {
        void onLog(String line);
    }

    public static final class Result {
        public final int exitCode;
        public final boolean updated;
        public final boolean upToDate;
        public final String summary;
        public final List<String> changedDatabases;

        Result(int exitCode, boolean updated, boolean upToDate, String summary, List<String> changedDatabases) {
            this.exitCode = exitCode;
            this.updated = updated;
            this.upToDate = upToDate;
            this.summary = summary;
            this.changedDatabases = changedDatabases;
        }
    }

    private DatabaseUpdateRunner() {
    }

    public static Result run(Context context,
                             String freshclamPath,
                             String databasePath,
                             Callback callback) {
        Process process = null;
        int exitCode = -1;
        boolean updated = false;
        boolean upToDate = false;
        ArrayList<String> changedDatabases = new ArrayList<String>();

        try {
            String command = ClamScanner.buildRuntimeEnvPrefix(context)
                    + shellQuote(freshclamPath)
                    + " --stdout --datadir=" + shellQuote(databasePath)
                    + " --config-file=" + shellQuote(RuntimeAssetsManager.getFreshclamConfigPath(context));
            if (callback != null) {
                callback.onLog("$ " + command);
            }

            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (callback != null) {
                    callback.onLog(line);
                }
                String lower = line.toLowerCase(Locale.US);
                if (lower.contains("is up-to-date") || lower.contains("up-to-date")) {
                    upToDate = true;
                }
                if (lower.contains("updated") || lower.contains("downloaded")) {
                    updated = true;
                    maybeAddDatabaseName(line, changedDatabases);
                }
            }
            exitCode = process.waitFor();
        } catch (IOException e) {
            if (callback != null) {
                callback.onLog("IO error: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (callback != null) {
                callback.onLog("Interrupted.");
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        String summary = buildSummary(exitCode, updated, upToDate, changedDatabases);
        return new Result(exitCode, updated, upToDate, summary, changedDatabases);
    }

    private static String buildSummary(int exitCode, boolean updated, boolean upToDate, List<String> changedDatabases) {
        if (exitCode != 0) {
            return "";
        }
        if (updated && !changedDatabases.isEmpty()) {
            return join(changedDatabases);
        }
        if (updated) {
            return "updated";
        }
        if (upToDate) {
            return "up-to-date";
        }
        return "ok";
    }

    private static void maybeAddDatabaseName(String line, List<String> changedDatabases) {
        String lower = line.toLowerCase(Locale.US);
        addIfMatch(lower, line, "main", changedDatabases);
        addIfMatch(lower, line, "daily", changedDatabases);
        addIfMatch(lower, line, "bytecode", changedDatabases);
    }

    private static void addIfMatch(String lower, String original, String key, List<String> changedDatabases) {
        if (!lower.contains(key)) {
            return;
        }
        if (!changedDatabases.contains(key)) {
            changedDatabases.add(key);
        }
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
