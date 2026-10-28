package com.amgm.personallog.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MigrationsV6Test {
    @Test
    fun migrationsAreContiguous_upToCurrentVersion() {
        val pairs = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13, 13 to 14, 14 to 15), pairs)
    }

    @Test
    fun v4ToV5_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 4 && it.endVersion == 5 }
        assertSame(Migrations.MIGRATION_4_5, m)
    }

    @Test
    fun v5ToV6_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 5 && it.endVersion == 6 }
        assertSame(Migrations.MIGRATION_5_6, m)
    }

    @Test
    fun v7ToV8_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 7 && it.endVersion == 8 }
        assertSame(Migrations.MIGRATION_7_8, m)
    }

    @Test
    fun v6ToV7_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 6 && it.endVersion == 7 }
        assertSame(Migrations.MIGRATION_6_7, m)
    }

    @Test
    fun v8ToV9_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 8 && it.endVersion == 9 }
        assertSame(Migrations.MIGRATION_8_9, m)
    }

    @Test
    fun v9ToV10_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 9 && it.endVersion == 10 }
        assertSame(Migrations.MIGRATION_9_10, m)
    }
}
