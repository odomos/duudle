package com.example.duudle

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A little loop a sticker plays when it's shown big (the pop-up, the in-app viewer): the whole
 * image wobbles, bounces or pulses. Vectors only, so no frame data and nothing extra to send.
 * Never used in grids, and never when the user turned on "Remove animations".
 */
enum class StickerMotion { NONE, WOBBLE, BOUNCE, PULSE }

/** False when the user turned on "Remove animations" (animator duration scale 0). */
fun isMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

private fun periodMillis(motion: StickerMotion): Int = when (motion) {
    StickerMotion.WOBBLE -> 700
    StickerMotion.BOUNCE -> 600
    StickerMotion.PULSE -> 550
    StickerMotion.NONE -> 1000
}

/** Compose side: applies [motion] to whatever it's on. Does nothing for NONE or reduced motion. */
@Composable
fun Modifier.stickerMotion(motion: StickerMotion): Modifier {
    if (motion == StickerMotion.NONE || !rememberMotionEnabled()) return this
    val t by rememberInfiniteTransition(label = "stickerMotion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis(motion), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "stickerMotionT"
    )
    return this.graphicsLayer {
        when (motion) {
            StickerMotion.WOBBLE -> rotationZ = (t - 0.5f) * 12f
            StickerMotion.BOUNCE -> translationY = -t * size.height * 0.06f
            StickerMotion.PULSE -> {
                val s = 1f + t * 0.08f
                scaleX = s
                scaleY = s
            }
            StickerMotion.NONE -> Unit
        }
    }
}

private fun motionAnimator(view: View, motion: StickerMotion): Animator? {
    val density = view.resources.displayMetrics.density
    val animator = when (motion) {
        StickerMotion.NONE -> return null
        StickerMotion.WOBBLE -> ObjectAnimator.ofFloat(view, View.ROTATION, -6f, 6f)
        StickerMotion.BOUNCE -> ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -10f * density)
        StickerMotion.PULSE -> ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)
        )
    }
    return animator.apply {
        duration = periodMillis(motion).toLong()
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
    }
}

/**
 * View side (the pop-up overlay): plays [motion] on [view] while it's on screen and stops it when
 * the window is removed, so no animator is left running.
 */
fun attachStickerMotion(view: View, motion: StickerMotion) {
    if (motion == StickerMotion.NONE || !isMotionEnabled(view.context)) return
    val animator = motionAnimator(view, motion) ?: return
    view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (!animator.isStarted) animator.start()
        }

        override fun onViewDetachedFromWindow(v: View) {
            animator.cancel()
        }
    })
}
