package com.karen_yao.chinesetravel.features.welcome.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.navigation.travelNavigator

/**
 * Welcome screen fragment with app introduction and navigation.
 * Features a beautiful beige and red themed design.
 */
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWelcomeButtons(view)
    }
    
    private fun setupWelcomeButtons(view: View) {
        // Get started button - navigates to main app
        view.findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            travelNavigator.openHome()
        }
        
        // Learn more button - could show app features
        view.findViewById<Button>(R.id.btnLearnMore).setOnClickListener {
            travelNavigator.openTutorial()
        }
    }
    
}
