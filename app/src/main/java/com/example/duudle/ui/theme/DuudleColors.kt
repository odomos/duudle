package com.example.duudle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

// Display font for the "Duudle" wordmark and big headlines. System font for now;
// swap this one token if a custom typeface is ever bundled.
val DuudleDisplayFont: FontFamily = FontFamily.Default

/** A character colour: [light] is the highlight of the radial gradient, [base] the saturated body. */
data class Pastel(val light: Color, val base: Color) {
    /** Single flat colour for the Reference-A style (no gradient). */
    val flat: Color get() = lerp(light, base, 0.72f)
}

object DuudleColors {
    val Ink = Color(0xFF141414)
    val Paper = Color(0xFFFFFFFF)
    val Cream = Color(0xFFF5F3EF)
    val SkyTop = Color(0xFFE9F2FF)
    val Muted = Color(0xFF6E6E73)
    val PillGrey = Color(0xFFF0F0F2)
    val Hairline = Color(0xFFE6E6EA)
    /** Error text: dark enough to read on Cream (Coral base is too light for text). */
    val ErrorRed = Color(0xFFC62828)

    val Mint = Pastel(light = Color(0xFFD9F8C4), base = Color(0xFF7ED957))
    val Lilac = Pastel(light = Color(0xFFE9D8FF), base = Color(0xFFB58CF2))
    val Bubblegum = Pastel(light = Color(0xFFFFD3EC), base = Color(0xFFF27AC6))
    val Sunny = Pastel(light = Color(0xFFFFF0B3), base = Color(0xFFFFC83A))
    val Tangerine = Pastel(light = Color(0xFFFFD7BD), base = Color(0xFFFF9248))
    val Sky = Pastel(light = Color(0xFFD3EBFF), base = Color(0xFF5BAEF5))
    val Coral = Pastel(light = Color(0xFFFFC7C2), base = Color(0xFFFF6B66))
}
