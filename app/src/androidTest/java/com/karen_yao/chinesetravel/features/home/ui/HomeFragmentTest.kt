package com.karen_yao.chinesetravel.features.home.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karen_yao.chinesetravel.MainActivity
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    private lateinit var repository: TravelRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = TravelRepository(AppDatabase.getDatabase(context))
        repository.clearAllSnaps()
    }

    @After
    fun tearDown() = runBlocking {
        repository.clearAllSnaps()
    }

    @Test
    fun emptyAndCollectionStatesUsePluralizedCounts() {
        launchHome().use { scenario ->
            awaitUi(scenario) { activity ->
                activity.findViewById<View>(R.id.emptyStateLayout).visibility == View.VISIBLE &&
                    activity.findViewById<TextView>(R.id.tvHeaderRight).text.toString() ==
                    activity.snapCountText(0)
            }

            runBlocking { repository.saveSnap(snap("first")) }

            awaitUi(scenario) { activity ->
                activity.findViewById<RecyclerView>(R.id.recycler).let { recycler ->
                    recycler.visibility == View.VISIBLE && recycler.adapter?.itemCount == 1
                } && activity.findViewById<TextView>(R.id.tvHeaderRight).text.toString() ==
                    activity.snapCountText(1)
            }

            runBlocking { repository.saveSnap(snap("second")) }

            awaitUi(scenario) { activity ->
                activity.findViewById<TextView>(R.id.tvHeaderRight).text.toString() ==
                    activity.snapCountText(2)
            }
        }
    }

    @Test
    fun deleteRunsOnlyAfterConfirmation() = runBlocking {
        repository.saveSnap(snap("delete-me"))
        launchHome().use { scenario ->
            awaitItemCount(scenario, 1)

            onView(withId(R.id.btnDelete)).perform(click())
            assertEquals(1, repository.getSnapCount())

            onView(withText(R.string.home_delete_confirm)).perform(click())
            awaitRepositoryCount(0)
        }
    }

    @Test
    fun clearAllRunsOnlyAfterConfirmation() = runBlocking {
        repository.saveSnap(snap("one"))
        repository.saveSnap(snap("two"))
        launchHome().use { scenario ->
            awaitItemCount(scenario, 2)

            onView(withId(R.id.btnHeaderOverflow)).perform(click())
            onView(withText(R.string.home_clear_all)).perform(click())
            assertEquals(2, repository.getSnapCount())

            onView(withText(R.string.home_clear_confirm)).perform(click())
            awaitRepositoryCount(0)
        }
    }

    @Test
    fun collectionPausesWhileStoppedAndCatchesUpWhenStarted() = runBlocking {
        repository.saveSnap(snap("one"))
        launchHome().use { scenario ->
            awaitItemCount(scenario, 1)

            scenario.moveToState(Lifecycle.State.CREATED)
            repository.saveSnap(snap("two"))
            SystemClock.sleep(250)
            scenario.onActivity { activity ->
                assertEquals(
                    activity.snapCountText(1),
                    activity.findViewById<TextView>(R.id.tvHeaderRight).text.toString()
                )
            }

            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUi(scenario) { activity ->
                activity.findViewById<TextView>(R.id.tvHeaderRight).text.toString() ==
                    activity.snapCountText(2)
            }
        }
    }

    @Test
    fun invalidMapUrlDoesNotExposeMapAction() = runBlocking {
        repository.saveSnap(snap("bad-map", mapUrl = "not a map URL"))
        launchHome().use { scenario ->
            awaitItemCount(scenario, 1)
            onView(withId(R.id.tvGoogleMapsLink)).check(matches(withEffectiveVisibilityGone()))
        }
    }

    private fun launchHome(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.container, HomeFragment())
                    .commitNow()
            }
        }

    private fun awaitItemCount(scenario: ActivityScenario<MainActivity>, expected: Int) {
        awaitUi(scenario) { activity ->
            activity.findViewById<RecyclerView>(R.id.recycler).adapter?.itemCount == expected
        }
    }

    private fun awaitUi(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity -> matched = condition(activity) }
            if (!matched) SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        assertTrue("Home UI did not reach the expected state", matched)
    }

    private suspend fun awaitRepositoryCount(expected: Int) {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
        var count = repository.getSnapCount()
        while (count != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
            count = repository.getSnapCount()
        }
        assertEquals(expected, count)
    }

    private fun snap(id: String, mapUrl: String? = null) = PlaceSnap(
        id = id,
        imagePath = "/test/$id.jpg",
        nameCn = id,
        namePinyin = id,
        lat = null,
        longitude = null,
        address = null,
        translation = id,
        googleMapsLink = mapUrl,
        createdAt = id.hashCode().toLong()
    )

    private fun withEffectiveVisibilityGone() =
        withEffectiveVisibility(Visibility.GONE)

    private fun MainActivity.snapCountText(count: Int): String =
        resources.getQuantityString(R.plurals.home_snap_count, count, count)

    private companion object {
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
