package io.github.vontk.tally

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal fun readableOn(background: Color): Color {
    val darkContrast = (background.luminance() + .05f) / .05f
    val lightContrast = 1.05f / (background.luminance() + .05f)
    return if (darkContrast >= lightContrast) Color.Black else Color.White
}

/** Action labels need contrast against their surface; the counter keeps the exact chosen color. */
internal fun actionAccent(accent: Color, background: Color): Color {
    fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
    if (contrast(accent, background) >= 4.5f) return accent
    val destination = readableOn(background)
    return (1..20)
        .asSequence()
        .map { lerp(accent, destination, it / 20f) }
        .first { contrast(it, background) >= 4.5f }
}
