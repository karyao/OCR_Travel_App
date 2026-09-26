package com.karen_yao.chinesetravel.features.capture.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {

    @Test
    fun smallImageUsesOriginalDimensions() {
        assertEquals(1, calculateOcrInSampleSize(1920, 1080))
    }

    @Test
    fun largeLandscapeImageIsSampledWithinBothLimits() {
        assertEquals(2, calculateOcrInSampleSize(4032, 3024))
    }

    @Test
    fun largePortraitImageIsSampledWithinBothLimits() {
        assertEquals(2, calculateOcrInSampleSize(3024, 4032))
    }

    @Test
    fun squareImageUsesPixelLimit() {
        assertEquals(2, calculateOcrInSampleSize(2560, 2560))
    }

    @Test
    fun panoramicImageUsesLongEdgeLimit() {
        assertEquals(4, calculateOcrInSampleSize(8000, 1000))
    }

    @Test
    fun veryLargeImageUsesPowerOfTwoSampling() {
        assertEquals(8, calculateOcrInSampleSize(16000, 12000))
    }

    @Test
    fun invalidDimensionsUseSafeDefault() {
        assertEquals(1, calculateOcrInSampleSize(0, 1000))
        assertEquals(1, calculateOcrInSampleSize(1000, 0))
        assertEquals(1, calculateOcrInSampleSize(-1, -1))
    }
}
