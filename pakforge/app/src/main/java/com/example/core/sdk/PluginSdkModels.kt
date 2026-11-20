package com.example.core.sdk

import org.json.JSONArray
import org.json.JSONObject

object PakForgeSdkSpec {
    const val SDK_VERSION = "1.0.0"
    const val HOST_API_LEVEL = 1
    const val MIN_SUPPORTED_API_LEVEL = 1
}

data class PluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<PluginVersion> {
    override fun compareTo(other: PluginVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    fun isApiCompatibleWithHost(hostSdk: PluginVersion = parseOrNull(PakForgeSdkSpec.SDK_VERSION)!!): Boolean {
        return major == hostSdk.major && this <= hostSdk
    }

    companion object {
        private val semVerRegex = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-[a-zA-Z0-9.]+)?$")

        fun parseOrNull(raw: String): PluginVersion? {
            val match = semVerRegex.matchEntire(raw.trim()) ?: return null
            val maj = match.groupValues[1].toIntOrNull() ?: return null
            val min = match.groupValues[2].toIntOrNull() ?: return null
            val pat = match.groupValues[3].toIntOrNull() ?: return null
            return PluginVersion(maj, min, pat)
        }
    }
}

enum class PluginPermission(
    val key: String,
    val titleEn: String,
    val titleAr: String,
    val riskSummaryEn: String
) {
    READ_DEVICE_TELEMETRY(
        key = "pakforge.permission.READ_DEVICE_TELEMETRY",
        titleEn = "Device Hardware & Telemetry",
        titleAr = "قراءة عتاد الجهاز والذاكرة",
        riskSummaryEn = "Reads RAM, CPU cores, ABIs, SDK version, and power-save state"
    ),
    READ_DIAGNOSTICS(
        key = "pakforge.permission.READ_DIAGNOSTICS",
        titleEn = "Engine Diagnostics & Shell Status",
        titleAr = "واجهة التشخيص وحالة المحرك",
        riskSummaryEn = "Reads compatibility checks, manifest risks, and Root/Sui/Shizuku status"
    ),
    READ_REPORTS(
        key = "pakforge.permission.READ_REPORTS",
        titleEn = "Compression Reports & History",
        titleAr = "واجهة التقارير وسجل الضغط",
        riskSummaryEn = "Reads past compression reports, saved bytes, and SHA-256 JSON audits"
    ),
    SEND_NOTIFICATIONS(
        key = "pakforge.permission.SEND_NOTIFICATIONS",
        titleEn = "System & In-App Notifications",
        titleAr = "واجهة الإشعارات والتنبيهات",
        riskSummaryEn = "Dispatches notifications to Android Notification Center and Plugin Feed"
    ),
    ACCESS_HOST_TOOLS(
        key = "pakforge.permission.ACCESS_HOST_TOOLS",
        titleEn = "Host Compression & Audit Tools",
        titleAr = "الوصول إلى أدوات البرنامج وفق الصلاحيات",
        riskSummaryEn = "Invokes SHA-256 probes, compatibility checks, and compression presets"
    ),
    PRIVILEGED_SHELL_READ(
        key = "pakforge.permission.PRIVILEGED_SHELL_READ",
        titleEn = "Privileged Shell UID Telemetry",
        titleAr = "فحص هوية وصلاحية Root / Shizuku",
        riskSummaryEn = "Queries active Root/Sui/Shizuku UID without running destructive commands"
    );

    companion object {
        fun fromKeyOrName(raw: String): PluginPermission? {
            val trimmed = raw.trim()
            return entries.firstOrNull { it.key == trimmed || it.name == trimmed }
        }
    }
}

enum class PluginHookType {
    MANUAL_RUN,
    ON_PACKAGE_INSPECTED,
    ON_COMPRESSION_COMPLETE,
    ON_DIAGNOSTICS_CHECK
}

enum class PluginCategory(val titleEn: String, val titleAr: String) {
    COMPRESSION("Compression & Slimming", "الضغط والتقليص"),
    SYSTEM_SHELL("System & Privileged Shell", "أدوات النظام والروت"),
    SECURITY_AUDIT("Security & Audit", "الفحص والأمان"),
    PERFORMANCE("Device & Battery", "الأداء والبطارية"),
    DEVELOPER_TOOL("Developer Tools", "أدوات المطورين")
}

