package com.example.core.shizuku;

import android.os.ParcelFileDescriptor;

interface IPakForgeShellService {
    void destroy();
    int getUid();
    String createInstallSession(long totalSizeBytes, boolean allowDowngrade, String installerPkg);
    boolean writeSessionFile(String sessionId, String entryName, long sizeBytes, in ParcelFileDescriptor pfd);
    String commitInstallSession(String sessionId);
    void abandonInstallSession(String sessionId);
    String uninstallPackageForUser(String packageName, boolean keepData);
    String setPackageEnabledState(String packageName, boolean enabled);
}
