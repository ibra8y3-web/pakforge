package com.example.core.signing

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class ApkV2Signer(private val context: Context) {

    data class SigningIdentity(
        val privateKey: PrivateKey,
        val publicKeyEncoded: ByteArray,
        val certDer: ByteArray
    )

    private val keyDir: File
        get() = File(context.filesDir, "pakforge_signing").apply { mkdirs() }

    fun getOrCreateIdentity(): SigningIdentity {
        val privFile = File(keyDir, "pakforge_rsa2048.pk8")
        val pubFile = File(keyDir, "pakforge_rsa2048.pub")
        val certFile = File(keyDir, "pakforge_rsa2048.der")

        if (privFile.exists() && pubFile.exists() && certFile.exists()) {
            val loaded = runCatching {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                val pubBytes = pubFile.readBytes()
                val certBytes = certFile.readBytes()
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certBytes))
                SigningIdentity(priv, pubBytes, certBytes)
            }.getOrNull()
            if (loaded != null) return loaded
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()
        val certDer = buildSelfSignedX509Certificate(keyPair)

        privFile.writeBytes(keyPair.private.encoded)
        pubFile.writeBytes(keyPair.public.encoded)
        certFile.writeBytes(certDer)

        return SigningIdentity(keyPair.private, keyPair.public.encoded, certDer)
    }

    fun getLocalCertSha256(): String {
        val identity = getOrCreateIdentity()
        val certDigest = MessageDigest.getInstance("SHA-256").digest(identity.certDer)
        return certDigest.joinToString(":") { "%02X".format(it) }
    }

    fun signApkV2(inputApk: File, outputApk: File): String {
        val identity = getOrCreateIdentity()
        val apkBytes = inputApk.readBytes()
        val eocdOffset = findEocdOffset(apkBytes)
        require(eocdOffset >= 0) { "Invalid ZIP/APK: EOCD not found" }

        val eocdBuf = ByteBuffer.wrap(apkBytes, eocdOffset, apkBytes.size - eocdOffset)
            .slice()
            .order(ByteOrder.LITTLE_ENDIAN)
        val cdOffset = eocdBuf.getInt(16).toLong() and 0xFFFFFFFFL
        require(cdOffset in 0L..eocdOffset.toLong()) { "Invalid ZIP/APK central directory offset" }

        // Compute APK Signature Scheme v2 CHUNKED_SHA256 digest (0x0103)
        val contentDigest = computeV2ChunkedSha256(apkBytes, cdOffset.toInt(), eocdOffset)

        // Build SignedData
        val digestEntry = ByteArrayOutputStream().apply {
            writeIntLE(0x0103) // SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA256
            writeLengthPrefixed(contentDigest)
        }.toByteArray()

        val digestsSeq = lengthPrefixed(digestEntry)
        val certsSeq = lengthPrefixed(identity.certDer)
        val additionalAttrs = ByteArray(0)

        val signedData = ByteArrayOutputStream().apply {
            writeLengthPrefixed(digestsSeq)
            writeLengthPrefixed(certsSeq)
            writeLengthPrefixed(additionalAttrs)
        }.toByteArray()

        val signatureBytes = Signature.getInstance("SHA256withRSA").apply {
            initSign(identity.privateKey)
            update(signedData)
        }.sign()

        val signatureEntry = ByteArrayOutputStream().apply {
            writeIntLE(0x0103)
            writeLengthPrefixed(signatureBytes)
        }.toByteArray()

        val signerBlock = ByteArrayOutputStream().apply {
            writeLengthPrefixed(signedData)
            writeLengthPrefixed(lengthPrefixed(signatureEntry))
            writeLengthPrefixed(identity.publicKeyEncoded)
        }.toByteArray()

        val v2SchemeValue = lengthPrefixed(lengthPrefixed(signerBlock))

        // Build APK Signing Block
        val pairLen = 4L + v2SchemeValue.size.toLong()
        val blockSizeField = (8L + pairLen) + 8L + 16L
        val signingBlock = ByteArrayOutputStream().apply {
            writeLongLE(blockSizeField)
            writeLongLE(pairLen)
            writeIntLE(0x7109871a) // APK_SIGNATURE_SCHEME_V2_BLOCK_ID
            write(v2SchemeValue)
            writeLongLE(blockSizeField)
            write("APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII))
        }.toByteArray()

        val newCdOffset = cdOffset + signingBlock.size.toLong()
        val patchedEocd = apkBytes.copyOfRange(eocdOffset, apkBytes.size)
        ByteBuffer.wrap(patchedEocd).order(ByteOrder.LITTLE_ENDIAN).putInt(16, newCdOffset.toInt())

        FileOutputStream(outputApk).use { out ->
            out.write(apkBytes, 0, cdOffset.toInt())
            out.write(signingBlock)
            out.write(apkBytes, cdOffset.toInt(), eocdOffset - cdOffset.toInt())
            out.write(patchedEocd)
        }

        val certDigest = MessageDigest.getInstance("SHA-256").digest(identity.certDer)
        return certDigest.joinToString(":") { "%02X".format(it) }
    }

    private fun computeV2ChunkedSha256(apkBytes: ByteArray, cdOffset: Int, eocdOffset: Int): ByteArray {
        val chunkDigests = ArrayList<ByteArray>()
        val chunkSize = 1024 * 1024 // 1 MB

        fun digestRange(start: Int, end: Int) {
            var pos = start
            while (pos < end) {
                val len = minOf(chunkSize, end - pos)
                val md = MessageDigest.getInstance("SHA-256")
                md.update(0xA5.toByte())
                md.update(intToBytesLE(len))
                md.update(apkBytes, pos, len)
                chunkDigests.add(md.digest())
                pos += len
            }
        }

        digestRange(0, cdOffset)
        digestRange(cdOffset, eocdOffset)
        digestRange(eocdOffset, apkBytes.size)

        val rootMd = MessageDigest.getInstance("SHA-256")
        rootMd.update(0x5A.toByte())
        rootMd.update(intToBytesLE(chunkDigests.size))
        for (digest in chunkDigests) {
            rootMd.update(digest)
        }
        return rootMd.digest()
    }

    private fun findEocdOffset(bytes: ByteArray): Int {
        val minOffset = (bytes.size - 65558).coerceAtLeast(0)
        for (i in (bytes.size - 22) downTo minOffset) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x05.toByte() &&
                bytes[i + 3] == 0x06.toByte()
            ) {
                val commentLen = ((bytes[i + 20].toInt() and 0xFF) or
                    ((bytes[i + 21].toInt() and 0xFF) shl 8))
                if (i + 22 + commentLen == bytes.size) {
                    return i
                }
            }
        }
        return -1
    }

    // Pure ASN.1 DER X.509 v3 self-signed certificate builder (no internal JDK APIs needed)
    private fun buildSelfSignedX509Certificate(keyPair: KeyPair): ByteArray {
        val sha256RsaOid = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B))
        val sigAlgId = derSeq(sha256RsaOid + byteArrayOf(0x05, 0x00)) // OID + NULL

        val cnOid = derOid(byteArrayOf(0x55, 0x04, 0x03))
        val cnVal = derTag(0x0C, "PakForge Local Signer".toByteArray(StandardCharsets.UTF_8))
        val name = derSeq(derTag(0x31, derSeq(cnOid + cnVal)))

        val serialBytes = ByteArray(8).apply {
            SecureRandom().nextBytes(this)
            this[0] = (this[0].toInt() and 0x7F).toByte()
            if (this[0] == 0.toByte()) this[0] = 0x01
        }

        val version = derTag(0xA0, derTag(0x02, byteArrayOf(0x02))) // v3
        val serial = derTag(0x02, serialBytes)
        val validity = derSeq(
            derTag(0x17, "250101000000Z".toByteArray(StandardCharsets.US_ASCII)) +
                derTag(0x17, "491231235959Z".toByteArray(StandardCharsets.US_ASCII))
        )
        val spki = keyPair.public.encoded

        val tbsCertificate = derSeq(version + serial + sigAlgId + name + validity + name + spki)

        val sigBytes = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(tbsCertificate)
        }.sign()

        val bitString = derTag(0x03, byteArrayOf(0x00) + sigBytes)
        return derSeq(tbsCertificate + sigAlgId + bitString)
    }

    private fun derOid(oidContent: ByteArray): ByteArray = derTag(0x06, oidContent)

    private fun derSeq(content: ByteArray): ByteArray = derTag(0x30, content)

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val len = content.size
        val lenBytes = when {
            len < 128 -> byteArrayOf(len.toByte())
            len < 256 -> byteArrayOf(0x81.toByte(), len.toByte())
            else -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
        }
        return byteArrayOf(tag.toByte()) + lenBytes + content
    }

    private fun lengthPrefixed(data: ByteArray): ByteArray {
        return intToBytesLE(data.size) + data
    }

    private fun ByteArrayOutputStream.writeLengthPrefixed(data: ByteArray) {
        write(intToBytesLE(data.size))
        write(data)
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(intToBytesLE(value))
    }

    private fun ByteArrayOutputStream.writeLongLE(value: Long) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
        write(buf.array())
    }

    private fun intToBytesLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte()
        )
    }
}
