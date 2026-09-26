package com.karen_yao.chinesetravel.features.capture.camera

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-side A/B fixture for evaluating whether the current preprocessing improves OCR.
 * Results are written to app storage so they can be inspected without relying on logcat.
 */
@RunWith(AndroidJUnit4::class)
class OcrAccuracyComparisonTest {

    @Test
    fun compareOriginalAndPreprocessedInputs() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        val processor = ImageProcessor()
        val report = mutableListOf(
            "asset\tvariant\texpected\tdetected\tconfidence\teditDistance\tCER\telapsedMs"
        )

        try {
            // Warm model initialization so the first measured fixture is comparable.
            copyAsset(context, FIXTURES.first().assetName).also { warmupFile ->
                recognize(context, recognizer, warmupFile)
                warmupFile.delete()
            }

            FIXTURES.forEach { fixture ->
                val originalFile = copyAsset(context, fixture.assetName)
                var processedFile: File? = null
                try {
                    val original = timedRecognition(context, recognizer, originalFile)
                    report += fixture.toReportLine("original", original)

                    val preprocessingStarted = SystemClock.elapsedRealtime()
                    processedFile = processor.preprocessImageForOCR(originalFile, context.cacheDir)
                    val processed = timedRecognition(context, recognizer, processedFile).let { result ->
                        result.copy(
                            elapsedMs = SystemClock.elapsedRealtime() - preprocessingStarted
                        )
                    }
                    report += fixture.toReportLine("preprocessed", processed)
                } finally {
                    processedFile?.takeIf { it != originalFile }?.delete()
                    originalFile.delete()
                }
            }
        } finally {
            recognizer.close()
        }

        File(context.filesDir, REPORT_FILE_NAME).writeText(report.joinToString("\n"))
    }

    private suspend fun timedRecognition(
        context: Context,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        file: File
    ): RecognitionResult {
        val started = SystemClock.elapsedRealtime()
        val lines = recognize(context, recognizer, file)
        return RecognitionResult(
            lines = lines,
            elapsedMs = SystemClock.elapsedRealtime() - started
        )
    }

    private suspend fun recognize(
        context: Context,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        file: File
    ): List<RecognizedLine> {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        return recognizer.process(image).await().textBlocks
            .flatMap { block -> block.lines }
            .map { line -> RecognizedLine(line.text, line.confidence) }
    }

    private fun Fixture.toReportLine(variant: String, result: RecognitionResult): String {
        val candidates = result.lines
            .map { line -> line.copy(text = chineseOnly(line.text)) }
            .filter { line -> line.text.isNotEmpty() }
        val best = candidates.minByOrNull { candidate -> editDistance(expected, candidate.text) }
        val detectedText = best?.text.orEmpty()
        val distance = editDistance(expected, detectedText)
        val cer = distance.toDouble() / expected.length.coerceAtLeast(1)
        return listOf(
            assetName,
            variant,
            expected,
            detectedText.ifEmpty { "<none>" },
            "%.3f".format(best?.confidence ?: 0f),
            distance,
            "%.3f".format(cer),
            result.elapsedMs
        ).joinToString("\t")
    }

    private fun copyAsset(context: Context, assetName: String): File {
        val suffix = assetName.substringAfterLast('.', missingDelimiterValue = "img")
        val destination = File.createTempFile("ocr_ab_", ".$suffix", context.cacheDir)
        context.assets.open(assetName).use { input ->
            destination.outputStream().use(input::copyTo)
        }
        return destination
    }

    private fun chineseOnly(value: String): String = value.filter { character ->
        character in '\u3400'..'\u4DBF' ||
            character in '\u4E00'..'\u9FFF' ||
            character in '\uF900'..'\uFAFF'
    }

    private fun editDistance(expected: String, actual: String): Int {
        var previous = IntArray(actual.length + 1) { it }
        expected.forEachIndexed { expectedIndex, expectedCharacter ->
            val current = IntArray(actual.length + 1)
            current[0] = expectedIndex + 1
            actual.forEachIndexed { actualIndex, actualCharacter ->
                current[actualIndex + 1] = minOf(
                    current[actualIndex] + 1,
                    previous[actualIndex + 1] + 1,
                    previous[actualIndex] + if (expectedCharacter == actualCharacter) 0 else 1
                )
            }
            previous = current
        }
        return previous[actual.length]
    }

    private data class Fixture(val assetName: String, val expected: String)

    private data class RecognitionResult(
        val lines: List<RecognizedLine>,
        val elapsedMs: Long
    )

    private data class RecognizedLine(
        val text: String,
        val confidence: Float
    )

    private companion object {
        const val REPORT_FILE_NAME = "ocr_accuracy_comparison.tsv"

        val FIXTURES = listOf(
            Fixture("chinese_character.jpg", "山"),
            Fixture("IMG_3849.JPG", "八邑酒楼"),
            Fixture("IMG_3950.JPG", "永庆坊")
        )
    }
}
