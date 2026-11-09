package com.example.duudle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The doodle "Test a pop-up" shows in release builds (Profile, and Home's overlay pill): a
 * smiley in the normal 0-1 stroke format, so the sample looks like a real friend's doodle.
 */
fun sampleDoodleStrokes(): List<DoodleStroke> {
    val ink = Color(0xFF141414)
    val pink = Color(0xFFF27AC6)
    fun arc(cx: Float, cy: Float, r: Float, from: Float, to: Float, steps: Int): List<Offset> =
        (0..steps).map { i ->
            val a = from + (to - from) * i / steps
            Offset(cx + r * cos(a), cy + r * sin(a))
        }
    val tau = (2 * PI).toFloat()
    return listOf(
        DoodleStroke(arc(0.5f, 0.5f, 0.34f, 0f, tau, 48), pink, 12f),
        DoodleStroke(listOf(Offset(0.39f, 0.37f), Offset(0.39f, 0.46f)), ink, 12f),
        DoodleStroke(listOf(Offset(0.61f, 0.37f), Offset(0.61f, 0.46f)), ink, 12f),
        // y grows downwards, so angles between 0 and pi draw the lower half: a smile.
        DoodleStroke(arc(0.5f, 0.5f, 0.2f, 0.15f * tau / 2, 0.85f * tau / 2, 16), ink, 12f)
    )
}
