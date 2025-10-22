package com.karen_yao.chinesetravel.features.tutorial.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import java.io.File

/**
 * TutorialFragment provides a step-by-step guide on how to use the Chinese Travel app.
 * It showcases the app's features using sample images from assets.
 */
class TutorialFragment : Fragment(R.layout.fragment_tutorial) {

    private var currentStep = 0
    private val tutorialSteps = listOf(
        TutorialStep(
            title = "Welcome to Chinese Travel!",
            description = "Learn how to capture, translate, and explore Chinese text during your travels.",
            imageRes = "chinese_character.jpg",
            stepNumber = 1,
            totalSteps = 4
        ),
        TutorialStep(
            title = "Step 1: Capture Chinese Text",
            description = "Point your camera at any Chinese text - signs, menus, documents, or books. The app will automatically detect and recognize the text using AI.",
            imageRes = "sample1.jpg",
            stepNumber = 2,
            totalSteps = 4
        ),
        TutorialStep(
            title = "Step 2: Get Translation & Pinyin",
            description = "The app will provide you with Pinyin pronunciation and English translation, making Chinese text accessible to you instantly.",
            imageRes = "IMG_3849.JPG",
            stepNumber = 3,
            totalSteps = 4
        ),
        TutorialStep(
            title = "Step 3: Save with Location",
            description = "Your captured text is automatically saved with your current location, creating a personal travel journal of your Chinese learning journey.",
            imageRes = "chinese_character.jpg",
            stepNumber = 4,
            totalSteps = 4
        )
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader(view)
        setupTutorial(view)
        setupNavigation(view)
    }
    
    private fun setupHeader(view: View) {
        val headerLayout = view.findViewById<View>(R.id.headerLayout)
        val backButton = headerLayout.findViewById<Button>(R.id.btnBack)
        val titleText = headerLayout.findViewById<android.widget.TextView>(R.id.tvHeaderTitle)
        val rightText = headerLayout.findViewById<android.widget.TextView>(R.id.tvHeaderRight)
        
        // Set title and hide right text
        titleText.text = "📚 How to Use Chinese Travel"
        rightText.visibility = android.view.View.GONE
        
        // Set up back button
        backButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, WelcomeFragment())
                .commit()
        }
    }

    private fun setupTutorial(view: View) {
        showTutorialStep(view, tutorialSteps[currentStep])
    }

    private fun showTutorialStep(view: View, step: TutorialStep) {
        // Update content
        view.findViewById<TextView>(R.id.tvTutorialTitle).text = step.title
        view.findViewById<TextView>(R.id.tvTutorialDescription).text = step.description

        // Load image from assets
        val imageView = view.findViewById<ImageView>(R.id.ivTutorialImage)
        loadImageFromAssets(imageView, step.imageRes)

        // Update navigation buttons
        val btnPrevious = view.findViewById<Button>(R.id.btnPrevious)
        val btnNext = view.findViewById<Button>(R.id.btnNext)
        val btnGetStarted = view.findViewById<Button>(R.id.btnGetStarted)

        btnPrevious.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        btnNext.visibility = if (currentStep < tutorialSteps.size - 1) View.VISIBLE else View.GONE
        btnGetStarted.visibility = if (currentStep == tutorialSteps.size - 1) View.VISIBLE else View.GONE
    }

    private fun loadImageFromAssets(imageView: ImageView, imageName: String) {
        try {
            val inputStream = requireContext().assets.open(imageName)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: Exception) {
            // Fallback to a default image if asset loading fails
            imageView.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    private fun setupNavigation(view: View) {
        view.findViewById<Button>(R.id.btnPrevious).setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                showTutorialStep(view, tutorialSteps[currentStep])
            }
        }

        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (currentStep < tutorialSteps.size - 1) {
                currentStep++
                showTutorialStep(view, tutorialSteps[currentStep])
            }
        }

        view.findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            // Navigate to HomeFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment())
                .commit()
        }


    }

    data class TutorialStep(
        val title: String,
        val description: String,
        val imageRes: String,
        val stepNumber: Int,
        val totalSteps: Int
    )
}
