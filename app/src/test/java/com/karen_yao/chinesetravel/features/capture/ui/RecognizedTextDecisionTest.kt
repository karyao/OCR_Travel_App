package com.karen_yao.chinesetravel.features.capture.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizedTextDecisionTest {
    @Test
    fun multipleNonEmptyLinesOpenSelectionInOriginalOrder() {
        val decision = classifyRecognizedText(listOf("第一行", "第二行"))

        assertEquals(
            RecognizedTextDecision.ChooseText(listOf("第一行", "第二行")),
            decision
        )
    }
}
