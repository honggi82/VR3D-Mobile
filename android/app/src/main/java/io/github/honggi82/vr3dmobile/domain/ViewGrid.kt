package io.github.honggi82.vr3dmobile.domain

import kotlin.math.max
import kotlin.math.min

data class GridKey(val row: Int, val column: Int)
data class WeightedView(val key: GridKey, val weight: Float)

object ViewGrid {
    val rollAngles = Vr3dManifest.ROLL_ANGLES.map(Int::toFloat)
    val pitchAngles = Vr3dManifest.PITCH_ANGLES.map(Int::toFloat)

    fun interpolate(pitch: Float, roll: Float): List<WeightedView> {
        val vertical = interval(pitch, pitchAngles)
        val horizontal = interval(roll, rollAngles)
        val weights = linkedMapOf<GridKey, Float>()

        fun add(row: Int, column: Int, weight: Float) {
            val key = GridKey(row, column)
            weights[key] = (weights[key] ?: 0f) + weight
        }

        add(vertical.lower, horizontal.lower, (1f - vertical.fraction) * (1f - horizontal.fraction))
        add(vertical.lower, horizontal.upper, (1f - vertical.fraction) * horizontal.fraction)
        add(vertical.upper, horizontal.lower, vertical.fraction * (1f - horizontal.fraction))
        add(vertical.upper, horizontal.upper, vertical.fraction * horizontal.fraction)
        return weights.filterValues { it > 0f }.map { WeightedView(it.key, it.value) }
    }

    fun clampPitch(value: Float): Float = value.coerceIn(pitchAngles.first(), pitchAngles.last())
    fun clampRoll(value: Float): Float = value.coerceIn(rollAngles.first(), rollAngles.last())

    private fun interval(value: Float, angles: List<Float>): Interval {
        val clamped = value.coerceIn(angles.first(), angles.last())
        if (clamped == angles.last()) return Interval(angles.lastIndex, angles.lastIndex, 0f)
        val lower = max(0, min(angles.lastIndex - 1, angles.indexOfLast { it <= clamped }))
        val upper = lower + 1
        val fraction = (clamped - angles[lower]) / (angles[upper] - angles[lower])
        return Interval(lower, upper, fraction.coerceIn(0f, 1f))
    }

    private data class Interval(val lower: Int, val upper: Int, val fraction: Float)
}
