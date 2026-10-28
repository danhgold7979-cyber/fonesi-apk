package vn.fonesi.nativeprobe;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import vn.fonesi.core.InstalledSource;

/**
 * Strictly test-owned package. The original installed files are read only for signed identity
 * and copied to a new, independently verified host-private APK cluster before execution.
 */
final class SyntheticSignedSplitSource {
    static final String PACKAGE = "vn.fonesi.splitfixture";
    static final String FEATURE = "feature.alpha";
    static final String DEPENDENT = "feature.beta";
    private SyntheticSignedSplitSource() {}

    @SuppressWarnings("deprecation")
    static InstalledSource fromInstalled(Context context) throws Exception {
        return fromInstalled(context, false);
    }

    static InstalledSource fromInstalled(Context context, boolean withDependent) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = pm.getPackageInfo(PACKAGE, flags);
        if (!PACKAGE.equals(info.packageName) || info.applicationInfo == null)
            throw new IllegalStateException("Only exact synthetic fixture package accepted");
        ApplicationInfo app = info.applicationInfo;
        int expected = withDependent ? 2 : 1;
        if (app.sourceDir == null || app.splitSourceDirs == null ||
            app.splitSourceDirs.length != expected || app.splitNames == null ||
            app.splitNames.length != expected) {
            throw new IllegalStateException("Exact signed synthetic split member count required");
        }
        java.util.Map<String, String> byName = new java.util.LinkedHashMap<>();
        for (int index = 0; index < app.splitNames.length; index++) {
            if (byName.put(app.splitNames[index], app.splitSourceDirs[index]) != null)
                throw new SecurityException("Duplicate synthetic feature split identity");
        }
        if (!byName.keySet().equals(withDependent
            ? java.util.Set.of(FEATURE, DEPENDENT) : java.util.Set.of(FEATURE))) {
            throw new SecurityException("Unexpected synthetic APK split name");
        }
        List<String> paths = new ArrayList<>();
        paths.add(app.sourceDir);
        paths.add(byName.get(FEATURE));
        if (withDependent) paths.add(byName.get(DEPENDENT));
        if (paths.stream().anyMatch(p -> p == null || p.isEmpty()) ||
            new java.util.HashSet<>(paths).size() != paths.size())
            throw new SecurityException("Synthetic private input split member paths overlap");
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
            ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null || signatures.length == 0)
            throw new SecurityException("Synthetic split signing identity missing");
        Set<String> digests = new LinkedHashSet<>();
        for (Signature signer : signatures) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray());
            digests.add(hex(hash));
        }
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        if (version != 1L) throw new SecurityException("Only pinned synthetic split version 1 accepted");
        return new InstalledSource(PACKAGE, version, digests, paths);
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(digits[(b >>> 4) & 15]).append(digits[b & 15]);
        }
        return result.toString();
    }
}
