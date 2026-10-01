package com.karen_yao.chinesetravel.features.tutorial.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.widget.NestedScrollView
import com.karen_yao.chinesetravel.shared.ui.ImagePreviewLoader
import kotlinx.coroutines.launch
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment

/**
 * TutorialFragment provides a step-by-step guide on how to use the Chinese Travel app.
 * It showcases the app's features using sample images from assets.
 */
class TutorialFragment : Fragment(R.layout.fragment_tutorial) {

    private val viewModel by lazy { ViewModelProvider(this)[TutorialViewModel::class.java] }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader(view)
        setupNavigation(view)
        val imageLoader = ImagePreviewLoader(view.findViewById(R.id.ivTutorialImage), viewLifecycleOwner)
        // Keep this per view, across collection restarts, without overriding restored scrolling.
        var renderedStepIndex: Int? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (renderedStepIndex != state.index) {
                        render(view, state, imageLoader, resetScroll = renderedStepIndex != null)
                        renderedStepIndex = state.index
                    }
                }
            }
        }
    }
    
    private fun setupHeader(view: View) {
        val headerLayout = view.findViewById<View>(R.id.headerLayout)
        val backButton = headerLayout.findViewById<Button>(R.id.btnBack)
        val titleText = headerLayout.findViewById<TextView>(R.id.tvHeaderTitle)
        val rightText = headerLayout.findViewById<TextView>(R.id.tvHeaderRight)
        
        // Set title and hide right text
        titleText.text = "📚 How to Use Chinese Travel"
        rightText.visibility = View.GONE
        
        // Set up back button
        backButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, WelcomeFragment())
                .commit()
        }
    }

    private fun render(view: View, state: TutorialUiState, imageLoader: ImagePreviewLoader, resetScroll: Boolean) {
        view.findViewById<TextView>(R.id.tvTutorialTitle).setText(state.step.title)
        view.findViewById<TextView>(R.id.tvTutorialDescription).setText(state.step.description)
        imageLoader.loadAsset(state.step.imageAsset)
        view.findViewById<Button>(R.id.btnPrevious).visibility = if (state.canGoPrevious) View.VISIBLE else View.INVISIBLE
        view.findViewById<Button>(R.id.btnNext).visibility = if (state.canGoNext) View.VISIBLE else View.GONE
        view.findViewById<Button>(R.id.btnGetStarted).visibility = if (state.canGoNext) View.GONE else View.VISIBLE
        if (resetScroll) {
            view.findViewById<NestedScrollView>(R.id.tutorialContent).scrollTo(0, 0)
        }
    }

    private fun setupNavigation(view: View) {
        view.findViewById<Button>(R.id.btnPrevious).setOnClickListener { viewModel.previous() }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener { viewModel.next() }

        view.findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            // Navigate to HomeFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment())
                .commit()
        }
    }
}
