package com.example.duudle

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.duudle.ui.theme.DuudleColors

// The look of the floating pop-up, shared by the real one and the sample. The overlay is
// plain Views (it lives outside any activity), styled to match the Compose screens: a white
// rounded card, a pastel avatar, a square doodle and black / grey pill buttons. Every colour
// is set explicitly, so the phone's dark mode and the service context's theme don't matter.

private fun Context.popupPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

private fun Context.rounded(color: Color, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color.toArgb())
    cornerRadius = popupPx(radiusDp).toFloat()
}

/** The white card everything goes in. Add it to the window through [popupFrame]. */
internal fun popupCard(context: Context): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    background = context.rounded(DuudleColors.Paper, 28).apply {
        setStroke(context.popupPx(1), DuudleColors.Hairline.toArgb())
    }
    val pad = context.popupPx(16)
    setPadding(pad, pad, pad, pad)
    elevation = context.popupPx(10).toFloat()
}

/** The window's root: transparent room around [card] so its shadow isn't cut off. */
internal fun popupFrame(context: Context, card: View): FrameLayout = FrameLayout(context).apply {
    val room = context.popupPx(14)
    setPadding(room, room, room, room)
    clipToPadding = false
    addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
}

/** Avatar circle with [title] and [subtitle] beside it, [widthPx] wide so long text wraps. */
internal fun popupHeader(
    context: Context,
    emoji: String,
    avatarColor: Color,
    title: String,
    subtitle: String,
    widthPx: Int
): View = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = context.popupPx(12)
    }
    addView(TextView(context).apply {
        text = emoji
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(avatarColor.toArgb())
        }
        // Decorative; the name beside it is what matters to a screen reader.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, LinearLayout.LayoutParams(context.popupPx(40), context.popupPx(40)))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            setTextColor(DuudleColors.Ink.toArgb())
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(TextView(context).apply {
            text = subtitle
            setTextColor(DuudleColors.Muted.toArgb())
            textSize = 13f
        })
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = context.popupPx(10)
    })
}

/** A rounded, hairline-edged square around [content] (the doodle or sticker). */
internal fun popupSquare(context: Context, content: View): View = FrameLayout(context).apply {
    background = context.rounded(DuudleColors.Paper, 18)
    clipToOutline = true
    addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    foreground = GradientDrawable().apply {
        cornerRadius = context.popupPx(18).toFloat()
        setStroke(context.popupPx(1), DuudleColors.Hairline.toArgb())
    }
}

/** A 48dp pill button: black with white text when [primary], grey with black text otherwise. */
internal fun popupButton(context: Context, label: String, primary: Boolean = false, onClick: () -> Unit): Button =
    Button(context).apply {
        text = label
        isAllCaps = false
        stateListAnimator = null
        val fill = context.rounded(if (primary) DuudleColors.Ink else DuudleColors.PillGrey, 24)
        val ripple = (if (primary) DuudleColors.Paper else DuudleColors.Ink).copy(alpha = 0.2f)
        background = RippleDrawable(ColorStateList.valueOf(ripple.toArgb()), fill, null)
        setTextColor((if (primary) DuudleColors.Paper else DuudleColors.Ink).toArgb())
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        minWidth = 0
        minimumWidth = 0
        minHeight = context.popupPx(48)
        minimumHeight = context.popupPx(48)
        setPadding(context.popupPx(6), 0, context.popupPx(6), 0)
        maxLines = 1
        // Shrinks rather than clips at large font sizes.
        setAutoSizeTextTypeUniformWithConfiguration(10, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        setOnClickListener { onClick() }
    }

/** [buttons] side by side with equal widths and small gaps, [widthPx] wide. */
internal fun popupButtonRow(context: Context, widthPx: Int, buttons: List<Button>): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.popupPx(12)
        }
        buttons.forEachIndexed { index, button ->
            addView(button, LinearLayout.LayoutParams(0, context.popupPx(48), 1f).apply {
                if (index > 0) marginStart = context.popupPx(8)
            })
        }
    }
