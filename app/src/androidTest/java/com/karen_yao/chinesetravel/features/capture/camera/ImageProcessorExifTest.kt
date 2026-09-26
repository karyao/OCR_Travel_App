package com.karen_yao.chinesetravel.features.capture.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageProcessorExifTest {

    @Test
    fun extractsCoordinatesWrittenToPhotoExif() {
        val file = createJpeg("located.jpg")
        val expected = Location("test").apply {
            latitude = 49.2827
            longitude = -123.1207
        }
        ExifInterface(file).apply {
            setGpsInfo(expected)
            saveAttributes()
        }

        val actual = ImageProcessor().extractLocationFromFile(file)

        assertEquals(expected.latitude, actual!!.first, COORDINATE_TOLERANCE)
        assertEquals(expected.longitude, actual.second, COORDINATE_TOLERANCE)
    }

    @Test
    fun returnsNullWhenPhotoHasNoGpsExif() {
        val file = createJpeg("unlocated.jpg")

        assertNull(ImageProcessor().extractLocationFromFile(file))
    }

    @Test
    fun preprocessingAppliesGrayscaleThenContrast() = runBlocking {
        val source = createTwoToneJpeg("two-tone.jpg")
        val output = ImageProcessor().preprocessImageForOCR(source, source.parentFile!!)

        val sourceBitmap = BitmapFactory.decodeFile(source.absolutePath)
        val outputBitmap = BitmapFactory.decodeFile(output.absolutePath)
        try {
            val sourceLeft = sourceBitmap.getPixel(16, sourceBitmap.height / 2)
            val sourceRight = sourceBitmap.getPixel(sourceBitmap.width - 16, sourceBitmap.height / 2)
            val outputLeft = outputBitmap.getPixel(16, outputBitmap.height / 2)
            val outputRight = outputBitmap.getPixel(outputBitmap.width - 16, outputBitmap.height / 2)

            assertGrayscale(outputLeft)
            assertGrayscale(outputRight)

            val sourceLuminanceRange = luminance(sourceRight) - luminance(sourceLeft)
            val outputRange = Color.red(outputRight) - Color.red(outputLeft)
            assertTrue(
                "Expected contrast to increase ($sourceLuminanceRange -> $outputRange)",
                outputRange > sourceLuminanceRange
            )
        } finally {
            sourceBitmap.recycle()
            outputBitmap.recycle()
            output.delete()
        }
    }

    @Test
    fun preprocessingPreservesNinetyDegreeExifOrientation() = runBlocking {
        val source = createJpeg("rotated.jpg")
        ExifInterface(source).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
            saveAttributes()
        }

        val output = ImageProcessor().preprocessImageForOCR(source, source.parentFile!!)
        try {
            val actual = ExifInterface(output).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            assertEquals(ExifInterface.ORIENTATION_ROTATE_90, actual)
        } finally {
            output.delete()
        }
    }

    @Test
    fun failedCompressionReturnsOriginalAndDeletesPartialOutput() = runBlocking {
        val source = createJpeg("compression-failure.jpg")
        val outputDirectory = source.parentFile!!
        val filesBefore = preprocessedFiles(outputDirectory)
        val processor = ImageProcessor(compressBitmap = { _, output ->
            output.write(1)
            false
        })

        val result = processor.preprocessImageForOCR(source, outputDirectory)

        assertSame(source, result)
        assertEquals(filesBefore, preprocessedFiles(outputDirectory))
    }

    @Test
    fun cancellationDeletesPartialOutputAndIsRethrown() = runBlocking {
        val source = createJpeg("compression-cancellation.jpg")
        val outputDirectory = source.parentFile!!
        val filesBefore = preprocessedFiles(outputDirectory)
        val processor = ImageProcessor(compressBitmap = { _, output ->
            output.write(1)
            throw CancellationException("test cancellation")
        })

        try {
            processor.preprocessImageForOCR(source, outputDirectory)
            fail("Expected preprocessing cancellation to be rethrown")
        } catch (_: CancellationException) {
            // Expected.
        }

        assertEquals(filesBefore, preprocessedFiles(outputDirectory))
    }

    private fun createJpeg(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        file.delete()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun createTwoToneJpeg(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        file.delete()
        val bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
        try {
            for (x in 0 until bitmap.width) {
                val color = if (x < bitmap.width / 2) {
                    Color.rgb(70, 130, 190)
                } else {
                    Color.rgb(180, 140, 80)
                }
                for (y in 0 until bitmap.height) bitmap.setPixel(x, y, color)
            }
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun assertGrayscale(color: Int) {
        assertTrue(kotlin.math.abs(Color.red(color) - Color.green(color)) <= COLOR_TOLERANCE)
        assertTrue(kotlin.math.abs(Color.green(color) - Color.blue(color)) <= COLOR_TOLERANCE)
    }

    private fun luminance(color: Int): Int =
        (0.213f * Color.red(color) +
            0.715f * Color.green(color) +
            0.072f * Color.blue(color)).toInt()

    private fun preprocessedFiles(directory: File): Set<String> =
        directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("preprocessed_") }
            .mapTo(mutableSetOf()) { it.name }

    private companion object {
        const val COORDINATE_TOLERANCE = 0.000001
        const val COLOR_TOLERANCE = 3
    }
}
