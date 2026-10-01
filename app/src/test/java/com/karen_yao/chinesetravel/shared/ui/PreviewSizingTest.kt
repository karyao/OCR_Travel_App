package com.karen_yao.chinesetravel.shared.ui

import kotlin.math.ceil
import org.junit.Assert.*
import org.junit.Test

class PreviewSizingTest {
    @Test fun largeTutorialPhotoIsSampledBeforeItsFinalResize() {
        val target = PreviewSize(600, 600)
        for (source in listOf(PreviewSize(4032, 3024), PreviewSize(3024, 4032))) {
            val sample = previewSampleSize(source, target, PreviewScale.FIT_CENTER)
            assertEquals(4, sample)
            val sampled = PreviewSize(source.width / sample, source.height / sample)
            val output = previewTransform(sampled, target, PreviewScale.FIT_CENTER).output
            assertTrue(output.width <= 600 && output.height <= 600)
            assertEquals(600, maxOf(output.width, output.height))
        }
    }

    @Test fun fitCenterPreservesTheEntirePortraitAndLandscapeImage() {
        for (source in listOf(PreviewSize(800, 400), PreviewSize(400, 800))) {
            val transform = previewTransform(source, PreviewSize(200, 200), PreviewScale.FIT_CENTER)
            assertEquals(0, transform.left)
            assertEquals(0, transform.top)
            assertEquals(source.width, transform.cropWidth)
            assertEquals(source.height, transform.cropHeight)
            assertEquals(200, maxOf(transform.output.width, transform.output.height))
            assertEquals(100, minOf(transform.output.width, transform.output.height))
        }
    }

    @Test fun centerCropKeepsTheCenterAndMatchesTheVisibleRectangle() {
        val horizontal = previewTransform(PreviewSize(800, 400), PreviewSize(200, 200), PreviewScale.CENTER_CROP)
        assertEquals(200, horizontal.left)
        assertEquals(0, horizontal.top)
        assertEquals(400, horizontal.cropWidth)
        assertEquals(400, horizontal.cropHeight)
        assertEquals(PreviewSize(200, 200), horizontal.output)
        val vertical = previewTransform(PreviewSize(400, 800), PreviewSize(200, 200), PreviewScale.CENTER_CROP)
        assertEquals(0, vertical.left)
        assertEquals(200, vertical.top)
        assertEquals(PreviewSize(200, 200), vertical.output)
    }

    @Test fun smallImagesAreNeverUpscaledDuringDecodeOrResize() {
        for (scale in PreviewScale.entries) {
            assertEquals(1, previewSampleSize(PreviewSize(16, 16), PreviewSize(600, 400), scale))
            assertEquals(PreviewSize(16, 16), previewTransform(PreviewSize(16, 16), PreviewSize(600, 400), scale).output)
        }
    }

    @Test fun extremelyWideAndTallCropsHaveABoundedSampledAllocation() {
        val target = PreviewSize(600, 600)
        for (source in listOf(PreviewSize(160_000, 2000), PreviewSize(2000, 160_000))) {
            val sample = previewSampleSize(source, target, PreviewScale.CENTER_CROP)
            val sampledPixels = ceil(source.width.toDouble() / sample) * ceil(source.height.toDouble() / sample)
            assertTrue(sampledPixels <= 4.0 * target.width * target.height)
        }
    }

    @Test fun zeroLayoutDimensionsCannotTriggerAFullResolutionDecode() {
        for (source in listOf(PreviewSize(0, 100), PreviewSize(100, 0))) {
            assertThrows(IllegalArgumentException::class.java) {
                previewSampleSize(source, PreviewSize(600, 600), PreviewScale.FIT_CENTER)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            previewTransform(PreviewSize(4032, 3024), PreviewSize(0, 0), PreviewScale.FIT_CENTER)
        }
    }
}
