package com.karen_yao.chinesetravel.features.capture.ui

import android.Manifest
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.navigation.travelNavigator
import kotlinx.coroutines.launch

/** Android integration only; CaptureViewModel owns the workflow and image lifetime. */
class CaptureFragment : Fragment(R.layout.fragment_capture) {
    private val dependencies by lazy {
        val host = requireActivity() as? CaptureDependenciesOwner
            ?: error("CaptureFragment host must provide capture dependencies")
        host.createCaptureDependencies()
    }
    private val viewModel by lazy {
        ViewModelProvider(this, CaptureViewModelFactory(dependencies.createWorkflow))[CaptureViewModel::class.java]
    }
    private var generation = 0L
    private var cameraSession: CaptureCameraSession? = null
    private var cameraPermissionGeneration: Long? = null
    private var cameraPermissionRequested = false
    private var locationRequest: CaptureEffectEnvelope? = null
    private var galleryRequest: CaptureEffectEnvelope? = null
    private var noTextDialog: AlertDialog? = null
    private var activeToast: Toast? = null
    private var lastTapTime = 0L

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        galleryRequest?.let {
            viewModel.onEvent(it.viewGeneration, CaptureEvent.GalleryResult(checkNotNull(it.operationId), uri?.toString()))
        }
        galleryRequest = null
    }
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val requestedGeneration = cameraPermissionGeneration
            cameraPermissionGeneration = null
            if (requestedGeneration == generation && view != null) {
                if (granted) {
                    cameraSession?.start()
                } else send(CaptureEvent.CameraFailed("Camera permission is required to take photos"))
            }
        }
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            locationRequest?.let {
                viewModel.onEvent(it.viewGeneration, CaptureEvent.PermissionResult(
                    checkNotNull(it.operationId), results.values.any { granted -> granted }
                ))
            }
            locationRequest = null
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        generation = viewModel.attachView()
        cameraPermissionRequested = false
        val viewGeneration = generation
        cameraSession = CaptureCameraSession(
            dependencies.camera, dependencies.permissions, requireContext(),
            view.findViewById(R.id.previewView), viewLifecycleOwner
        ) { event ->
            viewModel.onEvent(viewGeneration, event)
            if (this.view != null) render(viewModel.uiState.value)
        }
        val header = view.findViewById<View>(R.id.headerLayout)
        header.findViewById<TextView>(R.id.tvHeaderTitle).text = "📸 Capture Chinese Text"
        header.findViewById<TextView>(R.id.tvHeaderRight).visibility = View.GONE
        header.findViewById<Button>(R.id.btnBack).setOnClickListener {
            travelNavigator.goBack()
        }
        view.findViewById<Button>(R.id.btnShoot).setOnClickListener {
            send(CaptureEvent.CaptureTapped(dependencies.permissions.hasLocationPermission(requireContext())))
        }
        view.findViewById<Button>(R.id.btnGallery).setOnClickListener { send(CaptureEvent.GalleryTapped) }
        view.findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener { send(CaptureEvent.SwitchCameraTapped) }
        view.findViewById<PreviewView>(R.id.previewView).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 300) send(CaptureEvent.SwitchCameraTapped)
                lastTapTime = now
            }
            false
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect {
                    render(it)
                    drainEffects()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        send(CaptureEvent.Resumed)
        cameraSession?.resume()
        if (!dependencies.permissions.hasCameraPermission(requireContext()) && !cameraPermissionRequested) {
            cameraPermissionRequested = true
            cameraPermissionGeneration = generation
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        cancelToast()
        cameraSession?.pause { send(CaptureEvent.Paused) } ?: send(CaptureEvent.Paused)
        super.onPause()
    }

    override fun onDestroyView() {
        cameraSession?.dispose()
        cameraSession = null
        cancelToast()
        send(CaptureEvent.ViewDestroyed)
        noTextDialog?.setOnDismissListener(null)
        noTextDialog?.dismiss()
        noTextDialog = null
        super.onDestroyView()
    }

    private fun send(event: CaptureEvent) {
        viewModel.onEvent(generation, event)
        // Keep click handling and disabled controls atomic, even before the collector resumes.
        if (view != null) render(viewModel.uiState.value)
    }

    private fun render(state: CaptureUiState) {
        val root = view ?: return
        listOf(R.id.btnShoot, R.id.btnGallery, R.id.btnSwitchCamera).forEach {
            root.findViewById<Button>(it).isEnabled = state.controlsEnabled
        }
        root.findViewById<Button>(R.id.btnSwitchCamera).text = if (state.isBackCamera) "📷" else "🤳"
        val progressText = when (state.state) {
            CaptureState.GettingLocation -> R.string.capture_getting_location
            CaptureState.WaitingForCamera -> R.string.capture_waiting_for_camera
            CaptureState.Capturing -> R.string.capture_taking_photo
            CaptureState.ImportingImage -> R.string.capture_loading_image
            CaptureState.RecognizingText -> R.string.capture_reading_text
            CaptureState.Saving -> R.string.capture_saving
            else -> null
        }
        root.findViewById<TextView>(R.id.tvCaptureStatus).apply {
            val label = progressText?.let { getString(it) }.orEmpty()
            if (text.toString() != label) text = label
            visibility = if (progressText == null) View.GONE else View.VISIBLE
        }
    }

    /** Main-thread execution and immediate acknowledgement prevent replay on collector restart. */
    private fun drainEffects() {
        while (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val envelope = viewModel.uiState.value.effects.firstOrNull() ?: return
            if (envelope.viewGeneration != generation) return
            val effect = envelope.effect
            if (effect is CaptureEffect.OpenTextSelection || effect == CaptureEffect.NavigateBack ||
                effect == CaptureEffect.NavigateHome) {
                if (travelNavigator.isStateSaved) return
            }
            try {
                execute(envelope)
                viewModel.onEvent(envelope.viewGeneration, CaptureEvent.EffectHandled(envelope.id))
            } catch (error: Exception) {
                viewModel.onEvent(envelope.viewGeneration, CaptureEvent.EffectFailed(envelope.id, error.message.orEmpty()))
            }
        }
    }

    private fun execute(command: CaptureEffectEnvelope) {
        when (val effect = command.effect) {
            is CaptureEffect.Message -> {
                cancelToast()
                activeToast = Toast.makeText(requireContext(), effect.text, Toast.LENGTH_SHORT).also { it.show() }
            }
            CaptureEffect.RequestLocationPermission -> {
                locationRequest = command
                locationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            CaptureEffect.OpenGallery -> {
                cancelToast()
                galleryRequest = command
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            CaptureEffect.RestartCamera -> cameraSession?.start()
            CaptureEffect.SwitchCamera -> cameraSession?.switchCamera()
            is CaptureEffect.TakePhoto -> {
                val model = viewModel
                val id = checkNotNull(command.operationId)
                dependencies.camera.takePhoto(effect.file, effect.location?.toAndroidLocation(),
                    ContextCompat.getMainExecutor(requireContext()),
                    onSaved = { model.onEvent(command.viewGeneration, CaptureEvent.CameraSaved(id, effect.file)) },
                    onError = { model.onEvent(command.viewGeneration, CaptureEvent.CameraCaptureFailed(id, effect.file, it)) })
            }
            is CaptureEffect.OpenTextSelection -> travelNavigator.openTextSelection(
                effect.lines, effect.file.absolutePath, "", deleteImageIfUnsaved = true
            )
            CaptureEffect.ShowNoTextDialog -> showNoTextDialog(command)
            CaptureEffect.NavigateBack -> travelNavigator.goBack()
            CaptureEffect.NavigateHome -> travelNavigator.openHome()
        }
    }

    private fun cancelToast() {
        activeToast?.cancel()
        activeToast = null
    }

    private fun showNoTextDialog(command: CaptureEffectEnvelope) {
        fun choose(choice: NoTextAction) = viewModel.onEvent(command.viewGeneration,
            CaptureEvent.NoTextChoice(checkNotNull(command.operationId), choice))
        noTextDialog = AlertDialog.Builder(requireContext())
            .setTitle("🔍 No Text Detected")
            .setMessage("We couldn't find any text in this image. This often happens when:\n\n" +
                "• The image is too wide or includes too much background\n" +
                "• The text is too small or blurry\n" +
                "• The lighting is poor\n\n" +
                "Try taking a more focused photo with just the Chinese text visible.")
            .setPositiveButton("📸 Try Again") { _, _ -> choose(NoTextAction.TRY_AGAIN) }
            .setNegativeButton("📁 Choose from Gallery") { _, _ -> choose(NoTextAction.GALLERY) }
            .setNeutralButton("🏠 Back to Home") { _, _ -> choose(NoTextAction.HOME) }
            .create().also { dialog ->
                dialog.setOnDismissListener { choose(NoTextAction.DISMISS) }
                dialog.show()
            }
    }
}
