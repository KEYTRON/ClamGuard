package com.keytron46.clamguard;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

public final class RuntimeAssetsManager {
    private static final String ASSET_ROOT = "runtime";
    private static final String MARKER_NAME = ".runtime-version";
    private static final String RUNTIME_VERSION = "built-in-runtime-v2";

    private RuntimeAssetsManager() {
    }

    public static String getRuntimeRoot(Context context) {
        return new File(context.getFilesDir(), "runtime").getAbsolutePath();
    }

    public static String getClamscanPath(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libclamscan_exec.so").getAbsolutePath();
    }

    public static String getFreshclamPath(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libfreshclam_exec.so").getAbsolutePath();
    }

    public static String getDatabasePath(Context context) {
        return new File(getRuntimeRoot(context), "db").getAbsolutePath();
    }

    public static String getFreshclamConfigPath(Context context) {
        return new File(getRuntimeRoot(context), "usr/etc/clamav/freshclam.conf").getAbsolutePath();
    }

    public static String getQuarantinePath(Context context) {
        return "/sdcard/ClamGuard/quarantine";
    }

    public static void ensureInstalled(Context context) throws IOException {
        File root = new File(getRuntimeRoot(context));
        File marker = new File(root, MARKER_NAME);
        if (root.exists() && marker.exists() && RUNTIME_VERSION.equals(readFile(marker))) {
            return;
        }

        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IOException("Cannot create runtime directory: " + root);
        }

        copyAssetTree(context.getAssets(), ASSET_ROOT, root);
        ensureDirectory(new File(getDatabasePath(context)));
        writeFreshclamConfig(context);
        writeMarker(marker);
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File targetDir) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, targetDir);
            return;
        }

        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException("Cannot create directory: " + targetDir);
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childTarget = new File(targetDir, child);
            String[] grandChildren = assets.list(childAssetPath);
            if (grandChildren != null && grandChildren.length > 0) {
                copyAssetTree(assets, childAssetPath, childTarget);
            } else {
                copyAssetFile(assets, childAssetPath, childTarget);
            }
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create parent: " + parent);
        }

        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = assets.open(assetPath);
            output = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } finally {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
        }
    }

    private static void writeFreshclamConfig(Context context) throws IOException {
        File configFile = new File(getFreshclamConfigPath(context));
        File logDir = new File(getRuntimeRoot(context), "var/log/clamav");
        ensureDirectory(logDir);

        String config =
                "DatabaseDirectory " + getDatabasePath(context) + "\n"
                        + "UpdateLogFile " + new File(logDir, "freshclam.log").getAbsolutePath() + "\n"
                        + "LogTime yes\n"
                        + "Foreground yes\n"
                        + "Checks 12\n"
                        + "DNSDatabaseInfo current.cvd.clamav.net\n"
                        + "DatabaseMirror database.clamav.net\n"
                        + "ConnectTimeout 30\n"
                        + "ReceiveTimeout 60\n"
                        + "TestDatabases yes\n";

        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(configFile), "UTF-8");
            writer.write(config);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static void writeMarker(File marker) throws IOException {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(marker), "UTF-8");
            writer.write(RUNTIME_VERSION);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create directory: " + dir);
        }
    }

    private static String readFile(File file) throws IOException {
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = input.read(data);
            if (read <= 0) {
                return "";
            }
            return new String(data, 0, read, "UTF-8").trim();
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
