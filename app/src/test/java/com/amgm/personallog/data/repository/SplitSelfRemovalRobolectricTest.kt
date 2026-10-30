package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.SplitParticipant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 割り勘の保存と読み直しを、実際の Room DB(Robolectric の SQLite)で確かめる:
 * 自分を外して保存しても、読み直すと自分は戻らない / 初めての割り勘は人数ぶんの空欄で始まる / 支払い側の人数を書き換えられる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SplitSelfRemovalRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var splits: SplitRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        splits = SplitRepository(db, db.splitDao(), db.paymentDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 支払い1件を作る(headcount は指定どおり)。 */
    private fun newPayment(headcount: Int?): Long = runBlocking {
        db.paymentDao().insert(
            Payment(
                id = 0,
                purchasedAt = "2026-10-31 20:00",
                storeName = "店",
                totalAmount = 900,
                imagePath = "",
                rawJson = "{}",
                createdAt = 0,
                headcount = headcount,
            ),
        )
    }

    @Test
    fun 自分を外して保存すると再読込でも自分は戻らない() {
        runBlocking {
            val rid = newPayment(headcount = null)
            val first = splits.load(rid)
            assertEquals(1, first.participants.size) // headcount が null なら自分だけ

            // 自分の参加者を外し、名前なしの人だけにして保存する
            val blank = SplitParticipant(id = -1L, paymentId = rid, personId = null, sortOrder = 1)
            splits.save(first.copy(participants = listOf(blank), persons = emptyMap()), names = emptyMap())

            val reloaded = splits.load(rid)
            assertEquals(1, reloaded.participants.size)
            assertFalse(reloaded.participants.any { reloaded.persons[it.id]?.isSelf == true })
            assertTrue(reloaded.persons.values.none { it.isSelf })
            // 家計簿の計算でも、自分の参加者が無いまま割り勘として扱われる(他の人だけなので null にならない)
            assertNotNull(splits.buildFor(rid, total = 900, items = emptyList()))
        }
    }

    @Test
    fun 初めての割り勘は人数ぶんの空欄で始まる() {
        runBlocking {
            val rid = newPayment(headcount = 3)
            val d = splits.load(rid)
            assertEquals(3, d.participants.size)
            assertEquals(1, d.participants.count { d.persons[it.id]?.isSelf == true })
            assertEquals(2, d.participants.count { it.personId == null })
        }
    }

    @Test
    fun 支払い側の人数を書き換えられる() {
        runBlocking {
            val rid = newPayment(headcount = null)
            splits.setHeadcount(rid, 4)
            assertEquals(4, db.paymentDao().getById(rid)?.headcount)
            splits.setHeadcount(rid, null)
            assertNull(db.paymentDao().getById(rid)?.headcount)
        }
    }
}
