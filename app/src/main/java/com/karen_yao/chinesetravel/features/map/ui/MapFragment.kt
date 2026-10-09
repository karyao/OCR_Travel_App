package com.karen_yao.chinesetravel.features.map.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.navigation.travelNavigator
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
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
        val host = requireActivity() as? MapDependenciesOwner
            ?: error("MapFragment host must provide map dependencies")
        ViewModelProvider(this, host.createMapViewModelFactory())[MapViewModel::class.java]
    }

    private var mapView: MapView? = null
    private var zoomControls: MapZoomControls? = null

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
            travelNavigator.closeMap()
        }
    }

    private fun setupMap(view: View) {
        val map = view.findViewById<MapView>(R.id.mapView)
        mapView = map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        zoomControls = MapZoomControls.attach(view, map)

        // Default view: zoomed out to show all of China
        val mapController = map.controller
        mapController.setZoom(5.0)
        mapController.setCenter(GeoPoint(35.0, 105.0))
    }

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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(view, it) }
            }
        }
    }

    private fun render(view: View, state: MapUiState) {
        val map = mapView ?: return
        val hasPins = state.pins.isNotEmpty()
        view.findViewById<View>(R.id.emptyStateLayout).visibility = if (hasPins) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.mapEmptyScroll).visibility = if (hasPins) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.mapLoadingIndicator).visibility = if (state.isLoading) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.tvMapStateIcon).visibility = if (state.isLoading) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.tvMapStateTitle).setText(when {
            state.isLoading -> R.string.map_loading
            state.loadFailed -> R.string.map_load_error_title
            else -> R.string.map_empty_title
        })
        view.findViewById<TextView>(R.id.tvMapStateMessage).apply {
            setText(if (state.loadFailed) R.string.map_load_error_message else R.string.map_empty_message)
            visibility = if (state.isLoading) View.GONE else View.VISIBLE
        }
        map.visibility = if (hasPins) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.tvOsmAttribution).visibility = if (hasPins) View.VISIBLE else View.GONE
        if (hasPins) zoomControls?.show() else zoomControls?.hide()
        placeMarkers(map, state.pins)
    }

    /** Maps already-prepared UI data to Android map widgets. */
    private fun placeMarkers(map: MapView, pins: List<MapPin>) {
        map.overlays.clear()
        val geoPoints = pins.map { pin ->
            val point = GeoPoint(pin.latitude, pin.longitude)
            val marker = Marker(map)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = pin.title
            marker.snippet = pin.snippet
            map.overlays.add(marker)
            point
        }
        when {
            geoPoints.size == 1 -> {
                map.controller.setZoom(15.0)
                map.controller.setCenter(geoPoints.first())
            }
            geoPoints.size > 1 -> {
                val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
                map.post {
                    // A queued fit must not target a detached or replaced view.
                    if (mapView === map) map.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), true)
                }
            }
        }
        map.invalidate()
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
        zoomControls?.dispose()
        zoomControls = null
        map?.onDetach()
        mapView = null
        super.onDestroyView()
    }
}
