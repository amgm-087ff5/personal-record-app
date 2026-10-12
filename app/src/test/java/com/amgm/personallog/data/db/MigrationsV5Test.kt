package com.amgm.personallog.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MigrationsV5Test {
    @Test
    fun v5IsCurrentVersion_andMigrationsAreContiguous() {
        val pairs = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5), pairs)
    }

    @Test
    fun v4ToV5_isRegistered() {
        val m = Migrations.ALL.single { it.startVersion == 4 && it.endVersion == 5 }
        assertSame(Migrations.MIGRATION_4_5, m)
    }
}
