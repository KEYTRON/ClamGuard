package com.keytron.clamguard;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClamScanner {
    public interface ProgressCallback {
        void onLog(String line);
        void onProgress(long scannedBytes, long totalBytes, int scannedFiles, int totalFiles, ScanPlanner.ScanItem currentItem);
    }

    public static final class Result {
        public final int exitCode;
        public final List<String> threats;
        public final long scannedBytes;
        public final int scannedFiles;

        Result(int exitCode, List<String> threats, long scannedBytes, int scannedFiles) {
            this.exitCode = exitCode;
            this.threats = threats;
            this.scannedBytes = scannedBytes;
            this.scannedFiles = scannedFiles;
        }
    }

    private ClamScanner() {
    }

    public static Result scanPlan(Context context,
                                  String clamscanPath,
                                  String databasePath,
                                  ScanPlanner.ScanPlan plan,
                                  Set<String> ignoredThreats,
                                  ProgressCallback callback) {
        if (plan == null || plan.items.isEmpty()) {
            return new Result(0, new ArrayList<String>(), 0L, 0);
        }

        LinkedHashSet<String> threats = new LinkedHashSet<String>();
        int resultCode = 0;
        long scannedBytes = 0L;
        int scannedFiles = 0;

        java.io.File listFile = new java.io.File(context.getCacheDir(), "clamguard_scan_list.txt");
        java.io.BufferedWriter writer = null;
        try {
            writer = new java.io.BufferedWriter(new java.io.FileWriter(listFile));
            for (ScanPlanner.ScanItem item : plan.items) {
                writer.write(item.path);
                writer.newLine();
            }
        } catch (IOException e) {
            if (callback != null) callback.onLog("Failed to write scan list: " + e.getMessage());
            return new Result(2, new ArrayList<String>(), 0L, 0);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
        }

        Process process = null;
        try {
            String command = buildRuntimeEnvPrefix(context)
                    + shellQuote(clamscanPath)
                    + " --cvdcertsdir=" + shellQuote(RuntimeAssetsManager.getCertsPath(context))
                    + " --database=" + shellQuote(databasePath)
                    + " --file-list=" + shellQuote(listFile.getAbsolutePath());
            if (callback != null) {
                callback.onProgress(0L, plan.totalBytes, 0, plan.items.size(), plan.items.get(0));
                callback.onLog("$ " + command);
            }

            process = ShellUtils.newRootProcess(command)
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int itemIndex = 0;
            while ((line = reader.readLine()) != null) {
                if (callback != null) {
                    callback.onLog(line);
                }
                
                boolean isFileResult = false;
                if (line.endsWith(" FOUND")) {
                    isFileResult = true;
                    int colonIdx = line.lastIndexOf(": ");
                    if (colonIdx > 0) {
                        String threatPath = line.substring(0, colonIdx);
                        if (ignoredThreats == null || !ignoredThreats.contains(threatPath)) {
                            threats.add(threatPath);
                        }
                    }
                } else if (line.endsWith(" OK") || line.endsWith(" ERROR") || line.endsWith(" Excluded")) {
                    isFileResult = true;
                }

                if (isFileResult && itemIndex < plan.items.size()) {
                    ScanPlanner.ScanItem item = plan.items.get(itemIndex);
                    scannedFiles++;
                    scannedBytes += item.sizeBytes;
                    itemIndex++;
                    if (callback != null) {
                        ScanPlanner.ScanItem nextItem = itemIndex < plan.items.size() ? plan.items.get(itemIndex) : item;
                        callback.onProgress(scannedBytes, plan.totalBytes, scannedFiles, plan.items.size(), nextItem);
                    }
                }
            }

            int exitCode = process.waitFor();
            Log.w("ClamGuard", "clamscan exit code: " + exitCode);
            if (callback != null) callback.onLog("[exit:" + exitCode + "]");
            if (exitCode > resultCode) {
                resultCode = exitCode;
            }
        } catch (IOException e) {
            Log.w("ClamGuard", "clamscan IO error: " + e.getMessage());
            if (callback != null) {
                callback.onLog("IO error: " + e.getMessage());
            }
            resultCode = Math.max(resultCode, 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (callback != null) {
                callback.onLog("Interrupted.");
            }
            resultCode = Math.max(resultCode, 2);
        } finally {
            if (process != null) {
                process.destroy();
            }
            listFile.delete();
        }

        return new Result(resultCode, new ArrayList<String>(threats), scannedBytes, scannedFiles);
    }

    public static String buildRuntimeEnvPrefix(Context context) {
        String runtimeRoot = RuntimeAssetsManager.getRuntimeRoot(context);
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        String certFile = runtimeRoot + "/usr/etc/tls/cert.pem";
        String opensslConf = runtimeRoot + "/usr/etc/tls/openssl.cnf";
        return "LD_LIBRARY_PATH=" + shellQuote(nativeLibDir) + " "
                + "HOME=" + shellQuote(runtimeRoot) + " "
                + "SSL_CERT_FILE=" + shellQuote(certFile) + " "
                + "OPENSSL_CONF=" + shellQuote(opensslConf) + " ";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
