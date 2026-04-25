package com.keytron.clamguard;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

    private static final String[] DB_NAMES = {"main", "daily", "bytecode"};
    private static final String BASE_URL = "https://database.clamav.net/";
    private static final int CONNECT_TIMEOUT = 30_000;
    private static final int READ_TIMEOUT = 180_000;
    private static final int BUFFER_SIZE = 65536;

    private DatabaseUpdateRunner() {
    }

    public static Result run(Context context,
                             String freshclamPath,
                             String databasePath,
                             Callback callback) {
        File dbDir = new File(databasePath);
        dbDir.mkdirs();

        ArrayList<String> changedDatabases = new ArrayList<>();

        for (String db : DB_NAMES) {
            String fileName = db + ".cvd";
            File destFile = new File(dbDir, fileName);

            try {
                log(callback, "Проверка " + fileName + "...");
                boolean downloaded = downloadIfNewer(fileName, destFile, callback);
                if (downloaded) {
                    changedDatabases.add(db);
                }
            } catch (IOException e) {
                Log.e("ClamGuard", "DB update error for " + fileName, e);
                log(callback, "Ошибка " + fileName + ": " + e.getMessage());
                boolean anyUpdated = !changedDatabases.isEmpty();
                return new Result(2, anyUpdated, false,
                        "Ошибка загрузки: " + e.getMessage(), changedDatabases);
            }
        }

        boolean updated = !changedDatabases.isEmpty();
        String summary = updated ? join(changedDatabases) + " обновлено" : "базы актуальны";
        return new Result(0, updated, !updated, summary, changedDatabases);
    }

    private static boolean downloadIfNewer(String fileName, File destFile, Callback callback) throws IOException {
        String urlStr = BASE_URL + fileName;

        int remoteVersion = fetchRemoteVersion(urlStr, callback);
        if (remoteVersion > 0 && destFile.exists()) {
            int localVersion = readLocalVersion(destFile);
            if (localVersion >= remoteVersion) {
                log(callback, fileName + ": актуальна (v" + localVersion + ")");
                return false;
            }
            log(callback, fileName + ": обновление v" + localVersion + " → v" + remoteVersion);
        }

        downloadFile(urlStr, destFile, fileName, callback);
        return true;
    }

    private static int fetchRemoteVersion(String urlStr, Callback callback) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(urlStr + "?from=clamguard");
            conn.setRequestProperty("Range", "bytes=0-511");
            conn.setRequestMethod("GET");
            conn.connect();
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) return -1;
            byte[] header = new byte[512];
            int read = 0;
            InputStream in = conn.getInputStream();
            while (read < 512) {
                int n = in.read(header, read, 512 - read);
                if (n < 0) break;
                read += n;
            }
            return parseCvdVersion(new String(header, 0, read, "US-ASCII"));
        } catch (Exception e) {
            log(callback, "Не удалось получить версию: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static int readLocalVersion(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] header = new byte[512];
            int read = 0;
            while (read < 512) {
                int n = fis.read(header, read, 512 - read);
                if (n < 0) break;
                read += n;
            }
            return parseCvdVersion(new String(header, 0, read, "US-ASCII"));
        } catch (Exception e) {
            return -1;
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ignored) {}
        }
    }

    private static int parseCvdVersion(String header) {
        // CVD header: "ClamAV-VDB:DATE:VERSION:..."
        if (!header.startsWith("ClamAV-VDB:")) return -1;
        String[] parts = header.split(":");
        if (parts.length < 3) return -1;
        try {
            return Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void downloadFile(String urlStr, File destFile, String label, Callback callback) throws IOException {
        File tmp = new File(destFile.getParent(), destFile.getName() + ".tmp");
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            conn = openConnection(urlStr + "?from=clamguard");
            conn.connect();
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);

            long total = conn.getContentLengthLong();
            in = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE);
            out = new FileOutputStream(tmp);

            byte[] buf = new byte[BUFFER_SIZE];
            long downloaded = 0;
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                downloaded += n;
                if (total > 0) {
                    int pct = (int) (downloaded * 100 / total);
                    if (pct != lastPct && pct % 5 == 0) {
                        log(callback, label + ": " + pct + "% (" + formatBytes(downloaded) + " / " + formatBytes(total) + ")");
                        lastPct = pct;
                    }
                }
            }
            out.getFD().sync();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
            if (conn != null) conn.disconnect();
        }

        if (!tmp.renameTo(destFile)) {
            destFile.delete();
            if (!tmp.renameTo(destFile)) throw new IOException("Не удалось сохранить " + label);
        }
        log(callback, label + ": готово (" + formatBytes(destFile.length()) + ")");
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "ClamGuard/1.0 (Android)");
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String formatBytes(long v) {
        if (v < 1024) return v + " B";
        int exp = (int) (Math.log(v) / Math.log(1024));
        return String.format("%.1f %sB", v / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static void log(Callback callback, String line) {
        if (callback != null) callback.onLog(line);
    }
}
