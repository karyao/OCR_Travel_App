package com.karen_yao.chinesetravel.features.capture.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPipelineTest {

    @Test
    fun highConfidenceOriginalUsesFastPath() {
        val pass = OcrPass(listOf(OcrLine("八邑酒楼", 0.92f)))

        assertFalse(shouldTryEnhancedInput(pass))
    }

    @Test
    fun lowConfidenceOriginalRequestsEnhancedInput() {
        val pass = OcrPass(listOf(OcrLine("永庆坊", 0.54f)))

        assertTrue(shouldTryEnhancedInput(pass))
    }

    @Test
    fun noRecognizedTextRequestsEnhancedInput() {
        assertTrue(shouldTryEnhancedInput(OcrPass(emptyList())))
    }

    @Test
    fun confidenceIsWeightedByLineLength() {
        val pass = OcrPass(
            listOf(
                OcrLine("长文本", 0.9f),
                OcrLine("短", 0.5f)
            )
        )

        assertEquals(0.8f, pass.confidence, 0.0001f)
    }

    @Test
    fun unrelatedLatinTextDoesNotLowerChineseConfidence() {
        val pass = OcrPass(
            listOf(
                OcrLine("永庆坊", 0.9f),
                OcrLine("RESTAURANT", 0.2f)
            )
        )

        assertEquals(0.9f, pass.confidence, 0.0001f)
        assertFalse(shouldTryEnhancedInput(pass))
    }

    @Test
    fun nonChineseResultRequestsEnhancedInput() {
        val pass = OcrPass(listOf(OcrLine("RESTAURANT", 0.98f)))

        assertEquals(0f, pass.confidence, 0.0001f)
        assertTrue(shouldTryEnhancedInput(pass))
    }

    @Test
    fun enhancedPassIsKeptOnlyWhenItImprovesConfidence() {
        val original = OcrPass(listOf(OcrLine("永庆坊", 0.55f)))
        val improved = OcrPass(listOf(OcrLine("永庆坊", 0.88f)))
        val degraded = OcrPass(listOf(OcrLine("永庆坊", 0.42f)))

        assertSame(improved, chooseBetterOcrPass(original, improved))
        assertSame(original, chooseBetterOcrPass(original, degraded))
    }
}
