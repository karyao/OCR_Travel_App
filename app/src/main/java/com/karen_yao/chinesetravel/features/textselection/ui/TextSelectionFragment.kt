package com.karen_yao.chinesetravel.features.textselection.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.repository.TravelRepositoryOwner
import com.karen_yao.chinesetravel.databinding.FragmentTextSelectionBinding
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.shared.ui.CardSpacingDecoration
import java.io.File
import kotlinx.coroutines.launch

/** Displays ViewModel state; Android preview loading and navigation stay in the Fragment. */
class TextSelectionFragment : Fragment(R.layout.fragment_text_selection) {
    private var binding: FragmentTextSelectionBinding? = null
    private var adapter: TextOptionAdapter? = null
    private var backCallback: OnBackPressedCallback? = null
    private val viewModel by lazy {
        ViewModelProvider(this, TextSelectionViewModelFactory {
            when (val host = requireActivity()) {
                is TextSelectionDependenciesOwner -> host.createTextSelectionDependencies()
                is TravelRepositoryOwner -> productionTextSelectionDependencies(host.applicationContext, host.repository)
                else -> error("TextSelectionFragment host must provide selection dependencies")
            }
        })[TextSelectionViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = FragmentTextSelectionBinding.bind(view)
        binding = views
        views.headerLayout.tvHeaderTitle.setText(R.string.text_selection_title)
        views.headerLayout.tvHeaderRight.visibility = View.GONE
        views.headerLayout.btnBack.setOnClickListener { viewModel.cancel() }
        views.btnCancel.setOnClickListener { viewModel.cancel() }
        views.btnConfirmSelection.setOnClickListener { viewModel.confirm() }
        val state = viewModel.uiState.value
        adapter = TextOptionAdapter(state.lines, viewModel::select)
        views.rvTextOptions.layoutManager = LinearLayoutManager(requireContext())
        views.rvTextOptions.addItemDecoration(CardSpacingDecoration(resources.getDimensionPixelSize(R.dimen.list_gap)))
        views.rvTextOptions.adapter = adapter
        File(state.imagePath).takeIf { state.imagePath.isNotBlank() && it.exists() }?.let {
            ImageProcessor().loadImageWithRotation(views.ivCapturedImage, it.absolutePath)
        }
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { viewModel.cancel() }
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collect {
                    render(it)
                    drainCommands()
                }
            }
        }
    }

    private fun render(state: TextSelectionUiState) {
        val views = binding ?: return
        adapter?.render(state.selectedIndex, state.selectionEnabled)
        views.btnConfirmSelection.isEnabled = state.confirmEnabled
        views.btnConfirmSelection.setText(
            if (state.status == TextSelectionStatus.SAVING) R.string.text_selection_saving
            else R.string.text_selection_confirm
        )
        views.btnCancel.isEnabled = state.exitEnabled
        views.headerLayout.btnBack.isEnabled = state.exitEnabled
    }

    private fun drainCommands() {
        while (binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val command = viewModel.uiState.value.commands.firstOrNull() ?: return
            val navigation = command.effect is TextSelectionEffect.SavedAndGoHome ||
                command.effect == TextSelectionEffect.GoBack
            if (navigation && parentFragmentManager.isStateSaved) return
            when (val effect = command.effect) {
                TextSelectionEffect.ShowSaveError -> Toast.makeText(
                    requireContext(), R.string.text_selection_save_unknown, Toast.LENGTH_LONG
                ).show()
                TextSelectionEffect.ShowInvalidInput -> Toast.makeText(
                    requireContext(), R.string.text_selection_invalid_input, Toast.LENGTH_LONG
                ).show()
                is TextSelectionEffect.SavedAndGoHome -> {
                    returnHome()
                    Toast.makeText(requireContext(), getString(R.string.text_selection_saved, effect.count), Toast.LENGTH_SHORT).show()
                }
                TextSelectionEffect.GoBack -> {
                    // Delegate once without recursively invoking this callback.
                    backCallback?.isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
            // Synchronous acknowledgement avoids replay when collection resumes.
            viewModel.acknowledge(command.id)
        }
    }

    private fun returnHome() {
        val manager = parentFragmentManager
        val captureEntry = (0 until manager.backStackEntryCount).lastOrNull {
            manager.getBackStackEntryAt(it).name == CaptureFragment.BACK_STACK_NAME
        }
        if (captureEntry != null) {
            manager.popBackStack(manager.getBackStackEntryAt(captureEntry).id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } else {
            // Standalone hosts and older unnamed stacks have no Home return marker.
            manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            manager.beginTransaction().setReorderingAllowed(true)
                .replace(R.id.container, HomeFragment()).commit()
        }
    }

    override fun onDestroyView() {
        binding?.rvTextOptions?.adapter = null
        adapter = null
        binding = null
        backCallback = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(
            detectedTexts: List<String>,
            imagePath: String,
            selectedText: String,
            deleteImageIfUnsaved: Boolean = false
        ) = TextSelectionFragment().apply {
            arguments = Bundle().apply {
                putStringArray(TextSelectionViewModel.ARG_LINES, detectedTexts.toTypedArray())
                putString(TextSelectionViewModel.ARG_IMAGE_PATH, imagePath)
                putString(TextSelectionViewModel.ARG_SELECTED_TEXT, selectedText)
                putBoolean(TextSelectionViewModel.ARG_OWNED, deleteImageIfUnsaved)
            }
        }
    }
}
