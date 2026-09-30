package com.karen_yao.chinesetravel.features.capture.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {

    @Test
    fun smallImageUsesOriginalDimensions() {
        assertEquals(1, calculateOcrInSampleSize(1920, 1080))
    }

    @Test
    fun largeLandscapeImageRetainsTargetDetailDuringDecode() {
        assertEquals(1, calculateOcrInSampleSize(4032, 3024))
    }

    @Test
    fun largePortraitImageRetainsTargetDetailDuringDecode() {
        assertEquals(1, calculateOcrInSampleSize(3024, 4032))
    }

    @Test
    fun squareImageRetainsTargetDetailDuringDecode() {
        assertEquals(1, calculateOcrInSampleSize(2560, 2560))
    }

    @Test
    fun panoramicImageRetainsTargetDetailDuringDecode() {
        assertEquals(2, calculateOcrInSampleSize(8000, 1000))
    }

    @Test
    fun veryLargeImageUsesPowerOfTwoSampling() {
        assertEquals(4, calculateOcrInSampleSize(16000, 12000))
    }

    @Test
    fun targetPreservesMoreDetailThanHalfSizeDecode() {
        assertEquals(OcrImageSize(2309, 1732), calculateOcrImageSize(4032, 3024))
        assertEquals(OcrImageSize(1732, 2309), calculateOcrImageSize(3024, 4032))
    }

    @Test
    fun targetHonorsPixelAndLongEdgeBudgets() {
        assertEquals(OcrImageSize(2000, 2000), calculateOcrImageSize(2560, 2560))
        assertEquals(OcrImageSize(2560, 320), calculateOcrImageSize(8000, 1000))
    }

    @Test
    fun smallImagesAreNotUpscaled() {
        assertEquals(OcrImageSize(640, 480), calculateOcrImageSize(640, 480))
    }

    @Test
    fun invalidDimensionsUseSafeDefault() {
        assertEquals(1, calculateOcrInSampleSize(0, 1000))
        assertEquals(1, calculateOcrInSampleSize(1000, 0))
        assertEquals(1, calculateOcrInSampleSize(-1, -1))
    }
}
