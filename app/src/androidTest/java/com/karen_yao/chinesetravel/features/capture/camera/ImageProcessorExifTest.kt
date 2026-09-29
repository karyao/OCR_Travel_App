package com.karen_yao.chinesetravel.features.capture.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val output = checkNotNull(ImageProcessor().preprocessBitmapForOcr(source))

        val sourceBitmap = BitmapFactory.decodeFile(source.absolutePath)
        val outputBitmap = output.bitmap
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
            output.close()
        }
    }

    @Test
    fun preprocessingAppliesNinetyDegreeExifOrientation() = runBlocking {
        val source = createSolidJpeg("rotated.jpg", width = 80, height = 40)
        ExifInterface(source).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
            saveAttributes()
        }

        val output = checkNotNull(ImageProcessor().preprocessBitmapForOcr(source))
        try {
            assertEquals(40, output.bitmap.width)
            assertEquals(80, output.bitmap.height)
        } finally {
            output.close()
        }
    }

    @Test
    fun preprocessingAppliesHorizontalExifReflection() = runBlocking {
        val source = createTwoToneJpeg("reflected.jpg")
        ExifInterface(source).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString()
            )
            saveAttributes()
        }

        val output = checkNotNull(ImageProcessor().preprocessBitmapForOcr(source))
        try {
            val left = Color.red(output.bitmap.getPixel(16, output.bitmap.height / 2))
            val right = Color.red(
                output.bitmap.getPixel(output.bitmap.width - 16, output.bitmap.height / 2)
            )
            assertTrue("Expected reflected bright half on the left", left > right)
        } finally {
            output.close()
        }
    }

    @Test
    fun failedDecodeReturnsNull() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "invalid-image.jpg").apply {
            writeText("not an image")
        }

        assertNull(ImageProcessor().preprocessBitmapForOcr(source))
    }

    @Test
    fun cancellationIsRethrown() = runBlocking {
        val source = createJpeg("preprocessing-cancellation.jpg")
        val processor = ImageProcessor(computationDispatcher = CancellingDispatcher)

        try {
            processor.preprocessBitmapForOcr(source)
            fail("Expected preprocessing cancellation to be rethrown")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun preprocessingBoundsLargeImageAndPreservesAspectRatio() = runBlocking {
        val source = createSolidJpeg("large.jpg", width = 3000, height = 2000)
        val output = checkNotNull(ImageProcessor().preprocessBitmapForOcr(source))

        val bounds = output.bitmap.width to output.bitmap.height
        try {
            assertTrue(bounds.first <= MAX_TEST_LONG_EDGE)
            assertTrue(bounds.first.toLong() * bounds.second <= MAX_TEST_PIXEL_COUNT)
            assertEquals(
                3000f / 2000f,
                bounds.first.toFloat() / bounds.second,
                ASPECT_RATIO_TOLERANCE
            )
        } finally {
            output.close()
        }
    }

    @Test
    fun preprocessingKeepsSmallImageDimensions() = runBlocking {
        val source = createSolidJpeg("small.jpg", width = 640, height = 480)
        val output = checkNotNull(ImageProcessor().preprocessBitmapForOcr(source))

        try {
            assertEquals(640 to 480, output.bitmap.width to output.bitmap.height)
        } finally {
            output.close()
        }
    }

    @Test
    fun closingEnhancedImageRecyclesBitmapAndIsIdempotent() = runBlocking {
        val source = createJpeg("recycled.jpg")
        val output = checkNotNull(ImageProcessor().preprocessBitmapForOcr(source))
        val bitmap = output.bitmap

        output.close()
        output.close()

        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun preprocessingDoesNotCreateTemporaryJpeg() = runBlocking {
        val source = createJpeg("in-memory.jpg")
        val directory = source.parentFile!!
        val filesBefore = preprocessedFiles(directory)

        ImageProcessor().preprocessBitmapForOcr(source)?.use { }

        assertEquals(filesBefore, preprocessedFiles(directory))
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

    private fun createSolidJpeg(name: String, width: Int, height: Int): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        file.delete()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
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
        const val ASPECT_RATIO_TOLERANCE = 0.001f
        const val MAX_TEST_LONG_EDGE = 2560
        const val MAX_TEST_PIXEL_COUNT = 4_000_000L
    }

    private object CancellingDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            throw CancellationException("test cancellation")
        }
    }
}
