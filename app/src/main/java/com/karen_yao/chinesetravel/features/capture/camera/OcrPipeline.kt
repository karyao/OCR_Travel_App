package com.karen_yao.chinesetravel.features.capture.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

internal const val MIN_FAST_PATH_CONFIDENCE = 0.75f

internal data class OcrLine(
    val text: String,
    val confidence: Float
)

internal data class OcrPass(
    val lines: List<OcrLine>,
    val confidence: Float = calculateOcrConfidence(lines)
)

internal data class OcrOutcome(
    val selectedPass: OcrPass,
    val originalConfidence: Float,
    val usedEnhancedInput: Boolean
)

/**
 * Gives longer lines more influence while retaining the confidence returned by ML Kit.
 * A confidence of zero is valid and also covers ML Kit installations where confidence
 * information is unavailable.
 */
internal fun calculateOcrConfidence(lines: List<OcrLine>): Float {
    val chineseLines = lines.filter { line -> line.text.any(::isCjkCharacter) }
    if (chineseLines.isEmpty()) return 0f

    var weightedConfidence = 0f
    var totalWeight = 0
    chineseLines.forEach { line ->
        val weight = line.text.codePointCount(0, line.text.length).coerceAtLeast(1)
        weightedConfidence += line.confidence.coerceIn(0f, 1f) * weight
        totalWeight += weight
    }
    return if (totalWeight == 0) 0f else weightedConfidence / totalWeight
}

private fun isCjkCharacter(character: Char): Boolean =
    character in '\u3400'..'\u4DBF' ||
        character in '\u4E00'..'\u9FFF' ||
        character in '\uF900'..'\uFAFF'

internal fun shouldTryEnhancedInput(
    pass: OcrPass,
    confidenceThreshold: Float = MIN_FAST_PATH_CONFIDENCE
): Boolean = pass.lines.isEmpty() || pass.confidence < confidenceThreshold

internal fun chooseBetterOcrPass(original: OcrPass, enhanced: OcrPass): OcrPass = when {
    enhanced.lines.isEmpty() -> original
    original.lines.isEmpty() -> enhanced
    enhanced.confidence > original.confidence -> enhanced
    else -> original
}

/**
 * Runs OCR against the untouched image first. Image enhancement is paid for only when the
 * first pass is empty or low confidence, and is retained only when it improves confidence.
 */
internal class OcrPipeline(
    private val applicationContext: Context,
    private val imageProcessor: ImageProcessor,
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )
) : Closeable {

    suspend fun recognize(originalFile: File, cacheDirectory: File): OcrOutcome {
        val originalPass = recognizeFile(originalFile)
        if (!shouldTryEnhancedInput(originalPass)) {
            return OcrOutcome(
                selectedPass = originalPass,
                originalConfidence = originalPass.confidence,
                usedEnhancedInput = false
            )
        }

        val enhancedFile = imageProcessor.preprocessImageForOCR(originalFile, cacheDirectory)
        if (enhancedFile.canonicalPath == originalFile.canonicalPath) {
            return OcrOutcome(
                selectedPass = originalPass,
                originalConfidence = originalPass.confidence,
                usedEnhancedInput = false
            )
        }

        return try {
            val enhancedPass = recognizeFile(enhancedFile)
            val selectedPass = chooseBetterOcrPass(originalPass, enhancedPass)
            OcrOutcome(
                selectedPass = selectedPass,
                originalConfidence = originalPass.confidence,
                usedEnhancedInput = selectedPass === enhancedPass
            )
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (!enhancedFile.delete()) {
                    Log.w(TAG, "Could not delete temporary OCR image: ${enhancedFile.name}")
                }
            }
        }
    }

    private suspend fun recognizeFile(file: File): OcrPass {
        val image = withContext(Dispatchers.IO) {
            InputImage.fromFilePath(applicationContext, Uri.fromFile(file))
        }
        val result = recognizer.process(image).await()
        return OcrPass(result.toOcrLines())
    }

    override fun close() {
        recognizer.close()
    }

    private fun Text.toOcrLines(): List<OcrLine> = textBlocks
        .flatMap { block -> block.lines }
        .mapNotNull { line ->
            val normalizedText = line.text.trim()
            normalizedText.takeIf(String::isNotEmpty)?.let {
                OcrLine(text = it, confidence = line.confidence)
            }
        }

    private companion object {
        const val TAG = "OcrPipeline"
    }
}
