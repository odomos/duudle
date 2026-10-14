package com.example.duudle

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimplifyStrokeTest {

    @Test
    fun straightLineCollapsesToEndpoints() {
        val line = (0..100).map { Offset(it / 100f, 0.5f) }
        assertEquals(listOf(Offset(0f, 0.5f), Offset(1f, 0.5f)), simplifyStroke(line, 0.0012f))
    }

    @Test
    fun cornerIsKept() {
        val corner = (0..50).map { Offset(it / 100f, 0f) } + (1..50).map { Offset(0.5f, it / 100f) }
        val simplified = simplifyStroke(corner, 0.0012f)
        assertEquals(listOf(Offset(0f, 0f), Offset(0.5f, 0f), Offset(0.5f, 0.5f)), simplified)
    }

    @Test
    fun shortStrokesAreUntouched() {
        val dot = listOf(Offset(0.3f, 0.3f))
        assertEquals(dot, simplifyStroke(dot, 0.0012f))
        val pair = listOf(Offset(0.1f, 0.1f), Offset(0.2f, 0.2f))
        assertEquals(pair, simplifyStroke(pair, 0.0012f))
    }

    @Test
    fun smoothCurveShrinksButStaysWithinTolerance() {
        val curve = (0..300).map { i ->
            val t = i / 300f
            Offset(t, 0.5f + 0.3f * kotlin.math.sin(t * 2 * Math.PI).toFloat())
        }
        val simplified = simplifyStroke(curve, 0.0012f)
        assertTrue("expected big reduction, got ${simplified.size}", simplified.size < curve.size / 3)
        assertEquals(curve.first(), simplified.first())
        assertEquals(curve.last(), simplified.last())
    }
}
