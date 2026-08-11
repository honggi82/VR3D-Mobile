package io.github.honggi82.vr3dmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewGridTest {
    @Test
    fun exactCenterSelectsOneView() {
        val selection = ViewGrid.interpolate(0f, 0f)

        assertEquals(listOf(WeightedView(GridKey(2, 3), 1f)), selection)
    }

    @Test
    fun clampsOutsideGrid() {
        val selection = ViewGrid.interpolate(99f, -99f)

        assertEquals(listOf(WeightedView(GridKey(4, 0), 1f)), selection)
    }
}
