package com.amgm.personallog.data.db

import org.junit.Assert.assertSame
import org.junit.Test

class MigrationsTest {
    @Test
    fun v1ToV2_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 1 && it.endVersion == 2 }
        assertSame(Migrations.MIGRATION_1_2, m)
    }
}
