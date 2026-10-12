package com.amgm.personallog.data.db

import org.junit.Assert.assertSame
import org.junit.Test

class MigrationsTest {
    @Test
    fun v1ToV2_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 1 && it.endVersion == 2 }
        assertSame(Migrations.MIGRATION_1_2, m)
    }

    @Test
    fun v2ToV3_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 2 && it.endVersion == 3 }
        assertSame(Migrations.MIGRATION_2_3, m)
    }

    @Test
    fun v3ToV4_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 3 && it.endVersion == 4 }
        assertSame(Migrations.MIGRATION_3_4, m)
    }
}
