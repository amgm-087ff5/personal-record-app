package com.amgm.personallog.backup

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.TotalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    @Test
    fun json_roundTrip() {
        val data = BackupData(
            formatVersion = BACKUP_FORMAT_VERSION,
            dbVersion = 1,
            exportedAt = 123L,
            diaryEntries = listOf(BackupDiaryEntry(1, "2026-10-01", "今日は\"晴れ\"\n改行あり", 10, 20)),
            diaryPhotos = listOf(BackupDiaryPhoto(1, 1, "diary/a.jpg", null, 0)),
            receipts = listOf(BackupReceipt(5, "2026-10-02 12:30", "店", 980, "receipts/r.jpg", "{\"a\":1}", "confirmed", 30)),
            receiptItems = listOf(BackupReceiptItem(id = 1, receiptId = 5, name = "牛乳", memo = "2本目", amount = 150, category = "食費", ownAmount = 100)),
            expenses = listOf(
                BackupExpense(1, "2026-10-02", "12:30", 980, "食費", "店", 5, 40),
                BackupExpense(2, "2026-10-03", "", 500, "その他", "", null, 41),
            ),
        )
        val text = BackupJson.encodeToString(BackupData.serializer(), data)
        val back = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
        assertTrue(text.contains("\"formatVersion\""))
        assertTrue(text.contains("\"dbVersion\""))
    }

    @Test
    fun json_ignoresUnknownKeysAndUsesDefaults() {
        val text = """{"formatVersion":1,"future":"x","expenses":[{"id":1,"date":"2026-10-01","amount":1,"category":"食費","createdAt":0,"extra":true}]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(1, d.expenses.size)
        assertEquals("", d.expenses[0].memo)
        assertNull(d.expenses[0].receiptId)
    }

    @Test
    fun entityConversion_isLossless() {
        val e = BackupExpense(3, "2026-01-01", "09:00", 10, "食費", "m", 7, 99, uid = "u-1")
        assertEquals(e, BackupExpense.from(e.toEntity()))
        val r = BackupReceipt(1, "2026-01-01", "s", 1, "receipts/x.jpg", "{}", "draft", 2)
        assertEquals(r, BackupReceipt.from(r.toEntity()))
    }

    @Test
    fun receiptWithoutTime_keepsExpenseTimeEmpty_onRestore() {
        // レシートに時刻が無い支出は、復元しても 00:00 や作成時刻で補完しない
        val e = BackupExpense(4, "2026-01-01", "", 10, "食費", "", 7, 99)
        assertEquals("", e.toEntity(linkedTime = "").time)
        // 古いバックアップ(時刻なし・レシート紐づきなし)は作成時刻から補う
        val legacy = BackupExpense(5, "2026-01-01", "", 10, "食費", "", null, 0)
        assertTrue(legacy.toEntity().time.matches(Regex("""\d{2}:\d{2}""")))
    }

    @Test
    fun legacyItem_isExpandedOnImport() {
        // v3までの形式: 数量2・負担割合50%
        val legacy = BackupReceiptItem(id = 1, receiptId = 9, name = "洗剤", amount = 200, category = "日用品", quantity = 2.0, unitPrice = 100, ownPercent = 50)
        val rows = legacy.toUnitRows()
        assertEquals(2, rows.size)
        assertEquals(listOf(100, 100), rows.map { it.amount })
        assertEquals(listOf(50, 50), rows.map { it.ownAmount })
    }

    @Test
    fun safeRelativePath_acceptsNormal() {
        assertEquals("diary/a.jpg", BackupPaths.safeRelativePath("diary/a.jpg"))
        assertEquals("receipts/2026/10/x.png", BackupPaths.safeRelativePath("receipts/2026/10/x.png"))
        assertEquals("diary/a.jpg", BackupPaths.entryNameToRelative("files/diary/a.jpg"))
    }

    @Test
    fun zipSlip_isRejected() {
        val bad = listOf(
            "files/../evil.txt",
            "files/diary/../../evil.txt",
            "files//etc/passwd",
            "files/diary/../receipts/../../x",
            "/etc/passwd",
            "files/diary\\..\\x",
            "files/C:/x.jpg",
            "files/other/a.jpg",
            "files/a.jpg",
            "files/diary/./a.jpg",
            "files/",
            "",
        )
        for (name in bad) {
            assertNull("should reject: $name", BackupPaths.entryNameToRelative(name))
        }
        assertNull(BackupPaths.entryNameToRelative("data.json"))
        assertNull(BackupPaths.safeRelativePath("diary/\u0000x"))
    }

    @Test
    fun storedPath_validation() {
        assertTrue(BackupPaths.isSafeStoredPath(""))
        assertTrue(BackupPaths.isSafeStoredPath("diary/a.jpg"))
        assertFalse(BackupPaths.isSafeStoredPath("../a.jpg"))
        assertFalse(BackupPaths.isSafeStoredPath("diary/../../a.jpg"))
        assertFalse(BackupPaths.isSafeStoredPath("/data/x.jpg"))
    }

    @Test
    fun checkVersion_rejectsNewer() {
        BackupManager.checkVersion(BACKUP_FORMAT_VERSION)
        try {
            BackupManager.checkVersion(BACKUP_FORMAT_VERSION + 1)
            fail("expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("新しい"))
        }
    }

    @Test
    fun formatVersionIsNine() {
        assertEquals(9, BACKUP_FORMAT_VERSION)
    }

    @Test
    fun format5Fields_roundTrip_keepsNullTotalSourceCropAndNotDuplicates() {
        val data = BackupData(
            formatVersion = BACKUP_FORMAT_VERSION,
            dbVersion = 9,
            exportedAt = 1L,
            receipts = listOf(
                BackupReceipt(
                    id = 5, purchasedAt = "2026-10-02 12:30", storeName = "店", totalAmount = null,
                    imagePath = "receipts/r.jpg", rawJson = "{}", status = "draft", createdAt = 30,
                    totalSource = TotalSource.UNKNOWN, imageHash = "abc123",
                    rotationDegrees = 91.5f, cropLeft = 0.1f, cropTop = 0.2f, cropRight = 0.9f, cropBottom = 0.8f,
                ),
                BackupReceipt(
                    id = 6, purchasedAt = "2026-10-02 12:31", storeName = "店", totalAmount = 980,
                    imagePath = "receipts/s.jpg", rawJson = "{}", status = "confirmed", createdAt = 31,
                    totalSource = TotalSource.ITEMS,
                ),
            ),
            receiptNotDuplicates = listOf(BackupNotDuplicate(5, 6, 77)),
        )
        val text = BackupJson.encodeToString(BackupData.serializer(), data)
        val back = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
        assertNull(back.receipts[0].totalAmount)
        assertEquals(TotalSource.ITEMS, back.receipts[1].totalSource)
        assertEquals(listOf(BackupNotDuplicate(5, 6, 77)), back.receiptNotDuplicates)
        // エンティティとの往復で失われない
        for (r in back.receipts) assertEquals(r, BackupReceipt.from(r.toEntity()))
        val e = back.receipts[0].toEntity()
        assertNull(e.totalAmount)
        assertEquals("abc123", e.imageHash)
        assertEquals(91.5f, e.rotationDegrees, 0f)
        assertEquals(0.9f, e.cropRight, 0f)
    }

    @Test
    fun format4_isStillReadable_withDefaultsForNewFields() {
        val text = """{"formatVersion":4,"dbVersion":7,"exportedAt":1,
            "receipts":[{"id":1,"purchasedAt":"2026-10-01 10:00","storeName":"店","totalAmount":500,"imagePath":"receipts/a.jpg","rawJson":"{}","status":"confirmed","createdAt":5,"isAdvance":true,"ownAmount":200}],
            "expenseGroups":[]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        BackupManager.checkVersion(d.formatVersion)
        val r = d.receipts.single()
        assertEquals(500, r.totalAmount)
        assertEquals(TotalSource.UNKNOWN, r.totalSource)
        assertNull(r.imageHash)
        assertEquals(0f, r.rotationDegrees, 0f)
        assertEquals(0f, r.cropLeft, 0f)
        assertEquals(0f, r.cropTop, 0f)
        assertEquals(1f, r.cropRight, 0f)
        assertEquals(1f, r.cropBottom, 0f)
        assertTrue(d.receiptNotDuplicates.isEmpty())
        val e = r.toEntity()
        assertEquals(500, e.totalAmount)
        assertEquals("confirmed", e.status)
        assertEquals(200, e.ownAmount)
        assertEquals(TotalSource.UNKNOWN, e.totalSource)
    }

    @Test
    fun confirmedReceiptWithoutTotal_isRestoredAsDraft() {
        val r = BackupReceipt(1, "2026-01-01", "s", null, "receipts/x.jpg", "{}", "confirmed", 2)
        assertEquals("draft", r.toEntity().status)
        val ok = BackupReceipt(1, "2026-01-01", "s", 100, "receipts/x.jpg", "{}", "confirmed", 2)
        assertEquals("confirmed", ok.toEntity().status)
    }

    @Test
    fun remapNotDuplicates_followsIdMapAndDropsUnmapped() {
        val pairs = listOf(
            BackupNotDuplicate(1, 2, 10),
            BackupNotDuplicate(1, 3, 11), // 3 は対応なし -> 捨てる
            BackupNotDuplicate(2, 4, 12), // (20, 10) -> 正規化すると (10, 20) で、1つ目と同じ組 -> 1つにまとめる
            BackupNotDuplicate(1, 4, 13), // 1 と 4 が同じレシート(10)に対応する -> 組にならないので捨てる
        )
        val map = mapOf(1L to 10L, 2L to 20L, 4L to 10L)
        val r = remapNotDuplicates(pairs, map)
        assertEquals(listOf(10L to 20L), r.map { it.receiptIdLow to it.receiptIdHigh }.distinct())
        assertEquals(1, r.size)
        // 付け替えで大小が逆転した組は Low < High に直る
        val flipped = remapNotDuplicates(listOf(BackupNotDuplicate(1, 2, 0)), mapOf(1L to 9L, 2L to 4L))
        assertEquals(4L to 9L, flipped.single().let { it.receiptIdLow to it.receiptIdHigh })
        assertTrue(remapNotDuplicates(pairs, emptyMap()).isEmpty())
    }

    private fun entity(
        id: Long,
        at: String = "2026-10-01 10:00",
        total: Int? = 500,
        path: String = "receipts/$id.jpg",
        hash: String? = null,
    ) = Receipt(
        id = id, purchasedAt = at, storeName = "店", totalAmount = total, imagePath = path,
        rawJson = "{}", createdAt = id, imageHash = hash,
    )

    @Test
    fun mergeIndex_usesImageHashWhenPresent() {
        val idx = ReceiptMergeIndex(listOf(entity(1, hash = "h1"), entity(2, hash = "h2")))
        // 日時や画像パスが違っても、ハッシュが同じなら同じレシート
        assertEquals(2L, idx.take(entity(90, at = "2030-01-01 00:00", total = 1, path = "other.jpg", hash = "h2")))
        // 日時・合計・パスが同じでも、ハッシュが違えば別のレシート
        assertNull(idx.take(entity(91, path = "receipts/1.jpg", hash = "zzz")))
    }

    @Test
    fun mergeIndex_fallsBackToLegacyKey_andHandlesNullTotal() {
        val idx = ReceiptMergeIndex(listOf(entity(1, total = null), entity(2, total = 700)))
        assertEquals(1L, idx.take(entity(50, total = null, path = "receipts/1.jpg")))
        assertNull(idx.take(entity(51, total = 701, path = "receipts/2.jpg")))
        assertEquals(2L, idx.take(entity(52, total = 700, path = "receipts/2.jpg")))
        // 既存のハッシュの有無に関係なく、バックアップにハッシュが無ければ従来のキー
        val idx2 = ReceiptMergeIndex(listOf(entity(3, hash = "h3")))
        assertEquals(3L, idx2.take(entity(60, path = "receipts/3.jpg")))
    }

    @Test
    fun mergeIndex_matchesOneToOne() {
        val idx = ReceiptMergeIndex(listOf(entity(1, hash = "same")))
        assertEquals(1L, idx.take(entity(10, hash = "same")))
        // 同じ画像を2枚持つバックアップ: 2枚目は既存に対応が無いので新規
        assertNull(idx.take(entity(11, hash = "same")))
    }

    @Test
    fun mergeIndex_hashedBackupFindsExistingWithoutHashByLegacyKey() {
        val idx = ReceiptMergeIndex(listOf(entity(1, hash = null), entity(2, hash = "other", path = "receipts/1.jpg")))
        // 既存にハッシュが無ければ従来のキーで見つかる
        assertEquals(1L, idx.take(entity(10, hash = "newhash", path = "receipts/1.jpg")))
        // 既存に別のハッシュがあるものは別の画像
        assertNull(idx.take(entity(11, hash = "newhash", path = "receipts/1.jpg")))
    }

    @Test
    fun legacyFormat_decidesTotalAndSourceLikeTheMigration() {
        fun r(total: Int, status: String, raw: String) = BackupReceipt(
            id = 1, purchasedAt = "2026-10-09 18:32", storeName = "A", totalAmount = total, imagePath = "p",
            rawJson = raw, status = status, createdAt = 1,
        )
        val noTotal = """{"date":"2026-10-09","store":"A","items":[{"name":"a","amount":198}]}"""
        // 読み取り失敗の仮の0円(下書き)は合計に戻さない
        val failed = r(0, "draft", "").toEntity(legacyFormat = true)
        assertNull(failed.totalAmount)
        assertEquals(TotalSource.UNKNOWN, failed.totalSource)
        // 手で入れた値は残り、出所は manual
        val manual = r(500, "draft", noTotal).toEntity(legacyFormat = true)
        assertEquals(500, manual.totalAmount)
        assertEquals(TotalSource.MANUAL, manual.totalSource)
        // 形式5以降はそのまま
        assertEquals(0, r(0, "draft", "").toEntity().totalAmount)
    }
}
