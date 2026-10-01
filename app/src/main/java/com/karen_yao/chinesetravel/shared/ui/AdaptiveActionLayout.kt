package com.karen_yao.chinesetravel.shared.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.karen_yao.chinesetravel.R
import kotlin.math.ceil
import kotlin.math.max

/** Keeps equal action slots, stacking when full labels cannot fit at the current text size. */
internal class AdaptiveActionLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val originalWeights = mutableMapOf<View, Float>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val actions = (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }
        val weights = actions.associateWith { child ->
            originalWeights.getOrPut(child) { (child.layoutParams as LayoutParams).weight }
        }
        val compactActions = actions.filter { weights.getValue(it) == 0f }
        val compactWidth = compactActions.sumOf {
            it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            it.measuredWidth
        }
        val totalWeight = weights.values.sum()
        val gap = resources.getDimensionPixelSize(R.dimen.space_8)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val weightedSpace = available - compactWidth - gap * (actions.size - 1)
        val stack = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && actions.any { child ->
            if (weights.getValue(child) == 0f) return@any child.measuredWidth > available
            val slot = if (totalWeight == 0f) available else (weightedSpace * weights.getValue(child) / totalWeight).toInt()
            val labelWidth = if (child is TextView) {
                val label = child.transformationMethod?.getTransformation(child.text, child) ?: child.text
                // Capture may wrap between its emoji and word, but never split the action word.
                val segments = if (compactActions.isEmpty()) label.toString().lines()
                    else label.toString().split(Regex("\\s+"))
                ceil(segments.maxOf { child.paint.measureText(it) }).toInt() +
                    child.compoundPaddingLeft + child.compoundPaddingRight
            } else child.minimumWidth
            max(child.minimumWidth, labelWidth) > slot
        }
        orientation = if (stack) VERTICAL else HORIZONTAL
        actions.forEachIndexed { index, child ->
            val params = child.layoutParams as LayoutParams
            val compact = weights.getValue(child) == 0f
            val width = if (compact) LayoutParams.WRAP_CONTENT else if (stack) LayoutParams.MATCH_PARENT else 0
            val weight = if (stack) 0f else weights.getValue(child)
            val end = if (!stack && index < actions.lastIndex) gap else 0
            val bottom = if (stack && index < actions.lastIndex) gap else 0
            if (params.width != width || params.height != LayoutParams.WRAP_CONTENT || params.weight != weight ||
                params.marginStart != 0 || params.marginEnd != end || params.topMargin != 0 || params.bottomMargin != bottom) {
                params.width = width
                params.height = LayoutParams.WRAP_CONTENT
                params.weight = weight
                params.marginStart = 0
                params.marginEnd = end
                params.topMargin = 0
                params.bottomMargin = bottom
                child.layoutParams = params
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
