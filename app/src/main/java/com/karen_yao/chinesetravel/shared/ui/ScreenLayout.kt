package com.karen_yao.chinesetravel.shared.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.karen_yao.chinesetravel.R

/** The activity container is the only owner of system-bar and cutout padding. */
internal fun Activity.configureScreenWindow() {
    WindowCompat.enableEdgeToEdge(window)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

internal fun View.applyScreenInsets() {
    val original = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val safe = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            left = original.left + safe.left,
            top = original.top + safe.top,
            right = original.right + safe.right,
            bottom = original.bottom + safe.bottom
        )
        // Leave dispatch intact: no child screen applies these insets again.
        insets
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}

/** Clearance follows the controls' actual bounds, including their bottom margin. */
internal fun View.reserveFloatingActionSpace(vararg scrollingViews: View) {
    val gap = resources.getDimensionPixelSize(R.dimen.space_16)
    fun updateClearance() {
        val bottomMargin = (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        val clearance = height + bottomMargin + gap
        scrollingViews.forEach { it.updatePadding(bottom = clearance) }
    }
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateClearance() }
    doOnLayout { updateClearance() }
}

/** Only the list owns spacing between rows; the screen owns the outer gutter. */
internal class CardSpacingDecoration(private val gap: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        outRect.set(0, 0, 0, if (position != RecyclerView.NO_POSITION && position < state.itemCount - 1) gap else 0)
    }
}
