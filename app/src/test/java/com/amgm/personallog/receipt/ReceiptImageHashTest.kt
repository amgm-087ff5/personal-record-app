package com.amgm.personallog.receipt

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class ReceiptImageHashTest {
    private val emptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val abcDigest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun emptyInput_matchesKnownDigest() {
        assertEquals(emptyDigest, ReceiptImageHash.sha256Hex(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun abc_matchesKnownDigest() {
        assertEquals(abcDigest, ReceiptImageHash.sha256Hex(ByteArrayInputStream("abc".toByteArray())))
    }

    @Test
    fun largeInput_streamedMatchesOneShotDigest() {
        // バッファ(64KB)を複数回またぐサイズ
        val data = ByteArray(300_001) { (it * 31 % 251).toByte() }
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        assertEquals(expected, ReceiptImageHash.sha256Hex(ByteArrayInputStream(data)))
    }

    @Test
    fun file_matchesStreamDigest() {
        val f = File.createTempFile("receipt-hash", ".jpg")
        try {
            f.writeBytes("abc".toByteArray())
            assertEquals(abcDigest, ReceiptImageHash.sha256Hex(f))
        } finally {
            f.delete()
        }
    }
}
