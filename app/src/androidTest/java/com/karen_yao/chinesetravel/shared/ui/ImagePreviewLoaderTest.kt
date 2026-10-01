package com.karen_yao.chinesetravel.shared.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karen_yao.chinesetravel.MainActivity
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePreviewLoaderTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun largeTutorialAssetDecodesOffMainIntoTheMeasuredPreview() {
        val native = BitmapPreviewDecoder(context.assets)
        val decoder = PreviewDecoder { source, size, scale ->
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            native.decode(source, size, scale)
        }
        withTarget(decoder) { fixture ->
            onMain { fixture.loader.loadAsset("IMG_3950.JPG") }
            val bitmap = fixture.image.awaitBitmap()
            assertTrue(bitmap.width <= 600 && bitmap.height <= 600)
            assertEquals(600, maxOf(bitmap.width, bitmap.height))
            assertTrue("Preview must use at most 1.4 MiB, rather than the 46.5 MiB original", bitmap.allocationByteCount <= 600 * 600 * 4)
        }
    }

    @Test fun allExifOrientationsKeepTheExpectedCornersWithoutEditingThePhoto() = withPhotoDirectory { directory ->
        val expectedCorners = listOf(
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE),
            listOf(Color.GREEN, Color.RED, Color.WHITE, Color.BLUE),
            listOf(Color.WHITE, Color.BLUE, Color.GREEN, Color.RED),
            listOf(Color.BLUE, Color.WHITE, Color.RED, Color.GREEN),
            listOf(Color.RED, Color.BLUE, Color.GREEN, Color.WHITE),
            listOf(Color.BLUE, Color.RED, Color.WHITE, Color.GREEN),
            listOf(Color.WHITE, Color.GREEN, Color.BLUE, Color.RED),
            listOf(Color.GREEN, Color.WHITE, Color.RED, Color.BLUE)
        )
        for (orientation in 1..8) {
            val photo = File(directory, "orientation-$orientation.jpg")
            writeJpeg(photo, 80, 40) { x, y ->
                when {
                    y < 20 && x < 40 -> Color.RED
                    y < 20 -> Color.GREEN
                    x < 40 -> Color.BLUE
                    else -> Color.WHITE
                }
            }
            ExifInterface(photo).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val original = photo.readBytes()
            val bitmap = runBlocking(Dispatchers.IO) {
                BitmapPreviewDecoder(context.assets).decode(PreviewSource.Photo(photo), PreviewSize(80, 80), PreviewScale.FIT_CENTER)
            }
            try {
                val swapsAxes = orientation in 5..8
                assertEquals(if (swapsAxes) 40 else 80, bitmap.width)
                assertEquals(if (swapsAxes) 80 else 40, bitmap.height)
                val actual = listOf(
                    bitmap.getPixel(5, 5), bitmap.getPixel(bitmap.width - 6, 5),
                    bitmap.getPixel(5, bitmap.height - 6), bitmap.getPixel(bitmap.width - 6, bitmap.height - 6)
                )
                actual.zip(expectedCorners[orientation - 1]).forEach { (color, expected) ->
                    assertColorClose(expected, color)
                }
                assertArrayEquals(original, photo.readBytes())
            } finally { bitmap.recycle() }
        }
    }

    @Test fun filePreviewCenterCropsAndKeepsTheOriginalPhoto() = withPhotoDirectory { directory ->
        val photo = File(directory, "crop.jpg")
        writeJpeg(photo, 200, 100) { x, _ -> if (x in 50..149) Color.BLUE else Color.RED }
        val original = photo.readBytes()
        withTarget(width = 50, height = 50, scale = ImageView.ScaleType.CENTER_CROP) { fixture ->
            onMain { fixture.loader.loadFile(photo) }
            val bitmap = fixture.image.awaitBitmap()
            assertEquals(50, bitmap.width)
            assertEquals(50, bitmap.height)
            assertColorClose(Color.BLUE, bitmap.getPixel(5, 25))
            assertColorClose(Color.BLUE, bitmap.getPixel(44, 25))
            assertArrayEquals(original, photo.readBytes())
        }
    }

    @Test fun missingAndCorruptSourcesShowTheFallback() = withPhotoDirectory { directory ->
        val corrupt = File(directory, "corrupt.jpg").apply { writeText("not an image") }
        withTarget { fixture ->
            for (source in listOf(
                PreviewSource.Asset("missing-preview.jpg"),
                PreviewSource.Photo(File(directory, "missing.jpg")), PreviewSource.Photo(corrupt)
            )) {
                onMain {
                    when (source) {
                        is PreviewSource.Asset -> fixture.loader.loadAsset(source.name)
                        is PreviewSource.Photo -> fixture.loader.loadFile(source.file)
                    }
                }
                assertEquals(android.R.drawable.ic_menu_camera, fixture.image.fallbacks.poll(5, TimeUnit.SECONDS))
            }
            assertTrue(fixture.image.bitmaps.isEmpty())
        }
    }

    @Test fun waitsForLayoutAndUsesContentPixelsWhenTheViewResizes() {
        val requestedSizes = LinkedBlockingQueue<PreviewSize>()
        val decoder = PreviewDecoder { _, size, _ ->
            requestedSizes.offer(size)
            Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        }
        withTarget(decoder, width = 0, height = 0) { fixture ->
            onMain {
                fixture.image.setPadding(10, 20, 30, 40)
                fixture.loader.loadAsset("layout.jpg")
                assertTrue(requestedSizes.isEmpty())
                fixture.image.layout(0, 0, 600, 600)
            }
            fixture.image.awaitBitmap()
            assertEquals(PreviewSize(560, 540), requestedSizes.poll(5, TimeUnit.SECONDS))
            onMain { fixture.image.layout(0, 0, 300, 200) }
            fixture.image.awaitBitmap()
            assertEquals(PreviewSize(260, 140), requestedSizes.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test fun stoppedRequestsCancelAndResumeButCompletedImagesDoNotReload() {
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        val decoder = PreviewDecoder { _, _, _ ->
            calls.incrementAndGet()
            started.countDown()
            try { gate.await() } catch (error: CancellationException) {
                cancelled.countDown()
                throw error
            }
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        withTarget(decoder) { fixture ->
            onMain { fixture.loader.loadAsset("resume.jpg") }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            onMain { fixture.owner.registry.currentState = Lifecycle.State.CREATED }
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            assertTrue(fixture.image.fallbacks.isEmpty())
            onMain { fixture.owner.registry.currentState = Lifecycle.State.STARTED }
            gate.complete(Unit)
            val bitmap = fixture.image.awaitBitmap()
            assertEquals(2, calls.get())
            onMain {
                fixture.owner.registry.currentState = Lifecycle.State.CREATED
                fixture.owner.registry.currentState = Lifecycle.State.STARTED
                fixture.loader.loadAsset("resume.jpg")
                assertSame(bitmap, (fixture.image.drawable as BitmapDrawable).bitmap)
            }
            assertEquals(2, calls.get())
            assertTrue(fixture.image.bitmaps.isEmpty())
        }
    }

    @Test fun supersededNativeResultIsRecycledAndNeverDisplayed() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stale = AtomicReference<Bitmap>()
        val calls = AtomicInteger()
        val decoder = PreviewDecoder { source, _, _ ->
            calls.incrementAndGet()
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            if (source == PreviewSource.Asset("first.jpg")) {
                stale.set(bitmap)
                started.countDown()
                // Simulates a native decode which cannot be interrupted by coroutine cancellation.
                release.await(5, TimeUnit.SECONDS)
            }
            bitmap
        }
        try {
            withTarget(decoder) { fixture ->
                onMain { fixture.loader.loadAsset("first.jpg") }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                onMain { fixture.loader.loadAsset("second.jpg") }
                assertEquals("Only one native decode may allocate at a time", 1, calls.get())
                release.countDown()
                val latest = fixture.image.awaitBitmap()
                assertTrue(stale.get().isRecycled)
                assertNotSame(stale.get(), latest)
                assertEquals(2, calls.get())
                assertTrue(fixture.image.bitmaps.isEmpty())
                assertTrue(fixture.image.fallbacks.isEmpty())
            }
        } finally { release.countDown() }
    }

    @Test fun destroyingTheViewDiscardsLateNativeResultsAndClearsItsDrawable() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stale = AtomicReference<Bitmap>()
        val decoder = PreviewDecoder { _, _, _ ->
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            stale.set(bitmap)
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            bitmap
        }
        try {
            withTarget(decoder) { fixture ->
                onMain { fixture.loader.loadAsset("destroy.jpg") }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                onMain { fixture.owner.registry.currentState = Lifecycle.State.DESTROYED }
                release.countDown()
                // The shared decode lock is a barrier: this can complete only after stale cleanup.
                withTarget(PreviewDecoder { _, _, _ -> Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888) }) { next ->
                    onMain { next.loader.loadAsset("next-view.jpg") }
                    next.image.awaitBitmap()
                }
                assertTrue(stale.get().isRecycled)
                assertTrue(fixture.image.bitmaps.isEmpty())
                assertTrue(fixture.image.fallbacks.isEmpty())
                onMain { assertNull(fixture.image.drawable) }
            }
        } finally { release.countDown() }
    }

    private fun withTarget(
        decoder: PreviewDecoder? = null, width: Int = 600, height: Int = 600,
        scale: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
        block: (Fixture) -> Unit
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                val owner = TestOwner()
                val image = RecordingImageView(activity).apply {
                    scaleType = scale
                    layout(0, 0, width, height)
                }
                val loader = ImagePreviewLoader(image, owner, decoder ?: BitmapPreviewDecoder(context.assets))
                fixture = Fixture(image, owner, loader)
                owner.registry.currentState = Lifecycle.State.STARTED
            }
            try { block(fixture) } finally {
                onMain { fixture.owner.registry.currentState = Lifecycle.State.DESTROYED }
            }
        }
    }

    private data class Fixture(val image: RecordingImageView, val owner: TestOwner, val loader: ImagePreviewLoader)

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class RecordingImageView(context: Context) : ImageView(context) {
        val bitmaps = LinkedBlockingQueue<Bitmap>()
        val fallbacks = LinkedBlockingQueue<Int>()
        override fun setImageBitmap(bitmap: Bitmap?) {
            assertEquals("Only main may update the image", Looper.getMainLooper(), Looper.myLooper())
            super.setImageBitmap(bitmap)
            if (bitmap != null) bitmaps.offer(bitmap)
        }
        override fun setImageResource(resId: Int) {
            super.setImageResource(resId)
            fallbacks.offer(resId)
        }
        fun awaitBitmap(): Bitmap = checkNotNull(bitmaps.poll(5, TimeUnit.SECONDS)) { "Preview did not finish loading" }
    }

    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    private fun withPhotoDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "preview-test-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    private fun writeJpeg(file: File, width: Int, height: Int, color: (Int, Int) -> Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, color(x, y))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun assertColorClose(expected: Int, actual: Int) {
        assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 25)
        assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 25)
        assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 25)
    }
}