enum class PluginActionStep(val titleEn: String, val titleAr: String) {
    CHECK_DEVICE_HEADROOM("Audit RAM, CPU & Buffer", "فحص الذاكرة والأنوية"),
    AUDIT_SHELL_BACKEND("Check Root/Sui/Shizuku UID", "فحص محرك الصلاحيات والـ UID"),
    INSPECT_ACTIVE_PACKAGE("Inspect Active APK Blueprint", "فحص الحزمة المحمّلة حالياً"),
    APPLY_COMPRESSION_RULE("Apply Smart Compression Preset", "تطبيق قواعد الضغط الذكية"),
    COMPUTE_PACKAGE_DIGEST("Compute SHA-256 & Compat Summary", "حساب SHA-256 وتلخيص التوافق"),
    READ_SAVINGS_HISTORY("Read Reclaimed Storage History", "قراءة تقارير التوفير السابقة"),
    DISPATCH_NOTIFICATION("Dispatch Plugin Notification", "إرسال إشعار النتيجة")
}

data class SmartMatchCriteria(
    val requireSplits: Boolean = false,
    val requireSharedUserId: Boolean = false,
    val requireStaticLibs: Boolean = false,
    val minArchiveMb: Int = 0,
    val minLocaleCount: Int = 0,
    val minAbiCount: Int = 0,
    val requireLowRamOrPowerSave: Boolean = false,
    val requireElevatedShell: Boolean = false,
    val targetPackageRegex: String? = null
)

enum class SignatureTrustLevel(val badgeEn: String, val badgeAr: String) {
    VERIFIED_OFFICIAL("Official Signed", "موقّع رسمياً"),
    VERIFIED_DEVELOPER("Dev ECDSA Signed", "توقيع مطور موثق"),
    BUILTIN("Built-In SDK", "مدمج بالنظام"),
    UNSIGNED("Unsigned Manifest", "غير موقّع"),
    TAMPERED_REJECTED("Signature Mismatch", "توقيع غير صالح / معدل")
}

data class PluginCompressionRule(
    val profile: String? = null,
    val pruneUnusedLocales: Boolean? = null,
    val keptLocales: Set<String>? = null,
    val optimizeLargeImages: Boolean? = null,
    val mergeSplitsIntoUniversal: Boolean? = null,
    val stripSharedUserId: Boolean? = null,
    val patchStaticLibCertDigest: Boolean? = null,
    val bundleCompanionStaticLibs: Boolean? = null
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String = PakForgeSdkSpec.SDK_VERSION,
    val minHostApiLevel: Int = PakForgeSdkSpec.MIN_SUPPORTED_API_LEVEL,
    val author: String,
    val description: String,
    val entryPoint: String,
    val permissions: List<PluginPermission>,
    val hooks: List<PluginHookType> = listOf(PluginHookType.MANUAL_RUN),
    val compressionRule: PluginCompressionRule? = null,
    val category: PluginCategory = PluginCategory.DEVELOPER_TOOL,
    val actions: List<PluginActionStep> = emptyList(),
    val smartMatch: SmartMatchCriteria? = null
)

data class ManifestValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

class PluginSecurityException(
    val pluginId: String,
    val permission: PluginPermission
) : SecurityException(
    "Plugin '$pluginId' denied access: missing granted permission '${permission.key}'"
)

data class PluginRecord(
    val manifest: PluginManifest,
    val enabled: Boolean = true,
    val grantedPermissions: Set<PluginPermission> = manifest.permissions.toSet(),
    val isBuiltInSample: Boolean = false,
    val signatureTrust: SignatureTrustLevel = SignatureTrustLevel.BUILTIN,
    val signerFingerprint: String? = null,
    val installedAtEpochMs: Long = System.currentTimeMillis(),
    val lastExecutionSummary: String? = null,
    val lastExecutionOk: Boolean? = null,
    val lastExecutionEpochMs: Long? = null
)

object PluginManifestCodec {
    private val pluginIdRegex = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")

