package com.phoneagent.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCryptoTest {
    @Test
    fun hkdfMatchesRfc5869CaseOne() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val actual = DesktopCrypto.hkdfSha256(ikm, salt, info)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf", actual.toHex())
    }

    @Test
    fun hmacIsDeterministicAndAuthenticated() {
        val first = DesktopCrypto.hmac("key".encodeToByteArray(), "phoneagent-phone".encodeToByteArray())
        val second = DesktopCrypto.hmac("key".encodeToByteArray(), "phoneagent-phone".encodeToByteArray())
        assertArrayEquals(first, second)
        assertEquals(32, first.size)
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
