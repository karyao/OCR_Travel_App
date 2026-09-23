package com.karen_yao.chinesetravel.features.capture.camera

import android.graphics.Bitmap
import android.location.Location
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun createJpeg(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
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

    private companion object {
        const val COORDINATE_TOLERANCE = 0.000001
    }
}
