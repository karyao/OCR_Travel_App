package com.karen_yao.chinesetravel.features.home.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.databinding.FragmentHomeBinding
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.map.ui.MapFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import com.karen_yao.chinesetravel.shared.extensions.repo
import kotlinx.coroutines.launch

/** Displays the saved travel collection and forwards user actions. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = requireNotNull(_binding)

    private val viewModel by lazy {
        ViewModelProvider(this, HomeViewModelFactory(repo()))[HomeViewModel::class.java]
    }

    private lateinit var snapsAdapter: SnapsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        setupHeader()
        setupCollection()
        setupNavigation()
        observeHome()
    }

    private fun setupHeader() = with(binding.headerLayout) {
        tvHeaderTitle.setText(R.string.home_title)
        tvHeaderRight.isVisible = true
        btnHeaderOverflow.isVisible = true
        btnBack.setOnClickListener { navigateToWelcome() }
        btnHeaderOverflow.setOnClickListener(::showOverflowMenu)
    }

    private fun setupCollection() {
        snapsAdapter = SnapsAdapter(::handleSnapAction)
        binding.recycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = snapsAdapter
        }
    }

    private fun setupNavigation() {
        binding.fabCapture.setOnClickListener { navigateToCapture() }
        binding.fabMap.setOnClickListener { navigateToMap() }
    }

    private fun observeHome() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.effects.collect(::handleEffect) }
            }
        }
    }

    private fun render(state: HomeUiState) {
        binding.loadingIndicator.isVisible = state.isLoading
        binding.emptyStateLayout.isVisible = state.isEmpty
        binding.errorStateLayout.isVisible = state.loadFailed
        binding.recycler.isVisible = !state.isLoading && !state.isEmpty && !state.loadFailed

        binding.headerLayout.tvHeaderRight.text = resources.getQuantityString(
            R.plurals.home_snap_count,
            state.snapCount,
            state.snapCount
        )
        binding.headerLayout.btnHeaderOverflow.isEnabled =
            state.snaps.isNotEmpty() && !state.isMutating

        snapsAdapter.actionsEnabled = !state.isMutating
        snapsAdapter.submitList(state.snaps)
    }

    private fun handleEffect(effect: HomeEffect) {
        when (effect) {
            is HomeEffect.ShowMessage -> Snackbar.make(
                binding.root,
                effect.message,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleSnapAction(action: SnapItemAction) {
        when (action) {
            is SnapItemAction.Delete -> showDeleteConfirmation(action.snap)
            is SnapItemAction.OpenMap -> openMap(action.url)
        }
    }

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_home_overflow, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_clear_all -> {
                        showClearConfirmation()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showDeleteConfirmation(snap: PlaceSnap) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.home_delete_title)
            .setMessage(getString(R.string.home_delete_message, snap.nameCn))
            .setPositiveButton(R.string.home_delete_confirm) { _, _ ->
                viewModel.deleteSnap(snap)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showClearConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.home_clear_title)
            .setMessage(R.string.home_clear_message)
            .setPositiveButton(R.string.home_clear_confirm) { _, _ ->
                viewModel.clearAllSnaps()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openMap(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            showMapOpenError()
        } catch (_: SecurityException) {
            showMapOpenError()
        }
    }

    private fun showMapOpenError() {
        Snackbar.make(binding.root, R.string.home_map_open_error, Snackbar.LENGTH_SHORT).show()
    }

    private fun navigateToCapture() = navigateTo(CaptureFragment(), addToBackStack = true)

    private fun navigateToMap() = navigateTo(MapFragment(), addToBackStack = true)

    private fun navigateToWelcome() = navigateTo(WelcomeFragment(), addToBackStack = false)

    private fun navigateTo(destination: Fragment, addToBackStack: Boolean) {
        parentFragmentManager.beginTransaction().apply {
            replace(R.id.container, destination)
            if (addToBackStack) addToBackStack(null)
        }.commit()
    }

    override fun onDestroyView() {
        binding.recycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
