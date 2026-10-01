package com.karen_yao.chinesetravel.shared.ui

import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.junit.Assert.assertTrue

/** Main-thread idle alone cannot wait for an image decoded on a worker. */
internal fun awaitPreviewBitmap(imageId: Int) {
    onView(withId(imageId)).perform(object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(ImageView::class.java)
        override fun getDescription() = "wait for the asynchronous preview bitmap"
        override fun perform(uiController: UiController, view: View) {
            val image = view as ImageView
            val deadline = SystemClock.uptimeMillis() + 5000
            while (image.drawable !is BitmapDrawable && SystemClock.uptimeMillis() < deadline) {
                uiController.loopMainThreadForAtLeast(16)
            }
            assertTrue("Preview did not finish loading", image.drawable is BitmapDrawable)
        }
    })
}
