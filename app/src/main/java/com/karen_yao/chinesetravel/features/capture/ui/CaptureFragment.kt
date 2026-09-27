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
import com.karen_yao.chinesetravel.MainActivity
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment
import kotlinx.coroutines.launch

/** Android integration only; CaptureViewModel owns the workflow and image lifetime. */
class CaptureFragment : Fragment(R.layout.fragment_capture) {
    private val dependencies by lazy {
        when (val host = requireActivity()) {
            is MainActivity -> host.createCaptureDependencies()
            is CaptureDependenciesOwner -> host.createCaptureDependencies()
            else -> error("CaptureFragment host must provide capture dependencies")
        }
    }
    private val viewModel by lazy {
        ViewModelProvider(this, CaptureViewModelFactory(dependencies.createWorkflow))[CaptureViewModel::class.java]
    }
    private var generation = 0L
    private var cameraBinding = 0L
    private var cameraPermissionGeneration: Long? = null
    private var cameraPermissionRequested = false
    private var locationRequest: CaptureEffectEnvelope? = null
    private var galleryRequest: CaptureEffectEnvelope? = null
    private var noTextDialog: AlertDialog? = null
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
                    if (isResumed) bindCamera()
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
        val header = view.findViewById<View>(R.id.headerLayout)
        header.findViewById<TextView>(R.id.tvHeaderTitle).text = "📸 Capture Chinese Text"
        header.findViewById<TextView>(R.id.tvHeaderRight).visibility = View.GONE
        header.findViewById<Button>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
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
        if (!dependencies.permissions.hasCameraPermission(requireContext()) && !cameraPermissionRequested) {
            cameraPermissionRequested = true
            cameraPermissionGeneration = generation
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else bindCamera()
    }

    override fun onPause() {
        cameraBinding++
        send(CaptureEvent.Paused)
        dependencies.camera.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        cameraBinding++
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
    }

    private fun bindCamera(switch: Boolean = false) {
        if (!isResumed || !dependencies.permissions.hasCameraPermission(requireContext())) return
        val preview = view?.findViewById<PreviewView>(R.id.previewView) ?: return
        if (!dependencies.permissions.isCameraAvailable(requireContext())) {
            send(CaptureEvent.CameraFailed("Camera not available on this device"))
            return
        }
        val viewId = generation
        val bindingId = ++cameraBinding
        fun isCurrent() = viewId == generation && bindingId == cameraBinding && view != null && isResumed
        val onError: (String) -> Unit = { if (isCurrent()) send(CaptureEvent.CameraFailed(it)) }
        val onReady: () -> Unit = {
            if (isCurrent()) {
                val back = dependencies.camera.isBackCamera()
                send(CaptureEvent.CameraReady(back, if (switch)
                    "Switched to ${if (back) "back" else "front"} camera"
                    else "Camera ready! Point at Chinese text"))
            }
        }
        if (switch) dependencies.camera.switch(requireContext(), preview, viewLifecycleOwner, onError, onReady)
        else dependencies.camera.start(requireContext(), preview, viewLifecycleOwner, onError, onReady)
    }

    /** Main-thread execution and immediate acknowledgement prevent replay on collector restart. */
    private fun drainEffects() {
        while (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val envelope = viewModel.uiState.value.effects.firstOrNull() ?: return
            if (envelope.viewGeneration != generation) return
            val effect = envelope.effect
            if (effect is CaptureEffect.OpenTextSelection || effect == CaptureEffect.NavigateBack ||
                effect == CaptureEffect.NavigateHome) {
                if (parentFragmentManager.isStateSaved) return
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
            is CaptureEffect.Message -> Toast.makeText(requireContext(), effect.text, Toast.LENGTH_SHORT).show()
            CaptureEffect.RequestLocationPermission -> {
                locationRequest = command
                locationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            CaptureEffect.OpenGallery -> {
                galleryRequest = command
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            CaptureEffect.RestartCamera -> bindCamera()
            CaptureEffect.SwitchCamera -> bindCamera(switch = true)
            is CaptureEffect.TakePhoto -> {
                val model = viewModel
                val id = checkNotNull(command.operationId)
                dependencies.camera.takePhoto(effect.file, effect.location?.toAndroidLocation(),
                    ContextCompat.getMainExecutor(requireContext()),
                    onSaved = { model.onEvent(command.viewGeneration, CaptureEvent.CameraSaved(id, effect.file)) },
                    onError = { model.onEvent(command.viewGeneration, CaptureEvent.CameraCaptureFailed(id, effect.file, it)) })
            }
            is CaptureEffect.OpenTextSelection -> parentFragmentManager.beginTransaction()
                .replace(R.id.container, TextSelectionFragment.newInstance(
                    effect.lines, effect.file.absolutePath, "", deleteImageIfUnsaved = true
                )).addToBackStack(null).commit()
            CaptureEffect.ShowNoTextDialog -> showNoTextDialog(command)
            CaptureEffect.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
            CaptureEffect.NavigateHome -> parentFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment()).commit()
        }
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
