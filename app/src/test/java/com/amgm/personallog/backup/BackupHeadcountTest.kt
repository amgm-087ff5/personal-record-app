package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHeadcountTest {
    @Test
    fun headcount_roundTrips_throughJsonAndEntity() {
        val r = BackupReceipt(
            id = 1, purchasedAt = "2026-10-09 18:32", storeName = "居酒屋A", totalAmount = 5000,
            imagePath = "receipts/a.jpg", rawJson = "{}", status = ReceiptStatus.CONFIRMED, createdAt = 2,
            headcount = 3,
        )
        val text = BackupJson.encodeToString(BackupReceipt.serializer(), r)
        assertTrue(text.contains("\"headcount\":3"))
        val back = BackupJson.decodeFromString(BackupReceipt.serializer(), text)
        assertEquals(3, back.headcount)
        assertEquals(3, back.toEntity().headcount)
        assertEquals(r, BackupReceipt.from(r.toEntity()))
    }

    @Test
    fun headcount_missingInOldBackup_isNull() {
        // 形式8以前のレシート(headcount が無い)は null として読む
        val text = """{"formatVersion":8,"dbVersion":12,"exportedAt":1,
            "receipts":[{"id":1,"purchasedAt":"2026-10-09","storeName":"s","totalAmount":100,
            "imagePath":"","rawJson":"{}","status":"draft","createdAt":1}]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertNull(d.receipts.single().headcount)
        assertNull(d.receipts.single().toEntity().headcount)
    }
}
