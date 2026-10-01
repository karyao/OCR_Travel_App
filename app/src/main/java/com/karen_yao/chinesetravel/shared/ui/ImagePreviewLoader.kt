package com.karen_yao.chinesetravel.shared.ui

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One preview per view lifetime. Bitmaps never enter the ViewModel or OCR workflow. */
internal class ImagePreviewLoader(
    imageView: ImageView,
    owner: LifecycleOwner,
    private val decoder: PreviewDecoder = BitmapPreviewDecoder(imageView.context.applicationContext.assets),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DefaultLifecycleObserver {
    private var imageView: ImageView? = imageView
    private val lifecycle = owner.lifecycle
    private val scope = owner.lifecycleScope
    private var source: PreviewSource? = null
    private var completed: Request? = null
    private var pending: Request? = null
    private var job: Job? = null
    private var generation = 0L
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }

    init {
        imageView.addOnLayoutChangeListener(layoutListener)
        lifecycle.addObserver(this)
    }

    @MainThread fun loadAsset(assetName: String) = load(PreviewSource.Asset(assetName))
    @MainThread fun loadFile(file: File) = load(PreviewSource.Photo(file))

    private fun load(next: PreviewSource) {
        if (imageView == null) return
        if (source != next) {
            cancelPending()
            source = next
            completed = null
            imageView?.setImageDrawable(null)
        }
        refresh()
    }

    override fun onStart(owner: LifecycleOwner) = refresh()
    override fun onStop(owner: LifecycleOwner) = cancelPending()

    override fun onDestroy(owner: LifecycleOwner) {
        cancelPending()
        imageView?.removeOnLayoutChangeListener(layoutListener)
        imageView?.setImageDrawable(null)
        imageView = null
        source = null
        completed = null
        lifecycle.removeObserver(this)
    }

    private fun request(): Request? {
        val view = imageView ?: return null
        val source = source ?: return null
        val width = view.width - view.paddingLeft - view.paddingRight
        val height = view.height - view.paddingTop - view.paddingBottom
        if (width <= 0 || height <= 0) return null
        val scale = if (view.scaleType == ImageView.ScaleType.CENTER_CROP) PreviewScale.CENTER_CROP
            else PreviewScale.FIT_CENTER
        return Request(source, PreviewSize(width, height), scale)
    }

    private fun refresh() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val request = request()
        if (request == null) {
            cancelPending()
            return
        }
        if (request == completed || (request == pending && job?.isActive == true)) return
        cancelPending()
        pending = request
        val token = generation
        job = scope.launch {
            var owned: Bitmap? = null
            try {
                withContext(ioDispatcher) {
                    // A cancelled native decode can finish, but cannot overlap another decode.
                    decodeMutex.withLock {
                        currentCoroutineContext().ensureActive()
                        owned = decoder.decode(request.source, request.size, request.scale)
                        if (!currentCoroutineContext().isActive) {
                            owned?.recycle()
                            owned = null
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (isCurrent(request, token)) {
                    imageView?.setImageBitmap(owned)
                    owned = null // The ImageView now owns the displayed bitmap.
                    completed = request
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrent(request, token)) {
                    Log.w("ImagePreviewLoader", "Could not load image preview", error)
                    imageView?.setImageResource(android.R.drawable.ic_menu_camera)
                    completed = request
                }
            } finally {
                // Covers cancellation during the dispatcher handoff as well as stale results.
                owned?.recycle()
                if (token == generation) {
                    pending = null
                    job = null
                }
            }
        }
    }

    private fun isCurrent(request: Request, token: Long) =
        token == generation && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && request == request()

    private fun cancelPending() {
        generation++
        job?.cancel()
        job = null
        pending = null
    }

    private data class Request(val source: PreviewSource, val size: PreviewSize, val scale: PreviewScale)
    private companion object { val decodeMutex = Mutex() }
}
