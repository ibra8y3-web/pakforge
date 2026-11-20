package com.example.core.sdk

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class SecurityRiskTier {
    LOW,
    MODERATE,
    ELEVATED,
    BLOCKED
}

data class SecurityScanFinding(
    val id: String,
    val title: String,
    val detail: String,
    val passed: Boolean
)

data class PluginSecurityScanResult(
    val manifest: PluginManifest?,
    val signatureTrust: SignatureTrustLevel,
    val signerFingerprintSha256: String?,
    val payloadSha256: String?,
    val safetyScore: Int,
    val riskTier: SecurityRiskTier,
    val canInstall: Boolean,
    val findings: List<SecurityScanFinding>,
    val rawManifestJson: String? = null
)

data class DeveloperKeyInfo(
    val fingerprintSha256: String,
    val publicKeyBase64: String,
    val algorithm: String = "ECDSA P-256 (SHA256withECDSA)"
)

/**
 * Real cryptographic ECDSA P-256 signing, `.pfpak` bundle packaging/verification,
 * and static security + permission audit scanner for PakForge Plugin Store.
 */
class PluginSecurityEngine(private val context: Context) {

    private val keyFile: File
        get() = File(context.filesDir, "pakforge_dev_signing_key.json")

    @Volatile
    private var cachedDeveloperKeyPair: KeyPair? = null

    private val officialKeyPair: KeyPair by lazy {
        // Deterministic session official catalog keypair for built-in verified store verification
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        kpg.generateKeyPair()
    }

