package com.karen_yao.chinesetravel.debug

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.databinding.ActivityDebugToolsBinding
import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider
import com.karen_yao.chinesetravel.shared.utils.TestDataUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Developer-only entry point for fixture, OCR, EXIF, and location checks. */
class DebugToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugToolsBinding
    private val repository by lazy { TravelRepository(AppDatabase.getDatabase(this)) }
    private var spinnerInitialized = false
    private var pendingDeviceLocationTest = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!pendingDeviceLocationTest) return@registerForActivityResult
        pendingDeviceLocationTest = false
        if (results.values.any { it }) {
            runCurrentDeviceLocationTest()
        } else {
            showStatus(getString(R.string.debug_location_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
        setupTestSelector()
    }

    private fun setupTestSelector() {
        ArrayAdapter.createFromResource(
            this,
            R.array.debug_test_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerTestFeatures.adapter = adapter
        }

        binding.spinnerTestFeatures.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnerInitialized) {
                    spinnerInitialized = true
                    return
                }

                when (position) {
                    1 -> runRealImagePipelineTest()
                    2 -> testPhotoLocations()
                    3 -> testCurrentDeviceLocation()
                    4 -> runOcrSample("IMG_3950.JPG")
                    5 -> runOcrSample("TEST2.png")
                }
                if (position != 0) parent?.setSelection(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun runRealImagePipelineTest() {
        runDebugTask {
            val summary = TestDataUtils.exportAndTestImages(applicationContext, repository)
            getString(
                R.string.debug_pipeline_summary,
                summary.inserted,
                summary.processed,
                summary.located,
                summary.unlocated,
                summary.failed
            )
        }
    }

    private fun testPhotoLocations() {
        runDebugTask {
            val summary = TestDataUtils.testPhotoLocations(applicationContext, repository)
            getString(
                R.string.debug_photo_summary,
                summary.located,
                summary.unlocated,
                summary.failed
            )
        }
    }

    private fun testCurrentDeviceLocation() {
        if (DeviceLocationProvider.hasLocationPermission(this)) {
            runCurrentDeviceLocationTest()
        } else {
            pendingDeviceLocationTest = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun runCurrentDeviceLocationTest() {
        runDebugTask {
            when (val result = DeviceLocationProvider.getCurrentLocation(applicationContext)) {
                is DeviceLocationProvider.Result.Success -> {
                    val location = result.location
                    val record = TestDataUtils.createDeviceLocationTestRecord(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        isMock = result.isMock
                    )
                    repository.replaceSnapsByIds(
                        idsToReplace = TestDataUtils.LEGACY_MAP_FIXTURE_IDS +
                            TestDataUtils.DEVICE_LOCATION_TEST_ID,
                        replacements = listOf(record)
                    )
                    val source = if (result.isMock) {
                        getString(R.string.debug_location_source_simulated)
                    } else {
                        getString(R.string.debug_location_source_device)
                    }
                    getString(R.string.debug_location_saved, source)
                }
                is DeviceLocationProvider.Result.Unavailable -> getString(
                    R.string.debug_location_unavailable,
                    result.reason.name
                )
            }
        }
    }

    private fun runOcrSample(imageName: String) {
        lifecycleScope.launch {
            setBusy(true)
            try {
                val result = TestDataUtils.recognizeOcrSample(applicationContext, imageName)
                setBusy(false)
                showOcrSelection(result)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                setBusy(false)
                showStatus(getString(R.string.debug_ocr_failed, imageName))
            }
        }
    }

    private fun showOcrSelection(result: TestDataUtils.OcrSampleResult) {
        if (result.detectedLines.isEmpty()) {
            showStatus(getString(R.string.debug_no_text, result.imageName))
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.debug_select_ocr_line, result.imageName))
            .setItems(result.detectedLines.toTypedArray()) { _, index ->
                saveOcrSelection(result, result.detectedLines[index])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveOcrSelection(result: TestDataUtils.OcrSampleResult, selectedText: String) {
        runDebugTask {
            TestDataUtils.saveOcrSampleSelection(
                applicationContext,
                repository,
                result,
                selectedText
            )
            getString(R.string.debug_ocr_saved, selectedText)
        }
    }

    private fun runDebugTask(block: suspend () -> String) {
        lifecycleScope.launch {
            setBusy(true)
            try {
                showStatus(block())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showStatus(getString(R.string.debug_test_failed))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progressIndicator.isVisible = busy
        binding.spinnerTestFeatures.isEnabled = !busy
    }

    private fun showStatus(message: String) {
        binding.tvStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
