package com.example.core.privileged

enum class PrivilegedBackendKind {
    AUTO,
    ROOT_SU,
    SUI,
    SHIZUKU,
    DHIZUKU,
    STANDARD
}

enum class RootFlavor(val displayName: String) {
    KERNEL_SU("KernelSU"),
    APATCH("APatch"),
    MAGISK("Magisk"),
    GENERIC_SU("Root (su)"),
    NONE("No Root")
}

data class BackendAvailability(
    val kind: PrivilegedBackendKind,
    val title: String,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val isAuthorized: Boolean,
    val uid: Int? = null,
    val detail: String = ""
) {
    val isReady: Boolean get() = isRunning && isAuthorized
}

data class PrivilegedBridgeSnapshot(
    val preferredMode: PrivilegedBackendKind = PrivilegedBackendKind.AUTO,
    val activeBackend: PrivilegedBackendKind = PrivilegedBackendKind.STANDARD,
    val activeDetail: String = "Standard PackageInstaller",
    val activeUid: Int? = null,
    val rootFlavor: RootFlavor = RootFlavor.NONE,
    val backends: List<BackendAvailability> = emptyList()
) {
    val isElevatedReady: Boolean
        get() = activeBackend == PrivilegedBackendKind.ROOT_SU ||
            activeBackend == PrivilegedBackendKind.SUI ||
            activeBackend == PrivilegedBackendKind.SHIZUKU
}
