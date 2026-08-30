package io.s2qtech.shenk

import android.animation.ValueAnimator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

/** Shared motion contract for the accepted Soft Kinetic visual system. */
internal object ShenkMotionTokens {
    const val QUICK_MILLIS = 180
    const val STANDARD_MILLIS = 240
    const val EMPHASIZED_MILLIS = 280

    val SpatialEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

internal fun shenkAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

internal suspend fun PagerState.animateShenkToPage(page: Int) {
    if (currentPage == page && currentPageOffsetFraction == 0f) return
    if (!shenkAnimationsEnabled()) {
        scrollToPage(page)
        return
    }
    animateScrollToPage(
        page = page,
        animationSpec = tween(
            durationMillis = shenkPageMotionDuration(currentPage, page),
            easing = ShenkMotionTokens.SpatialEasing,
        ),
    )
}

internal fun shenkPageMotionDuration(fromPage: Int, toPage: Int): Int =
    if (abs(toPage - fromPage) > 1) {
        ShenkMotionTokens.EMPHASIZED_MILLIS
    } else {
        ShenkMotionTokens.STANDARD_MILLIS
    }

internal fun shenkHorizontalContentTransform(forward: Boolean): ContentTransform {
    if (!shenkAnimationsEnabled()) return EnterTransition.None togetherWith ExitTransition.None
    val direction = if (forward) 1 else -1
    val enter = fadeIn(
        animationSpec = tween(ShenkMotionTokens.QUICK_MILLIS),
        initialAlpha = 0.92f,
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = ShenkMotionTokens.STANDARD_MILLIS,
            easing = ShenkMotionTokens.SpatialEasing,
        ),
        initialOffsetX = { width -> direction * width / 12 },
    )
    val exit = fadeOut(
        animationSpec = tween(ShenkMotionTokens.QUICK_MILLIS),
        targetAlpha = 0.92f,
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = ShenkMotionTokens.STANDARD_MILLIS,
            easing = ShenkMotionTokens.SpatialEasing,
        ),
        targetOffsetX = { width -> -direction * width / 16 },
    )
    return enter togetherWith exit
}

internal fun shenkStateContentTransform(): ContentTransform {
    if (!shenkAnimationsEnabled()) return EnterTransition.None togetherWith ExitTransition.None
    val enter = fadeIn(
        animationSpec = tween(ShenkMotionTokens.STANDARD_MILLIS),
        initialAlpha = 0.9f,
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = ShenkMotionTokens.STANDARD_MILLIS,
            easing = ShenkMotionTokens.SpatialEasing,
        ),
        initialOffsetY = { height -> height / 14 },
    )
    val exit = fadeOut(
        animationSpec = tween(ShenkMotionTokens.QUICK_MILLIS),
        targetAlpha = 0.9f,
    ) + slideOutVertically(
        animationSpec = tween(
            durationMillis = ShenkMotionTokens.QUICK_MILLIS,
            easing = ShenkMotionTokens.SpatialEasing,
        ),
        targetOffsetY = { height -> -height / 18 },
    )
    return enter togetherWith exit
}

internal fun shenkAppearEnter(initialScale: Float = 0.94f): EnterTransition {
    if (!shenkAnimationsEnabled()) return EnterTransition.None
    return fadeIn(animationSpec = tween(ShenkMotionTokens.QUICK_MILLIS)) + scaleIn(
        initialScale = initialScale,
        animationSpec = tween(
            durationMillis = ShenkMotionTokens.STANDARD_MILLIS,
            easing = ShenkMotionTokens.SpatialEasing,
        ),
    )
}

internal fun shenkAppearExit(targetScale: Float = 0.97f): ExitTransition {
    if (!shenkAnimationsEnabled()) return ExitTransition.None
    return fadeOut(animationSpec = tween(ShenkMotionTokens.QUICK_MILLIS)) + scaleOut(
        targetScale = targetScale,
        animationSpec = tween(
            durationMillis = ShenkMotionTokens.QUICK_MILLIS,
            easing = ShenkMotionTokens.SpatialEasing,
        ),
    )
}
