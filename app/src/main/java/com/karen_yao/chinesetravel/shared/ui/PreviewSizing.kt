package com.karen_yao.chinesetravel.shared.ui

import kotlin.math.ceil
import kotlin.math.floor

internal enum class PreviewScale { FIT_CENTER, CENTER_CROP }
internal data class PreviewSize(val width: Int, val height: Int)
internal data class PreviewTransform(
    val left: Int, val top: Int, val cropWidth: Int, val cropHeight: Int, val output: PreviewSize
)

/** Uses upright dimensions; the sample factor is also valid for the encoded orientation. */
internal fun previewSampleSize(source: PreviewSize, target: PreviewSize, scale: PreviewScale): Int {
    require(source.width > 0 && source.height > 0 && target.width > 0 && target.height > 0)
    val ratio = previewScaleRatio(source, target, scale)
    val neededWidth = ceil(source.width * ratio).toInt().coerceAtLeast(1)
    val neededHeight = ceil(source.height * ratio).toInt().coerceAtLeast(1)
    var sample = 1
    while (sample <= Int.MAX_VALUE / 2) {
        val next = sample * 2
        if (source.width / next < neededWidth || source.height / next < neededHeight) break
        sample = next
    }
    // A panorama must not allocate a huge bitmap just to center-crop a small visible region.
    val pixelBudget = target.width.toDouble() * target.height * 4
    while (sample <= Int.MAX_VALUE / 2 &&
        ceil(source.width.toDouble() / sample) * ceil(source.height.toDouble() / sample) > pixelBudget
    ) sample *= 2
    return sample
}

/** Crop before scaling so CENTER_CROP never creates an oversized scaled intermediate. */
internal fun previewTransform(source: PreviewSize, target: PreviewSize, scale: PreviewScale): PreviewTransform {
    require(source.width > 0 && source.height > 0 && target.width > 0 && target.height > 0)
    val ratio = previewScaleRatio(source, target, scale)
    val cropWidth = if (scale == PreviewScale.CENTER_CROP) {
        floor(target.width / ratio).toInt().coerceIn(1, source.width)
    } else source.width
    val cropHeight = if (scale == PreviewScale.CENTER_CROP) {
        floor(target.height / ratio).toInt().coerceIn(1, source.height)
    } else source.height
    return PreviewTransform(
        left = (source.width - cropWidth) / 2,
        top = (source.height - cropHeight) / 2,
        cropWidth = cropWidth,
        cropHeight = cropHeight,
        output = PreviewSize(
            (cropWidth * ratio).toInt().coerceIn(1, target.width),
            (cropHeight * ratio).toInt().coerceIn(1, target.height)
        )
    )
}

private fun previewScaleRatio(source: PreviewSize, target: PreviewSize, scale: PreviewScale): Double {
    val widthRatio = target.width.toDouble() / source.width
    val heightRatio = target.height.toDouble() / source.height
    return minOf(1.0, when (scale) {
        PreviewScale.FIT_CENTER -> minOf(widthRatio, heightRatio)
        PreviewScale.CENTER_CROP -> maxOf(widthRatio, heightRatio)
    })
}
