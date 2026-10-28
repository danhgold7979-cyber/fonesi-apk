package vn.fonesi.nativeprobe;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import vn.fonesi.core.InstalledSource;

/** Only the project-controlled installed synthetic test payload is eligible for the E16 proof. */
final class SyntheticSignedPayloadSource {
    static final String PACKAGE = "vn.fonesi.payload";
    private SyntheticSignedPayloadSource() {}

    @SuppressWarnings("deprecation")
    static InstalledSource fromInstalled(Context context) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, flags);
        ApplicationInfo app = info.applicationInfo;
        if (app == null || app.sourceDir == null || info.packageName == null ||
            !PACKAGE.equals(info.packageName)) {
            throw new IllegalStateException("Signed synthetic test payload was not installed");
        }
        List<String> paths = new ArrayList<>();
        paths.add(app.sourceDir);
        if (app.splitSourceDirs != null) Collections.addAll(paths, app.splitSourceDirs);
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
            ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null || signatures.length == 0)
            throw new IllegalStateException("Synthetic payload has no validated signing identity");
        Set<String> signerDigests = new LinkedHashSet<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures)
            signerDigests.add(SyntheticGuestChannelService.hex(digest.digest(signature.toByteArray())));
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return new InstalledSource(PACKAGE, version, signerDigests, paths);
    }
}
