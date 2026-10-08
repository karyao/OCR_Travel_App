package com.karen_yao.chinesetravel.features.map.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.annotation.MainThread
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.karen_yao.chinesetravel.R
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView

/**
 * Owns zoom buttons and the map's touch-listener slot for one Fragment view.
 * Dispose before detaching the map; map lifecycle and positioning stay with the host.
 */
@MainThread
// Touch events are observed, never consumed; osmdroid handles gestures and buttons provide clicks.
@SuppressLint("ClickableViewAccessibility")
internal class MapZoomControls private constructor(
    private val map: MapView,
    private val controls: View,
    private val zoomIn: MaterialButton,
    private val zoomOut: MaterialButton
) {
    private val buttons = listOf(zoomIn, zoomOut)
    private var disposed = false
    private val fadeRunnable = Runnable {
        if (!disposed && controls.isVisible) {
            buttons.forEach { button ->
                button.animate()
                    .alpha(IDLE_ALPHA)
                    .setDuration(FADE_DURATION_MS)
                    .start()
            }
        }
    }
    private val mapListener = object : MapListener {
        override fun onScroll(event: ScrollEvent): Boolean {
            reveal()
            return false
        }

        override fun onZoom(event: ZoomEvent): Boolean {
            if (!disposed) {
                updateButtonStates()
                reveal()
            }
            return false
        }
    }

    init {
        zoomIn.setOnClickListener {
            if (!disposed) {
                map.controller.zoomIn()
                updateButtonStates()
                reveal()
            }
        }
        zoomOut.setOnClickListener {
            if (!disposed) {
                map.controller.zoomOut()
                updateButtonStates()
                reveal()
            }
        }
        map.setOnTouchListener { _, event ->
            if (!disposed) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> reveal(fadeAfterIdle = false)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleFade()
                }
            }
            false
        }
        map.addMapListener(mapListener)
        updateButtonStates()
    }

    fun show() {
        if (disposed) return
        controls.isVisible = true
        updateButtonStates()
        reveal()
    }

    fun hide() {
        if (disposed) return
        controls.removeCallbacks(fadeRunnable)
        resetButtonOpacity()
        controls.isVisible = false
    }

    /** Idempotent; callbacks already in flight also become inactive. */
    fun dispose() {
        if (disposed) return
        disposed = true
        zoomIn.setOnClickListener(null)
        zoomOut.setOnClickListener(null)
        map.setOnTouchListener(null)
        map.removeMapListener(mapListener)
        controls.removeCallbacks(fadeRunnable)
        buttons.forEach { it.animate().cancel() }
    }

    private fun updateButtonStates() {
        zoomIn.isEnabled = map.canZoomIn()
        zoomOut.isEnabled = map.canZoomOut()
    }

    private fun reveal(fadeAfterIdle: Boolean = true) {
        if (disposed) return
        controls.removeCallbacks(fadeRunnable)
        resetButtonOpacity()
        if (fadeAfterIdle && controls.isVisible) {
            controls.postDelayed(fadeRunnable, FADE_DELAY_MS)
        }
    }

    private fun resetButtonOpacity() {
        buttons.forEach { button ->
            button.animate().cancel()
            button.alpha = 1f
        }
    }

    private fun scheduleFade() {
        controls.removeCallbacks(fadeRunnable)
        if (controls.isVisible) {
            controls.postDelayed(fadeRunnable, FADE_DELAY_MS)
        }
    }

    companion object {
        private const val FADE_DELAY_MS = 2_000L
        private const val FADE_DURATION_MS = 250L
        private const val IDLE_ALPHA = 0.4f

        /** Attach once per view without changing the controls' initial visibility. */
        fun attach(root: View, map: MapView): MapZoomControls = MapZoomControls(
            map,
            root.findViewById(R.id.mapZoomControls),
            root.findViewById(R.id.btnZoomIn),
            root.findViewById(R.id.btnZoomOut)
        )
    }
}
