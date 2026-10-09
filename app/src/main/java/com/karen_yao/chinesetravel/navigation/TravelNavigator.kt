package com.karen_yao.chinesetravel.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.features.map.ui.MapFragment
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment
import com.karen_yao.chinesetravel.features.tutorial.ui.TutorialFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment

/**
 * Activity-scoped screen transitions and back-stack rules.
 * Workflow callers defer navigation while state is saved and acknowledge accepted commands.
 * Commits and pops remain asynchronous; failures propagate to the caller.
 */
@MainThread
internal class TravelNavigator(
    private val fragmentManager: FragmentManager,
    private val backDispatcher: OnBackPressedDispatcher
) {
    val isStateSaved: Boolean
        get() = fragmentManager.isStateSaved

    fun openWelcome() {
        replace(WelcomeFragment())
    }

    fun openHome() {
        replace(HomeFragment())
    }

    fun openTutorial() {
        replace(TutorialFragment())
    }

    fun openCapture() {
        replace(CaptureFragment(), addToBackStack = true, backStackName = CAPTURE_BACK_STACK_NAME)
    }

    fun openMap() {
        replace(MapFragment(), addToBackStack = true)
    }

    fun openTextSelection(
        detectedTexts: List<String>,
        imagePath: String,
        selectedText: String,
        deleteImageIfUnsaved: Boolean = false
    ) {
        replace(
            TextSelectionFragment.newInstance(detectedTexts, imagePath, selectedText, deleteImageIfUnsaved),
            addToBackStack = true
        )
    }

    fun goBack() {
        backDispatcher.onBackPressed()
    }

    /** Map's header only pops its entry; a standalone map does not finish the activity. */
    fun closeMap() {
        fragmentManager.popBackStack()
    }

    fun returnHomeAfterSave() {
        val captureEntry = (0 until fragmentManager.backStackEntryCount).lastOrNull {
            fragmentManager.getBackStackEntryAt(it).name == CAPTURE_BACK_STACK_NAME
        }
        if (captureEntry != null) {
            fragmentManager.popBackStack(
                fragmentManager.getBackStackEntryAt(captureEntry).id,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        } else {
            // Standalone hosts and older unnamed stacks have no Home return marker.
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            fragmentManager.beginTransaction().setReorderingAllowed(true)
                .replace(R.id.container, HomeFragment()).commit()
        }
    }

    private fun replace(destination: Fragment, addToBackStack: Boolean = false, backStackName: String? = null) {
        fragmentManager.beginTransaction().apply {
            replace(R.id.container, destination)
            if (addToBackStack) addToBackStack(backStackName)
        }.commit()
    }

    companion object {
        // Keep this value stable: FragmentManager persists names across activity recreation.
        const val CAPTURE_BACK_STACK_NAME = "capture"
    }
}
