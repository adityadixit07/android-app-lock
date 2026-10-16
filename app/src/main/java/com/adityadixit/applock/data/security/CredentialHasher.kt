package com.adityadixit.applock.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A salted PBKDF2 hash plus the parameters needed to verify (and later upgrade) it. */
class HashedCredential(
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
) {
    fun encode(): String = listOf(
        VERSION,
        iterations.toString(),
        Base64.getEncoder().encodeToString(salt),
        Base64.getEncoder().encodeToString(hash),
    ).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "pbkdf2-sha256-v1"
        private const val SEPARATOR = "$"

        fun decode(encoded: String): HashedCredential? {
            val parts = encoded.split(SEPARATOR)
            if (parts.size != 4 || parts[0] != VERSION) return null
            return runCatching {
                HashedCredential(
                    salt = Base64.getDecoder().decode(parts[2]),
                    hash = Base64.getDecoder().decode(parts[3]),
                    iterations = parts[1].toInt(),
                )
            }.getOrNull()
        }
    }
}

/**
 * PBKDF2-HMAC-SHA256 (platform built-in since API 26, no native dependency).
 *
 * Iteration count is a deliberate trade-off: the resulting record is additionally encrypted
 * with a hardware-backed Android Keystore key ([KeystoreCipher]), so an attacker with a raw
 * file dump cannot even start an offline attack. The KDF is the second line of defence and
 * must stay fast enough (~150–300 ms on low-end devices) for an unlock screen.
 */
class CredentialHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    private val random = SecureRandom()

    fun hash(secret: CharArray): HashedCredential {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return HashedCredential(salt, derive(secret, salt, iterations), iterations)
    }

    /** Constant-time comparison so timing doesn't leak how many bytes matched. */
    fun verify(secret: CharArray, stored: HashedCredential): Boolean =
        MessageDigest.isEqual(derive(secret, stored.salt, stored.iterations), stored.hash)

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val KEY_LENGTH_BITS = 256
    }
}
