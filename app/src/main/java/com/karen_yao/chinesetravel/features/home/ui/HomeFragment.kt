package com.karen_yao.chinesetravel.features.home.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.shared.extensions.repo
import com.karen_yao.chinesetravel.shared.utils.TestDataUtils
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import android.widget.Button

/**
 * Home fragment displaying the list of captured place snaps.
 * Provides navigation to capture functionality.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {
    
    private val viewModel by lazy {
        ViewModelProvider(this, HomeViewModelFactory(repo()))[HomeViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView(view)
        setupFloatingActionButton(view)
        setupTestButton(view)
        testImageForLocation(requireContext())
    }
    
    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler)
        val adapter = SnapsAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snaps.collect { list ->
                Log.d("HomeFragment", "snaps size=${list.size}")
                adapter.submitList(list)
            }
        }
    }
    
    private fun setupFloatingActionButton(view: View) {
        view.findViewById<FloatingActionButton>(R.id.fabCapture).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }
    }
    
    private fun setupTestButton(view: View) {
        // Add a test button for development/testing purposes
        val testButton = view.findViewById<Button>(R.id.btnTest) ?: return
        testButton.setOnClickListener {
            Log.d("HomeFragment", "Running test data export...")
            runTestExport()
        }
    }
    
    private fun runTestExport() {
        // Show immediate feedback
        Log.d("HomeFragment", "Starting test export...")
        
        // Run the test export
        TestDataUtils.exportAndTestImages(requireContext(), repo())
        
        // Show a toast to indicate test is running
        android.widget.Toast.makeText(requireContext(), "Test export started! Check logs for results.", android.widget.Toast.LENGTH_LONG).show()
        
        // Refresh the RecyclerView to show any new test data
        viewLifecycleOwner.lifecycleScope.launch {
            // Wait a moment for the test to complete, then refresh
            kotlinx.coroutines.delay(2000)
            // The RecyclerView will automatically update via the ViewModel's Flow
        }
    }
    
    private fun testImageForLocation(context: Context) {
        val inputStream = context.assets.open("IMG_3849.JPG")
        val exif = ExifInterface(inputStream)

        val latLong = FloatArray(2)
        val hasLocation = exif.getLatLong(latLong)

        if (hasLocation) {
            val latitude = latLong[0]
            val longitude = latLong[1]
            Log.d("EXIF", "✅ Image has location: lat=$latitude, lon=$longitude")

            val mapsUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            Log.d("EXIF", "🌍 Google Maps link: $mapsUrl")
        } else {
            Log.d("EXIF", "❌ No location data found in EXIF.")
        }

        inputStream.close()
    }
}
