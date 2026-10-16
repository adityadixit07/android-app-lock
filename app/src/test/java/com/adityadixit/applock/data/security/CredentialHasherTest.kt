package com.adityadixit.applock.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialHasherTest {

    // Low iteration count keeps the test fast; production uses DEFAULT_ITERATIONS.
    private val hasher = CredentialHasher(iterations = 1_000)

    @Test
    fun `correct secret verifies`() {
        val stored = hasher.hash("4821".toCharArray())
        assertTrue(hasher.verify("4821".toCharArray(), stored))
    }

    @Test
    fun `wrong secret is rejected`() {
        val stored = hasher.hash("4821".toCharArray())
        assertFalse(hasher.verify("4822".toCharArray(), stored))
        assertFalse(hasher.verify("48210".toCharArray(), stored))
    }

    @Test
    fun `same secret hashed twice uses different salts`() {
        val a = hasher.hash("1234".toCharArray())
        val b = hasher.hash("1234".toCharArray())
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.hash.contentEquals(b.hash))
    }

    @Test
    fun `encode and decode round trip`() {
        val stored = hasher.hash("0000".toCharArray())
        val decoded = HashedCredential.decode(stored.encode())
        assertNotNull(decoded)
        assertEquals(stored.iterations, decoded!!.iterations)
        assertTrue(hasher.verify("0000".toCharArray(), decoded))
    }

    @Test
    fun `decode rejects malformed or unknown records`() {
        assertNull(HashedCredential.decode(""))
        assertNull(HashedCredential.decode("md5\$1\$abc\$def"))
        assertNull(HashedCredential.decode("pbkdf2-sha256-v1\$notanumber\$AA==\$AA=="))
    }

    @Test
    fun `verification honours iterations stored in the record`() {
        val strong = CredentialHasher(iterations = 2_000).hash("9999".toCharArray())
        // A hasher configured differently must still verify using the record's own parameters.
        assertTrue(hasher.verify("9999".toCharArray(), strong))
    }
}