    fun validate(manifest: PluginManifest): ManifestValidationResult {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()

        if (!pluginIdRegex.matches(manifest.id)) {
            errors.add("Invalid plugin id '${manifest.id}': must use reverse-DNS format (e.g. dev.example.my_plugin)")
        }
        if (manifest.name.isBlank() || manifest.name.length > 48) {
            errors.add("Plugin name must be 1..48 characters")
        }
        val parsedVer = PluginVersion.parseOrNull(manifest.version)
        if (parsedVer == null) {
            errors.add("Invalid SemVer plugin version '${manifest.version}' (expected major.minor.patch)")
        }
        val parsedApi = PluginVersion.parseOrNull(manifest.apiVersion)
        if (parsedApi == null) {
            errors.add("Invalid SemVer apiVersion '${manifest.apiVersion}'")
        } else if (!parsedApi.isApiCompatibleWithHost()) {
            errors.add("Incompatible SDK apiVersion '${manifest.apiVersion}' (host is ${PakForgeSdkSpec.SDK_VERSION})")
        }
        if (manifest.minHostApiLevel > PakForgeSdkSpec.HOST_API_LEVEL || manifest.minHostApiLevel < 1) {
            errors.add("minHostApiLevel ${manifest.minHostApiLevel} exceeds host API level ${PakForgeSdkSpec.HOST_API_LEVEL}")
        }
        if (manifest.entryPoint.isBlank() || !manifest.entryPoint.contains('.')) {
            errors.add("entryPoint must be a fully qualified class name")
        }
        if (manifest.author.isBlank()) {
            warnings.add("Author field is empty")
        }
        if (manifest.hooks.isEmpty()) {
            warnings.add("No lifecycle hooks declared; defaulting to MANUAL_RUN")
        }

        return ManifestValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun parseJson(rawJson: String): Result<PluginManifest> = runCatching {
        val obj = JSONObject(rawJson)
        val permsArray = obj.optJSONArray("permissions") ?: JSONArray()
        val permissions = ArrayList<PluginPermission>()
        for (i in 0 until permsArray.length()) {
            val raw = permsArray.optString(i)
            val mapped = PluginPermission.fromKeyOrName(raw)
                ?: throw IllegalArgumentException("Unknown plugin permission: $raw")
            if (mapped !in permissions) permissions.add(mapped)
        }

        val hooksArray = obj.optJSONArray("hooks") ?: JSONArray()
        val hooks = ArrayList<PluginHookType>()
        for (i in 0 until hooksArray.length()) {
            val raw = hooksArray.optString(i).trim()
            runCatching { PluginHookType.valueOf(raw) }.getOrNull()?.let { hooks.add(it) }
        }
        if (hooks.isEmpty()) hooks.add(PluginHookType.MANUAL_RUN)

        val ruleObj = obj.optJSONObject("compressionRule")
        val compressionRule = ruleObj?.let { r ->
            val locArr = r.optJSONArray("keptLocales")
            val locales = if (locArr != null) {
                buildSet<String> {
                    for (i in 0 until locArr.length()) {
                        val loc = locArr.optString(i).trim()
                        if (loc.isNotEmpty()) add(loc)
                    }
                }
            } else null

            PluginCompressionRule(
                profile = if (r.has("profile")) r.optString("profile") else null,
                pruneUnusedLocales = if (r.has("pruneUnusedLocales")) r.optBoolean("pruneUnusedLocales") else null,
                keptLocales = locales,
                optimizeLargeImages = if (r.has("optimizeLargeImages")) r.optBoolean("optimizeLargeImages") else null,
                mergeSplitsIntoUniversal = if (r.has("mergeSplitsIntoUniversal")) r.optBoolean("mergeSplitsIntoUniversal") else null,
                stripSharedUserId = if (r.has("stripSharedUserId")) r.optBoolean("stripSharedUserId") else null,
                patchStaticLibCertDigest = if (r.has("patchStaticLibCertDigest")) r.optBoolean("patchStaticLibCertDigest") else null,
                bundleCompanionStaticLibs = if (r.has("bundleCompanionStaticLibs")) r.optBoolean("bundleCompanionStaticLibs") else null
            )
        }

        val category = obj.optString("category", "").trim().let { raw ->
            runCatching { PluginCategory.valueOf(raw) }.getOrNull()
        } ?: PluginCategory.DEVELOPER_TOOL

        val actionsArray = obj.optJSONArray("actions") ?: JSONArray()
        val actions = ArrayList<PluginActionStep>()
        for (i in 0 until actionsArray.length()) {
            val raw = actionsArray.optString(i).trim()
            runCatching { PluginActionStep.valueOf(raw) }.getOrNull()?.let { actions.add(it) }
        }

        val smObj = obj.optJSONObject("smartMatch")
        val smartMatch = smObj?.let { s ->
            SmartMatchCriteria(
                requireSplits = s.optBoolean("requireSplits", false),
                requireSharedUserId = s.optBoolean("requireSharedUserId", false),
                requireStaticLibs = s.optBoolean("requireStaticLibs", false),
                minArchiveMb = s.optInt("minArchiveMb", 0),
                minLocaleCount = s.optInt("minLocaleCount", 0),
                minAbiCount = s.optInt("minAbiCount", 0),
                requireLowRamOrPowerSave = s.optBoolean("requireLowRamOrPowerSave", false),
                requireElevatedShell = s.optBoolean("requireElevatedShell", false),
                targetPackageRegex = if (s.has("targetPackageRegex")) s.optString("targetPackageRegex") else null
            )
        }

        val manifest = PluginManifest(
            id = obj.getString("id").trim(),
            name = obj.getString("name").trim(),
            version = obj.getString("version").trim(),
            apiVersion = obj.optString("apiVersion", PakForgeSdkSpec.SDK_VERSION).trim(),
            minHostApiLevel = obj.optInt("minHostApiLevel", PakForgeSdkSpec.MIN_SUPPORTED_API_LEVEL),
            author = obj.optString("author", "External Developer").trim(),
            description = obj.optString("description", "").trim(),
            entryPoint = obj.getString("entryPoint").trim(),
            permissions = permissions,
            hooks = hooks,
            compressionRule = compressionRule,
            category = category,
            actions = actions,
            smartMatch = smartMatch
        )
        val validation = validate(manifest)
        require(validation.isValid) { validation.errors.joinToString("; ") }
        manifest
    }

    fun toJson(manifest: PluginManifest, indent: Int = 2): String {
        val obj = JSONObject().apply {
            put("id", manifest.id)
            put("name", manifest.name)
            put("version", manifest.version)
            put("apiVersion", manifest.apiVersion)
            put("minHostApiLevel", manifest.minHostApiLevel)
            put("author", manifest.author)
            put("description", manifest.description)
            put("entryPoint", manifest.entryPoint)
            put("category", manifest.category.name)
            put("permissions", JSONArray().apply {
                manifest.permissions.forEach { put(it.key) }
            })
            put("hooks", JSONArray().apply {
                manifest.hooks.forEach { put(it.name) }
            })
            if (manifest.actions.isNotEmpty()) {
                put("actions", JSONArray().apply {
                    manifest.actions.forEach { put(it.name) }
                })
            }
            manifest.smartMatch?.let { s ->
                put("smartMatch", JSONObject().apply {
                    put("requireSplits", s.requireSplits)
                    put("requireSharedUserId", s.requireSharedUserId)
                    put("requireStaticLibs", s.requireStaticLibs)
                    put("minArchiveMb", s.minArchiveMb)
                    put("minLocaleCount", s.minLocaleCount)
                    put("minAbiCount", s.minAbiCount)
                    put("requireLowRamOrPowerSave", s.requireLowRamOrPowerSave)
                    put("requireElevatedShell", s.requireElevatedShell)
                    s.targetPackageRegex?.let { put("targetPackageRegex", it) }
                })
            }
            manifest.compressionRule?.let { rule ->
                put("compressionRule", JSONObject().apply {
                    rule.profile?.let { put("profile", it) }
                    rule.pruneUnusedLocales?.let { put("pruneUnusedLocales", it) }
                    rule.keptLocales?.let { locs ->
                        put("keptLocales", JSONArray().apply { locs.forEach { put(it) } })
                    }
                    rule.optimizeLargeImages?.let { put("optimizeLargeImages", it) }
                    rule.mergeSplitsIntoUniversal?.let { put("mergeSplitsIntoUniversal", it) }
                    rule.stripSharedUserId?.let { put("stripSharedUserId", it) }
                    rule.patchStaticLibCertDigest?.let { put("patchStaticLibCertDigest", it) }
                    rule.bundleCompanionStaticLibs?.let { put("bundleCompanionStaticLibs", it) }
                })
            }
        }
        return obj.toString(indent)
    }
}
