package com.lainsmain.mneme.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamThemeTest {
    @Test
    fun `dream variant remains derived from the selected palette`() {
        val ocean = darkColorScheme(primary = Color(0xFF8BD5F5)).dreamVariant(dark = true)
        val amber = darkColorScheme(primary = Color(0xFFFFC285)).dreamVariant(dark = true)

        assertNotEquals(ocean.primary, amber.primary)
        assertNotEquals(Color(0xFF8BD5F5), ocean.primary)
        assertNotEquals(Color(0xFFFFC285), amber.primary)
    }

    @Test
    fun `dream variant is distinct in dark and light appearances`() {
        val darkBase = darkColorScheme(primary = Color(0xFF8BD5F5))
        val lightBase = lightColorScheme(primary = Color(0xFF006780))
        val darkDream = darkBase.dreamVariant(dark = true)
        val lightDream = lightBase.dreamVariant(dark = false)

        assertNotEquals(darkBase.primary, darkDream.primary)
        assertNotEquals(darkBase.background, darkDream.background)
        assertNotEquals(lightBase.primary, lightDream.primary)
        assertNotEquals(lightBase.background, lightDream.background)
        assertTrue(darkDream.primary != lightDream.primary)
    }
}
