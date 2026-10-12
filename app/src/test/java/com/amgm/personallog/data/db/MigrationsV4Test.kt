package com.amgm.personallog.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationsV4Test {
    @Test
    fun v4IsCurrentVersion_andMigrationsAreContiguous() {
        val pairs = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4), pairs)
    }
}
