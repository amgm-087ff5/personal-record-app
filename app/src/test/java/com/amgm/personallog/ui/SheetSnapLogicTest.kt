package com.amgm.personallog.ui

import com.amgm.personallog.ui.design.SheetSnap
import com.amgm.personallog.ui.design.SheetSnapLogic
import org.junit.Assert.assertEquals
import org.junit.Test

/** シートの止まる位置の判定(InkSheet の SheetSnapLogic.settle)。 */
class SheetSnapLogicTest {
    private val sheetHeight = 1000f
    private val peekHeight = 100f

    private fun settle(
        from: SheetSnap,
        offset: Float,
        velocity: Float,
        minimizable: Boolean,
    ): SheetSnap = SheetSnapLogic.settle(from, offset, velocity, sheetHeight, peekHeight, minimizable)

    @Test
    fun minimizable_expanded_smallDownwardDrag_staysExpanded() {
        // 最小化までの距離 900px の 35% (315px) に届かない小さな下スワイプ
        assertEquals(SheetSnap.Expanded, settle(SheetSnap.Expanded, offset = 100f, velocity = 0f, minimizable = true))
    }

    @Test
    fun minimizable_expanded_fastDownwardFling_goesToMinimized_notHidden() {
        assertEquals(SheetSnap.Minimized, settle(SheetSnap.Expanded, offset = 50f, velocity = 2000f, minimizable = true))
    }

    @Test
    fun minimizable_minimized_fastDownwardFling_goesToHidden() {
        assertEquals(SheetSnap.Hidden, settle(SheetSnap.Minimized, offset = 900f, velocity = 2000f, minimizable = true))
    }

    @Test
    fun notMinimizable_expanded_flingDown_goesToHidden() {
        assertEquals(SheetSnap.Hidden, settle(SheetSnap.Expanded, offset = 50f, velocity = 2000f, minimizable = false))
    }

    @Test
    fun minimized_fastUpwardFling_goesToExpanded() {
        assertEquals(SheetSnap.Expanded, settle(SheetSnap.Minimized, offset = 900f, velocity = -2000f, minimizable = true))
    }
}
