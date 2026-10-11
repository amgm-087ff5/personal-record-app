package com.amgm.personallog.receipt

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** レシート原本画像の SHA-256(16進小文字)。ストリームで読むため、大きな画像でもメモリに全部載せない。 */
object ReceiptImageHash {
    private const val BUFFER_SIZE = 64 * 1024

    /** 入力を最後まで読んでハッシュを返す。閉じるのは呼び出し側。 */
    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }
}
