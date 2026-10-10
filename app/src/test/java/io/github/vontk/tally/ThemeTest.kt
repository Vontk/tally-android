package io.github.vontk.tally

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val luminances = listOf(a.luminance(), b.luminance()).sorted()
        return (luminances[1] + .05f) / (luminances[0] + .05f)
    }

    @Test
    fun counterLabelsStayReadableAcrossCustomColors() {
        for (channel in 0..255 step 5) {
            val background = Color(channel, channel, channel)
            assertTrue(
                "Unreadable foreground for gray $channel",
                contrast(background, readableOn(background)) >= 4.5f,
            )
        }
    }

    @Test
    fun actionLabelsMeetContrastOnDarkLightAndIntermediateSurfaces() {
        val accents =
            listOf(Color.Black, Color.White, Color.Blue, Color.Red, Color.Green, Color(0xFFA69AEE))
        for (channel in 0..255 step 15) {
            val background = Color(channel, channel, channel)
            for (accent in accents) assertTrue(
                contrast(actionAccent(accent, background), background) >= 4.5f
            )
        }
    }
}