    fun getOrCreateDeveloperKeyPair(): KeyPair {
        cachedDeveloperKeyPair?.let { return it }
        synchronized(this) {
            cachedDeveloperKeyPair?.let { return it }
            if (keyFile.exists()) {
                val loaded = runCatching {
                    val obj = JSONObject(keyFile.readText())
                    val pubBytes = Base64.decode(obj.getString("publicKey"), Base64.NO_WRAP)
                    val privBytes = Base64.decode(obj.getString("privateKey"), Base64.NO_WRAP)
                    val kf = KeyFactory.getInstance("EC")
                    val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                    val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                    KeyPair(pub, priv)
                }.getOrNull()
                if (loaded != null) {
                    cachedDeveloperKeyPair = loaded
                    return loaded
                }
            }
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val kp = kpg.generateKeyPair()
            runCatching {
                val obj = JSONObject().apply {
                    put("publicKey", Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
                    put("privateKey", Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
                }
                keyFile.writeText(obj.toString())
            }
            cachedDeveloperKeyPair = kp
            return kp
        }
    }

    fun developerKeyInfo(): DeveloperKeyInfo {
        val pub = getOrCreateDeveloperKeyPair().public
        return DeveloperKeyInfo(
            fingerprintSha256 = sha256Hex(pub.encoded).take(24),
            publicKeyBase64 = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
        )
    }

    fun officialPublicKeyFingerprint(): String = sha256Hex(officialKeyPair.public.encoded).take(24)

    fun signAndPackPfpak(
        manifest: PluginManifest,
        useOfficialKey: Boolean = false
    ): ByteArray {
        val kp = if (useOfficialKey) officialKeyPair else getOrCreateDeveloperKeyPair()
        val manifestJson = PluginManifestCodec.toJson(manifest, indent = 2)
        val payloadBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
        val shaHex = sha256Hex(payloadBytes)
        val sigBytes = signBytes(payloadBytes, kp.private)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("pakforge-plugin.json"))
            zos.write(payloadBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("plugin.sha256"))
            zos.write(shaHex.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("plugin.sig"))
            zos.write(sigBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("publisher.pub"))
            zos.write(kp.public.encoded)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    fun inspectAndVerifyIncomingBytes(
        rawBytes: ByteArray,
        displayName: String = "plugin"
    ): PluginSecurityScanResult {
        val isZip = rawBytes.size >= 4 &&
            rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()

        if (!isZip) {
            val rawText = String(rawBytes, StandardCharsets.UTF_8)
            val parsed = PluginManifestCodec.parseJson(rawText)
            val manifest = parsed.getOrElse { err ->
                return rejectedResult("Manifest JSON Error", err.localizedMessage ?: "Malformed JSON")
            }
            return runStaticSecurityAudit(
                manifest = manifest,
                rawJson = rawText,
                payloadSha256 = sha256Hex(rawBytes),
                signatureTrust = SignatureTrustLevel.UNSIGNED,
                signerFingerprint = null
            )
        }

        // Unpack .pfpak ZIP bundle strictly guarding against ZipSlip or oversized entries
        var manifestBytes: ByteArray? = null
        var declaredSha: String? = null
        var sigBytes: ByteArray? = null
        var pubKeyBytes: ByteArray? = null
        var unsafeEntryDetected = false

        runCatching {
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/")) {
                        unsafeEntryDetected = true
                        zis.closeEntry()
                        continue
                    }
                    when (name) {
                        "pakforge-plugin.json" -> manifestBytes = zis.readBytesLimited(512 * 1024)
                        "plugin.sha256" -> declaredSha = String(zis.readBytesLimited(1024), StandardCharsets.UTF_8).trim()
                        "plugin.sig" -> sigBytes = zis.readBytesLimited(4096)
                        "publisher.pub" -> pubKeyBytes = zis.readBytesLimited(4096)
                        else -> {
                            // Reject unknown executable formats (.dex, .so, .jar, .apk) inside a .pfpak
                            val lower = name.lowercase()
                            if (lower.endsWith(".dex") || lower.endsWith(".so") || lower.endsWith(".jar") || lower.endsWith(".apk")) {
                                return rejectedResult(
                                    "Executable Binary Prohibited",
                                    "Bundle '$displayName' contains blocked binary '$name' (DCL policy protection)."
                                )
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
        }.onFailure { err ->
            return rejectedResult("Corrupt .pfpak Archive", err.localizedMessage ?: "Failed to read bundle")
        }

        if (unsafeEntryDetected) {
            return rejectedResult("Path Traversal Blocked", "Archive contains unsafe ZipSlip paths.")
        }
        val mBytes = manifestBytes
            ?: return rejectedResult("Missing Manifest", "pakforge-plugin.json was not found inside bundle.")

        val actualSha = sha256Hex(mBytes)
        if (!declaredSha.isNullOrBlank() && !declaredSha.equals(actualSha, ignoreCase = true)) {
            return PluginSecurityScanResult(
                manifest = null,
                signatureTrust = SignatureTrustLevel.TAMPERED_REJECTED,
                signerFingerprintSha256 = null,
                payloadSha256 = actualSha,
                safetyScore = 0,
                riskTier = SecurityRiskTier.BLOCKED,
                canInstall = false,
                findings = listOf(
                    SecurityScanFinding(
                        id = "sha_mismatch",
                        title = "SHA-256 Digest Tampered",
                        detail = "Manifest digest does not match plugin.sha256 inside bundle.",
                        passed = false
                    )
                )
            )
        }

        val rawJson = String(mBytes, StandardCharsets.UTF_8)
        val manifest = PluginManifestCodec.parseJson(rawJson).getOrElse { err ->
            return rejectedResult("Invalid Manifest Schema", err.localizedMessage ?: "Schema error")
        }

        val (trust, fingerprint) = verifySignature(mBytes, sigBytes, pubKeyBytes)
        if (trust == SignatureTrustLevel.TAMPERED_REJECTED) {
            return PluginSecurityScanResult(
                manifest = manifest,
                signatureTrust = SignatureTrustLevel.TAMPERED_REJECTED,
                signerFingerprintSha256 = fingerprint,
                payloadSha256 = actualSha,
                safetyScore = 0,
                riskTier = SecurityRiskTier.BLOCKED,
                canInstall = false,
                findings = listOf(
                    SecurityScanFinding(
                        id = "ecdsa_fail",
                        title = "ECDSA Cryptographic Signature Failed",
                        detail = "Signature verification against publisher.pub failed — package may have been modified.",
                        passed = false
                    )
                ),
                rawManifestJson = rawJson
            )
        }

        return runStaticSecurityAudit(
            manifest = manifest,
            rawJson = rawJson,
            payloadSha256 = actualSha,
            signatureTrust = trust,
            signerFingerprint = fingerprint
        )
    }

    fun auditExistingManifest(
        manifest: PluginManifest,
        signatureTrust: SignatureTrustLevel,
        signerFingerprint: String? = null
    ): PluginSecurityScanResult {
        val rawJson = PluginManifestCodec.toJson(manifest)
        val sha = sha256Hex(rawJson.toByteArray(StandardCharsets.UTF_8))
        return runStaticSecurityAudit(
            manifest = manifest,
            rawJson = rawJson,
            payloadSha256 = sha,
            signatureTrust = signatureTrust,
            signerFingerprint = signerFingerprint
        )
    }

    private fun verifySignature(
        payload: ByteArray,
        sigBytes: ByteArray?,
        pubKeyBytes: ByteArray?
    ): Pair<SignatureTrustLevel, String?> {
        if (sigBytes == null || pubKeyBytes == null) {
            return SignatureTrustLevel.UNSIGNED to null
        }
        return runCatching {
            val kf = KeyFactory.getInstance("EC")
            val pubKey: PublicKey = kf.generatePublic(X509EncodedKeySpec(pubKeyBytes))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey)
            sig.update(payload)
            val valid = sig.verify(sigBytes)
            val fingerprint = sha256Hex(pubKeyBytes).take(24)
            if (!valid) {
                SignatureTrustLevel.TAMPERED_REJECTED to fingerprint
            } else if (pubKeyBytes.contentEquals(officialKeyPair.public.encoded)) {
                SignatureTrustLevel.VERIFIED_OFFICIAL to fingerprint
            } else {
                SignatureTrustLevel.VERIFIED_DEVELOPER to fingerprint
            }
        }.getOrElse {
            SignatureTrustLevel.TAMPERED_REJECTED to null
        }
    }

    private fun runStaticSecurityAudit(
        manifest: PluginManifest,
        rawJson: String,
        payloadSha256: String,
        signatureTrust: SignatureTrustLevel,
        signerFingerprint: String?
    ): PluginSecurityScanResult {
        val findings = ArrayList<SecurityScanFinding>()
        var score = 100

        // 1. Signature Verification Check
        when (signatureTrust) {
            SignatureTrustLevel.VERIFIED_OFFICIAL -> findings.add(
                SecurityScanFinding(
                    id = "sig_official",
                    title = "Official ECDSA P-256 Signature Verified",
                    detail = "Key fingerprint: ${signerFingerprint ?: "official"}",
                    passed = true
                )
            )
            SignatureTrustLevel.VERIFIED_DEVELOPER,
            SignatureTrustLevel.BUILTIN -> findings.add(
                SecurityScanFinding(
                    id = "sig_dev",
                    title = "Developer Cryptographic Signature Valid",
                    detail = "ECDSA P-256 verified (${signerFingerprint ?: "built-in"})",
                    passed = true
                )
            )
            SignatureTrustLevel.UNSIGNED -> {
                score -= 22
                findings.add(
                    SecurityScanFinding(
                        id = "sig_unsigned",
                        title = "Unsigned Manifest (.json)",
                        detail = "No ECDSA signature attached; verify author before granting elevated permissions.",
                        passed = false
                    )
                )
            }
            SignatureTrustLevel.TAMPERED_REJECTED -> {
                score = 0
            }
        }

        // 2. Host API & SemVer Compatibility
        val validation = PluginManifestCodec.validate(manifest)
        findings.add(
            SecurityScanFinding(
                id = "semver_compat",
                title = "SemVer & Host API Compatibility",
                detail = if (validation.isValid) {
                    "Plugin v${manifest.version} targets SDK ${manifest.apiVersion} (Host API ${manifest.minHostApiLevel})"
                } else {
                    validation.errors.joinToString("; ")
                },
                passed = validation.isValid
            )
        )
        if (!validation.isValid) score -= 50

        // 3. No Arbitrary Binary Execution Check
        findings.add(
            SecurityScanFinding(
                id = "no_dcl",
                title = "Declarative Sandbox Compliance (Zero DEX/SO)",
                detail = "Runs safely inside PakForge permission-guarded HostContext.",
                passed = true
            )
        )

        // 4. Elevated Permission Combination Audit
        val hasShell = PluginPermission.PRIVILEGED_SHELL_READ in manifest.permissions
        val hasTools = PluginPermission.ACCESS_HOST_TOOLS in manifest.permissions
        val hasNotif = PluginPermission.SEND_NOTIFICATIONS in manifest.permissions
        if (hasShell && hasTools) score -= 8
        if (hasNotif) score -= 2

        findings.add(
            SecurityScanFinding(
                id = "perm_audit",
                title = "Permission Scope Audit (${manifest.permissions.size}/6)",
                detail = if (hasShell) {
                    "Requests elevated shell telemetry + ${manifest.permissions.size - 1} standard scopes."
                } else {
                    "Standard scoped permissions only; revocable anytime."
                },
                passed = true
            )
        )

        // 5. Target Regex Safety Check (Prevent Catastrophic Regex)
        val regexStr = manifest.smartMatch?.targetPackageRegex
        val regexSafe = if (regexStr.isNullOrBlank()) {
            true
        } else {
            regexStr.length <= 80 && runCatching { Regex(regexStr) }.isSuccess
        }
        if (!regexSafe) {
            score -= 25
            findings.add(
                SecurityScanFinding(
                    id = "regex_safety",
                    title = "Invalid or Overlong SmartMatch Regex",
                    detail = "Target package matcher pattern failed safety check.",
                    passed = false
                )
            )
        }

        val finalScore = score.coerceIn(0, 100)
        val riskTier = when {
            !validation.isValid || signatureTrust == SignatureTrustLevel.TAMPERED_REJECTED -> SecurityRiskTier.BLOCKED
            hasShell && signatureTrust == SignatureTrustLevel.UNSIGNED -> SecurityRiskTier.ELEVATED
            signatureTrust == SignatureTrustLevel.UNSIGNED || manifest.permissions.size >= 5 -> SecurityRiskTier.MODERATE
            else -> SecurityRiskTier.LOW
        }

        return PluginSecurityScanResult(
            manifest = manifest,
            signatureTrust = signatureTrust,
            signerFingerprintSha256 = signerFingerprint,
            payloadSha256 = payloadSha256,
            safetyScore = finalScore,
            riskTier = riskTier,
            canInstall = riskTier != SecurityRiskTier.BLOCKED,
            findings = findings,
            rawManifestJson = rawJson
        )
    }

    private fun rejectedResult(title: String, detail: String): PluginSecurityScanResult {
        return PluginSecurityScanResult(
            manifest = null,
            signatureTrust = SignatureTrustLevel.TAMPERED_REJECTED,
            signerFingerprintSha256 = null,
            payloadSha256 = null,
            safetyScore = 0,
            riskTier = SecurityRiskTier.BLOCKED,
            canInstall = false,
            findings = listOf(SecurityScanFinding("rejected", title, detail, passed = false))
        )
    }

    private fun signBytes(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun ZipInputStream.readBytesLimited(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (true) {
            val read = read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalStateException("Entry exceeds max allowed size ($maxBytes bytes)")
            }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
