package com.example.duudle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    private val view = Size(1000f, 1200f)

    @Test
    fun unzoomedTouchMapsToItself() {
        assertEquals(Offset(250f, 600f), screenToCanvas(Offset(250f, 600f), 1f, Offset.Zero, view))
    }

    @Test
    fun zoomedTouchMapsBackToUnzoomedCanvas() {
        // 2x zoom with the canvas shifted so canvas (500, 600) sits at the view's top-left
        val offset = Offset(-1000f, -1200f)
        assertEquals(Offset(500f, 600f), screenToCanvas(Offset(0f, 0f), 2f, offset, view))
        assertEquals(Offset(1000f, 1200f), screenToCanvas(Offset(1000f, 1200f), 2f, offset, view))
    }

    @Test
    fun touchesOutsideTheCanvasAreClamped() {
        assertEquals(Offset(0f, 1200f), screenToCanvas(Offset(-50f, 5000f), 1f, Offset.Zero, view))
    }

    @Test
    fun pinchKeepsThePointUnderTheFingersFixed() {
        val centroid = Offset(400f, 300f)
        val before = screenToCanvas(centroid, 1f, Offset.Zero, view)
        val (scale, offset) = applyZoomPan(1f, Offset.Zero, centroid, 2f, Offset.Zero, view)
        assertEquals(2f, scale)
        assertEquals(before, screenToCanvas(centroid, scale, offset, view))
    }

    @Test
    fun zoomIsClampedBetweenOneAndMax() {
        assertEquals(MAX_ZOOM, applyZoomPan(3f, Offset.Zero, Offset.Zero, 10f, Offset.Zero, view).first)
        assertEquals(1f, applyZoomPan(1.5f, Offset.Zero, Offset.Zero, 0.1f, Offset.Zero, view).first)
    }

    @Test
    fun panCannotDragTheCanvasOffScreen() {
        val (_, atOne) = applyZoomPan(1f, Offset.Zero, Offset.Zero, 1f, Offset(300f, -300f), view)
        assertEquals(Offset.Zero, atOne)
        val (_, atTwo) = applyZoomPan(2f, Offset.Zero, Offset.Zero, 1f, Offset(-5000f, -5000f), view)
        assertEquals(Offset(-1000f, -1200f), atTwo)
    }
}
