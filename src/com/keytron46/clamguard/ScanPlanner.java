package com.keytron46.clamguard;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScanPlanner {
    public static final class ScanItem {
        public final String path;
        public final long sizeBytes;
        public final long modifiedAt;

        ScanItem(String path, long sizeBytes, long modifiedAt) {
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.modifiedAt = modifiedAt;
        }
    }

    public static final class ScanPlan {
        public final List<ScanItem> items;
        public final long totalBytes;

        ScanPlan(List<ScanItem> items, long totalBytes) {
            this.items = items;
            this.totalBytes = totalBytes;
        }
    }

    public static final class ScanSummary {
        public final int totalFiles;
        public final long totalBytes;

        ScanSummary(int totalFiles, long totalBytes) {
            this.totalFiles = totalFiles;
            this.totalBytes = totalBytes;
        }
    }

    private ScanPlanner() {
    }

    public static ScanPlan buildPlan(Context context, String primaryTarget, boolean includeInstalledApks, long modifiedAfter) {
        ArrayList<ScanItem> items = new ArrayList<ScanItem>();
        Set<String> seenPaths = new LinkedHashSet<String>();
        Set<String> excludedRoots = buildExcludedRoots(context);
        long totalBytes = 0L;

        if (!TextUtils.isEmpty(primaryTarget)) {
            totalBytes += addPath(new File(primaryTarget), items, seenPaths, modifiedAfter, excludedRoots);
        }

        if (includeInstalledApks) {
            totalBytes += addInstalledApks(context, items, seenPaths, modifiedAfter, excludedRoots);
        }

        return new ScanPlan(items, totalBytes);
    }

    public static ScanSummary summarize(Context context, String primaryTarget, boolean includeInstalledApks, long modifiedAfter) {
        ScanPlan plan = buildPlan(context, primaryTarget, includeInstalledApks, modifiedAfter);
        return new ScanSummary(plan.items.size(), plan.totalBytes);
    }

    private static long addInstalledApks(Context context,
                                         List<ScanItem> items,
                                         Set<String> seenPaths,
                                         long modifiedAfter,
                                         Set<String> excludedRoots) {
        long totalBytes = 0L;
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            if (app == null || context.getPackageName().equals(app.packageName)) {
                continue;
            }
            String source = !TextUtils.isEmpty(app.publicSourceDir) ? app.publicSourceDir : app.sourceDir;
            if (TextUtils.isEmpty(source)) {
                source = app.sourceDir;
            }
            totalBytes += addPath(new File(source), items, seenPaths, modifiedAfter, excludedRoots);

            String[] splitSources = app.splitPublicSourceDirs != null ? app.splitPublicSourceDirs : app.splitSourceDirs;
            if (splitSources == null) {
                continue;
            }
            for (String splitSource : splitSources) {
                if (TextUtils.isEmpty(splitSource)) {
                    continue;
                }
                totalBytes += addPath(new File(splitSource), items, seenPaths, modifiedAfter, excludedRoots);
            }
        }
        return totalBytes;
    }

    private static long addPath(File file,
                                List<ScanItem> items,
                                Set<String> seenPaths,
                                long modifiedAfter,
                                Set<String> excludedRoots) {
        if (file == null || !file.exists() || !file.canRead()) {
            return 0L;
        }
        if (isExcluded(file, excludedRoots)) {
            return 0L;
        }

        long totalBytes = 0L;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return 0L;
            }
            for (File child : children) {
                totalBytes += addPath(child, items, seenPaths, modifiedAfter, excludedRoots);
            }
            return totalBytes;
        }

        String path = file.getAbsolutePath();
        if (seenPaths.contains(path)) {
            return 0L;
        }
        if (modifiedAfter > 0L && file.lastModified() < modifiedAfter) {
            return 0L;
        }

        seenPaths.add(path);
        long sizeBytes = Math.max(file.length(), 1L);
        items.add(new ScanItem(path, sizeBytes, file.lastModified()));
        return sizeBytes;
    }

    private static Set<String> buildExcludedRoots(Context context) {
        LinkedHashSet<String> roots = new LinkedHashSet<String>();
        roots.add(RuntimeAssetsManager.getQuarantinePath(context));
        roots.add(RuntimeAssetsManager.getRuntimeRoot(context));
        return roots;
    }

    private static boolean isExcluded(File file, Set<String> excludedRoots) {
        if (excludedRoots == null || excludedRoots.isEmpty()) {
            return false;
        }
        String path = file.getAbsolutePath();
        for (String excludedRoot : excludedRoots) {
            if (!TextUtils.isEmpty(excludedRoot) && path.startsWith(excludedRoot)) {
                return true;
            }
        }
        return false;
    }
}
