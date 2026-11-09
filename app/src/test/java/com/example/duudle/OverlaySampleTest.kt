package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySampleTest {
    @Test
    fun sampleIsASmileyInsideTheSquare() {
        val strokes = sampleDoodleStrokes()
        assertEquals(4, strokes.size) // face, two eyes, smile
        for (s in strokes) {
            assertTrue(s.points.size >= 2)
            for (p in s.points) {
                assertTrue("x out of 0-1: ${p.x}", p.x in 0f..1f)
                assertTrue("y out of 0-1: ${p.y}", p.y in 0f..1f)
            }
        }
    }

    @Test
    fun theSmileCurvesDownwards() {
        val smile = sampleDoodleStrokes().last().points
        val middle = smile[smile.size / 2]
        assertTrue(middle.y > smile.first().y && middle.y > smile.last().y)
    }
}
