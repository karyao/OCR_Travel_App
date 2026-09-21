package com.karen_yao.chinesetravel.features.map.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.shared.extensions.repo
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Fragment displaying a map with pins for all captured snaps that have GPS coordinates.
 * Tapping a pin shows a popup with the Chinese text, Pinyin pronunciation,
 * English translation, and address.
 */
class MapFragment : Fragment(R.layout.fragment_map) {

    private val viewModel by lazy {
        ViewModelProvider(this, MapViewModelFactory(repo()))[MapViewModel::class.java]
    }

    private var mapView: MapView? = null
    private var zoomControls: View? = null
    private var zoomInButton: MaterialButton? = null
    private var zoomOutButton: MaterialButton? = null
    private var zoomMapListener: MapListener? = null
    private var fadeZoomControlsRunnable: Runnable? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Set the user agent before fragment_map inflates and creates the MapView.
        Configuration.getInstance().userAgentValue = context.packageName
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader(view)
        setupMap(view)
        setupAttribution(view)
        observeSnaps(view)
    }

    private fun setupHeader(view: View) {
        val headerLayout = view.findViewById<View>(R.id.headerLayout)
        val backButton = headerLayout.findViewById<Button>(R.id.btnBack)
        val titleText = headerLayout.findViewById<TextView>(R.id.tvHeaderTitle)
        val rightText = headerLayout.findViewById<TextView>(R.id.tvHeaderRight)

        titleText.text = "🗺️ Travel Map"
        rightText.visibility = View.GONE

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupMap(view: View) {
        val map = view.findViewById<MapView>(R.id.mapView)
        mapView = map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setupZoomControls(view, map)

        // Default view: zoomed out to show all of China
        val mapController = map.controller
        mapController.setZoom(5.0)
        mapController.setCenter(GeoPoint(35.0, 105.0))
    }

    private fun setupZoomControls(view: View, map: MapView) {
        val controls = view.findViewById<View>(R.id.mapZoomControls)
        val zoomIn = view.findViewById<MaterialButton>(R.id.btnZoomIn)
        val zoomOut = view.findViewById<MaterialButton>(R.id.btnZoomOut)

        zoomControls = controls
        zoomInButton = zoomIn
        zoomOutButton = zoomOut
        fadeZoomControlsRunnable = Runnable {
            if (zoomControls?.visibility == View.VISIBLE) {
                zoomButtons().forEach { button ->
                    button.animate()
                        .alpha(ZOOM_CONTROLS_IDLE_ALPHA)
                        .setDuration(ZOOM_CONTROLS_FADE_DURATION_MS)
                        .start()
                }
            }
        }

        zoomIn.setOnClickListener {
            map.controller.zoomIn()
            updateZoomButtonStates(map)
            revealZoomControls()
        }
        zoomOut.setOnClickListener {
            map.controller.zoomOut()
            updateZoomButtonStates(map)
            revealZoomControls()
        }

        map.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> revealZoomControls(scheduleFade = false)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleZoomControlsFade()
            }
            false
        }

        zoomMapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                revealZoomControls()
                return false
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                updateZoomButtonStates(map)
                revealZoomControls()
                return false
            }
        }.also(map::addMapListener)

        updateZoomButtonStates(map)
    }

    private fun updateZoomButtonStates(map: MapView) {
        zoomInButton?.isEnabled = map.canZoomIn()
        zoomOutButton?.isEnabled = map.canZoomOut()
    }

    private fun revealZoomControls(scheduleFade: Boolean = true) {
        val controls = zoomControls ?: return
        val fadeRunnable = fadeZoomControlsRunnable ?: return

        controls.removeCallbacks(fadeRunnable)
        zoomButtons().forEach { button ->
            button.animate().cancel()
            button.alpha = 1f
        }

        if (scheduleFade && controls.visibility == View.VISIBLE) {
            controls.postDelayed(fadeRunnable, ZOOM_CONTROLS_FADE_DELAY_MS)
        }
    }

    private fun scheduleZoomControlsFade() {
        val controls = zoomControls ?: return
        val fadeRunnable = fadeZoomControlsRunnable ?: return

        controls.removeCallbacks(fadeRunnable)
        if (controls.visibility == View.VISIBLE) {
            controls.postDelayed(fadeRunnable, ZOOM_CONTROLS_FADE_DELAY_MS)
        }
    }

    private fun hideZoomControls() {
        val controls = zoomControls ?: return
        fadeZoomControlsRunnable?.let(controls::removeCallbacks)
        zoomButtons().forEach { button ->
            button.animate().cancel()
            button.alpha = 1f
        }
        controls.visibility = View.GONE
    }

    private fun showZoomControls(map: MapView) {
        zoomControls?.visibility = View.VISIBLE
        updateZoomButtonStates(map)
        revealZoomControls()
    }

    private fun zoomButtons(): List<MaterialButton> =
        listOfNotNull(zoomInButton, zoomOutButton)

    private fun setupAttribution(view: View) {
        view.findViewById<TextView>(R.id.tvOsmAttribution).setOnClickListener {
            val copyrightUrl = getString(R.string.osm_copyright_url)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, copyrightUrl.toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    R.string.osm_copyright_open_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun observeSnaps(view: View) {
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyStateLayout)
        val attribution = view.findViewById<TextView>(R.id.tvOsmAttribution)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snapsWithLocation.collect { snaps ->
                    val map = mapView ?: return@collect
                    if (snaps.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        map.visibility = View.GONE
                        attribution.visibility = View.GONE
                        hideZoomControls()
                    } else {
                        emptyState.visibility = View.GONE
                        map.visibility = View.VISIBLE
                        attribution.visibility = View.VISIBLE
                        showZoomControls(map)
                        placeMarkers(map, snaps)
                    }
                }
            }
        }
    }

    /**
     * Clears existing markers and places a new pin for each snap.
     * Auto-zooms the map to fit all markers into view.
     */
    private fun placeMarkers(map: MapView, snaps: List<PlaceSnap>) {
        map.overlays.clear()

        val geoPoints = mutableListOf<GeoPoint>()

        for (snap in snaps) {
            val lat = snap.lat ?: continue
            val lng = snap.longitude ?: continue
            val point = GeoPoint(lat, lng)
            geoPoints.add(point)

            val marker = Marker(map)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Title = Chinese text (shown bold at top of popup)
            marker.title = snap.nameCn

            // Snippet = Pinyin + Translation + Address (shown in popup body)
            marker.snippet = buildSnippet(snap)

            map.overlays.add(marker)
        }

        // Auto-zoom to fit all markers
        when {
            geoPoints.size == 1 -> {
                map.controller.setZoom(15.0)
                map.controller.setCenter(geoPoints.first())
            }
            geoPoints.size > 1 -> {
                val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
                map.post {
                    map.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), true)
                }
            }
        }

        map.invalidate()
    }

    /**
     * Builds the info-window body text with Pinyin, English translation, and address.
     */
    private fun buildSnippet(snap: PlaceSnap): String {
        return buildString {
            if (snap.namePinyin.isNotBlank()) {
                append("📖 ${snap.namePinyin}")
            }
            if (snap.translation.isNotBlank()) {
                append("\n🌐 ${snap.translation}")
            }
            if (!snap.address.isNullOrBlank()) {
                append("\n📍 ${snap.address}")
            }
        }
    }

    // osmdroid requires resume/pause lifecycle calls to manage tile downloads
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        val map = mapView
        map?.setOnTouchListener(null)
        zoomMapListener?.let { map?.removeMapListener(it) }
        zoomControls?.let { controls ->
            fadeZoomControlsRunnable?.let(controls::removeCallbacks)
        }
        zoomButtons().forEach { it.animate().cancel() }
        zoomMapListener = null
        fadeZoomControlsRunnable = null
        zoomInButton = null
        zoomOutButton = null
        zoomControls = null
        map?.onDetach()
        mapView = null
        super.onDestroyView()
    }

    private companion object {
        const val ZOOM_CONTROLS_FADE_DELAY_MS = 2_000L
        const val ZOOM_CONTROLS_FADE_DURATION_MS = 250L
        const val ZOOM_CONTROLS_IDLE_ALPHA = 0.4f
    }
}
