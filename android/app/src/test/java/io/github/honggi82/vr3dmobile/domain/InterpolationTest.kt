package io.github.honggi82.vr3dmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InterpolationTest {
    @Test
    fun midpointUsesBilinearQuarterWeights() {
        val selection = ViewGrid.interpolate(pitch = 2f, roll = 2f)

        assertEquals(4, selection.size)
        selection.forEach { assertEquals(0.25f, it.weight, 0.0001f) }
        assertEquals(1f, selection.sumOf { it.weight.toDouble() }.toFloat(), 0.0001f)
    }
}
