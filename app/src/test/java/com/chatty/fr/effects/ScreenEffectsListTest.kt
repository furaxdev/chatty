package com.chatty.fr.effects

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde-fou contre les régressions : les effets d'écran d'origine (et leur ordre, qui sert à
 * l'encodage dans les SMS) doivent rester disponibles.
 */
class ScreenEffectsListTest {

    @Test
    fun `tous les effets d'ecran d'origine sont presents`() {
        assertEquals(
            listOf(
                "ECHO", "SPOTLIGHT", "BALLOONS", "CONFETTI", "LOVE", "LASERS", "FIREWORKS",
                "CELEBRATION", "SNOW", "EMOJI_RAIN", "RAINBOW", "MONEY", "SHOOTING_STARS",
            ),
            MessageEffect.screen.map { it.name },
        )
    }

    @Test
    fun `les effets de bulle d'origine sont presents`() {
        assertEquals(
            listOf("SLAM", "LOUD", "GENTLE", "INVISIBLE_INK", "SHAKE", "JELLY"),
            MessageEffect.bubble.map { it.name },
        )
    }
}
