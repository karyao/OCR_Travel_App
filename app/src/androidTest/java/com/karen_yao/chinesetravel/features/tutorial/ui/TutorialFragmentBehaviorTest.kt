package com.karen_yao.chinesetravel.features.tutorial.ui

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karen_yao.chinesetravel.MainActivity
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.appContainer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorialFragmentBehaviorTest {
    @Test fun tutorialScrollAndImageSurviveRepeatedBackgroundResume() {
        launchTutorial().use { scenario ->
            repeat(2) { clickNavigation(scenario, R.id.btnNext) }
            val scrollY = scrollTutorial(scenario)
            var image: Drawable? = null
            scenario.onActivity {
                assertTranslationStep(it)
                image = it.findViewById<ImageView>(R.id.ivTutorialImage).drawable
            }

            repeat(2) {
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitTutorialLayout()
                scenario.onActivity { activity ->
                    assertTranslationStep(activity)
                    assertEquals(scrollY, activity.findViewById<NestedScrollView>(R.id.tutorialContent).scrollY)
                    assertSame(image, activity.findViewById<ImageView>(R.id.ivTutorialImage).drawable)
                }
            }
        }
    }

    @Test fun tutorialStepScrollAndButtonStateSurviveActivityRecreation() {
        launchTutorial().use { scenario ->
            repeat(2) { clickNavigation(scenario, R.id.btnNext) }
            val scrollY = scrollTutorial(scenario)
            scenario.recreate()
            awaitTutorialLayout()
            scenario.onActivity {
                assertTranslationStep(it)
                assertEquals(scrollY, it.findViewById<NestedScrollView>(R.id.tutorialContent).scrollY)
            }
            clickNavigation(scenario, R.id.btnNext)
            scenario.onActivity {
                assertEquals(it.getString(R.string.tutorial_location_title), it.findViewById<TextView>(R.id.tvTutorialTitle).text)
                assertEquals(View.GONE, it.findViewById<View>(R.id.btnNext).visibility)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.btnGetStarted).visibility)
            }
        }
    }

    @Test fun nextAndPreviousStepsResetScrollAndUpdateContent() {
        launchTutorial().use { scenario ->
            scrollTutorial(scenario)
            clickNavigation(scenario, R.id.btnNext)
            scenario.onActivity {
                assertEquals(0, it.findViewById<NestedScrollView>(R.id.tutorialContent).scrollY)
                assertEquals(it.getString(R.string.tutorial_capture_title), it.findViewById<TextView>(R.id.tvTutorialTitle).text)
                assertEquals(it.getString(R.string.tutorial_capture_description), it.findViewById<TextView>(R.id.tvTutorialDescription).text)
                assertTrue(it.findViewById<ImageView>(R.id.ivTutorialImage).drawable is BitmapDrawable)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.btnPrevious).visibility)
            }

            scrollTutorial(scenario)
            clickNavigation(scenario, R.id.btnPrevious)
            scenario.onActivity {
                assertEquals(0, it.findViewById<NestedScrollView>(R.id.tutorialContent).scrollY)
                assertEquals(it.getString(R.string.tutorial_welcome_title), it.findViewById<TextView>(R.id.tvTutorialTitle).text)
                assertEquals(it.getString(R.string.tutorial_welcome_description), it.findViewById<TextView>(R.id.tvTutorialDescription).text)
                assertTrue(it.findViewById<ImageView>(R.id.ivTutorialImage).drawable is BitmapDrawable)
                assertEquals(View.INVISIBLE, it.findViewById<View>(R.id.btnPrevious).visibility)
            }
        }
    }

    @Test fun applicationDependenciesSurviveActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var before: Any? = null
            var repository: Any? = null
            scenario.onActivity { before = it.appContainer; repository = it.appContainer.repository }
            scenario.recreate()
            scenario.onActivity {
                assertSame(before, it.appContainer)
                assertSame(repository, it.appContainer.repository)
            }
        }
    }

    private fun launchTutorial(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity {
                it.supportFragmentManager.beginTransaction()
                    .replace(R.id.container, TutorialFragment()).commitNow()
            }
            awaitTutorialLayout()
        }

    private fun clickNavigation(scenario: ActivityScenario<MainActivity>, buttonId: Int) {
        scenario.onActivity { it.findViewById<View>(buttonId).performClick() }
        awaitTutorialLayout()
    }

    private fun scrollTutorial(scenario: ActivityScenario<MainActivity>): Int {
        awaitTutorialLayout()
        var scrollY = 0
        scenario.onActivity {
            val scroll = it.findViewById<NestedScrollView>(R.id.tutorialContent)
            assertTrue("Run with a compact phone viewport so the Tutorial can scroll", scroll.canScrollVertically(1))
            scroll.scrollTo(0, scroll.height / 2)
            scrollY = scroll.scrollY
            assertTrue("The regression test must start below the top", scrollY > 0)
        }
        awaitTutorialLayout()
        scenario.onActivity {
            assertEquals(scrollY, it.findViewById<NestedScrollView>(R.id.tutorialContent).scrollY)
        }
        return scrollY
    }

    private fun awaitTutorialLayout() {
        // Espresso waits for the main thread and pending layout work before checking the view.
        onView(withId(R.id.tutorialContent)).check { view, error ->
            if (error != null) throw error
            val scroll = view as NestedScrollView
            assertTrue("Tutorial content must be laid out", scroll.isLaidOut)
            assertFalse("Tutorial layout must be settled", scroll.isLayoutRequested)
        }
    }

    private fun assertTranslationStep(activity: MainActivity) {
        assertEquals(activity.getString(R.string.tutorial_translation_title), activity.findViewById<TextView>(R.id.tvTutorialTitle).text)
        assertEquals(activity.getString(R.string.tutorial_translation_description), activity.findViewById<TextView>(R.id.tvTutorialDescription).text)
        assertTrue(activity.findViewById<ImageView>(R.id.ivTutorialImage).drawable is BitmapDrawable)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnPrevious).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnNext).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnGetStarted).visibility)
    }
}
