package com.karen_yao.chinesetravel.features.capture.camera

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
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
 * Device-side benchmark for the production OCR inputs. Results are written to app storage so
 * timing and recognition quality can be inspected without relying on logcat. Timing is
 * observational only and must not be used as a CI performance threshold.
 */
@RunWith(AndroidJUnit4::class)
class OcrAccuracyComparisonTest {

    @Test
    fun benchmarkOriginalAndEnhancedBitmapInputs() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        val processor = ImageProcessor()
        val report = mutableListOf(
            "asset\tvariant\titeration\texpected\tdetected\tconfidence\teditDistance\tCER" +
                "\tpreprocessingMs\trecognitionMs\ttotalMs"
        )

        try {
            // Warm model initialization so the first measured fixture is comparable.
            copyAsset(context, FIXTURES.first().assetName).also { warmupFile ->
                recognize(context, recognizer, warmupFile)
                warmupFile.delete()
            }

            FIXTURES.forEach { fixture ->
                val originalFile = copyAsset(context, fixture.assetName)
                try {
                    repeat(MEASURED_ITERATIONS) { iteration ->
                        val variants = if (iteration % 2 == 0) {
                            listOf(Variant.ORIGINAL, Variant.ENHANCED_BITMAP)
                        } else {
                            listOf(Variant.ENHANCED_BITMAP, Variant.ORIGINAL)
                        }
                        variants.forEach { variant ->
                            val result = when (variant) {
                                Variant.ORIGINAL -> benchmarkOriginal(context, recognizer, originalFile)
                                Variant.ENHANCED_BITMAP -> benchmarkEnhanced(
                                    processor,
                                    recognizer,
                                    originalFile
                                )
                            }
                            report += fixture.toReportLine(variant.label, iteration + 1, result)
                        }
                    }
                } finally {
                    originalFile.delete()
                }
            }
        } finally {
            recognizer.close()
        }

        val reportText = report.joinToString("\n")
        File(context.filesDir, REPORT_FILE_NAME).writeText(reportText)
        report.forEach { line -> Log.i(REPORT_LOG_TAG, line) }
    }

    private suspend fun benchmarkOriginal(
        context: Context,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        file: File
    ): BenchmarkResult {
        val totalStarted = SystemClock.elapsedRealtime()
        val recognition = timedRecognition(recognizer) {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        }
        return BenchmarkResult(
            lines = recognition.lines,
            preprocessingMs = 0L,
            recognitionMs = recognition.elapsedMs,
            totalMs = SystemClock.elapsedRealtime() - totalStarted
        )
    }

    private suspend fun benchmarkEnhanced(
        processor: ImageProcessor,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        file: File
    ): BenchmarkResult {
        val totalStarted = SystemClock.elapsedRealtime()
        val preprocessingStarted = SystemClock.elapsedRealtime()
        val enhanced = checkNotNull(processor.preprocessBitmapForOcr(file)) {
            "Could not preprocess ${file.name}"
        }
        val preprocessingMs = SystemClock.elapsedRealtime() - preprocessingStarted
        return try {
            val recognition = timedRecognition(recognizer) {
                InputImage.fromBitmap(enhanced.bitmap, 0)
            }
            BenchmarkResult(
                lines = recognition.lines,
                preprocessingMs = preprocessingMs,
                recognitionMs = recognition.elapsedMs,
                totalMs = SystemClock.elapsedRealtime() - totalStarted
            )
        } finally {
            enhanced.close()
        }
    }

    private suspend fun timedRecognition(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: () -> InputImage
    ): TimedRecognition {
        val started = SystemClock.elapsedRealtime()
        val lines = recognizer.process(image()).await().textBlocks
            .flatMap { block -> block.lines }
            .map { line -> RecognizedLine(line.text, line.confidence) }
        return TimedRecognition(lines, SystemClock.elapsedRealtime() - started)
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

    private fun Fixture.toReportLine(
        variant: String,
        iteration: Int,
        result: BenchmarkResult
    ): String {
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
            iteration,
            expected,
            detectedText.ifEmpty { "<none>" },
            "%.3f".format(best?.confidence ?: 0f),
            distance,
            "%.3f".format(cer),
            result.preprocessingMs,
            result.recognitionMs,
            result.totalMs
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

    private data class BenchmarkResult(
        val lines: List<RecognizedLine>,
        val preprocessingMs: Long,
        val recognitionMs: Long,
        val totalMs: Long
    )

    private data class TimedRecognition(
        val lines: List<RecognizedLine>,
        val elapsedMs: Long
    )

    private data class RecognizedLine(
        val text: String,
        val confidence: Float
    )

    private companion object {
        const val REPORT_FILE_NAME = "ocr_accuracy_comparison.tsv"
        const val REPORT_LOG_TAG = "OcrBenchmark"
        const val MEASURED_ITERATIONS = 5

        val FIXTURES = listOf(
            Fixture("chinese_character.jpg", "山"),
            Fixture("IMG_3849.JPG", "八邑酒楼"),
            Fixture("IMG_3950.JPG", "永庆坊")
        )
    }

    private enum class Variant(val label: String) {
        ORIGINAL("original"),
        ENHANCED_BITMAP("enhanced_bitmap")
    }
}
