package com.furaxdev.chatty.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectCodecTest {

    @Test
    fun `chaque effet survit a l'aller-retour`() {
        MessageEffect.entries.forEach { effect ->
            val (text, decoded) = EffectCodec.decode(EffectCodec.encode("Salut Furax !", effect))
            assertEquals("Salut Furax !", text)
            assertEquals(effect, decoded)
        }
    }

    @Test
    fun `un message normal n'a pas d'effet`() {
        val (text, effect) = EffectCodec.decode("Coucou")
        assertEquals("Coucou", text)
        assertNull(effect)
    }

    @Test
    fun `le marqueur est invisible`() {
        val encoded = EffectCodec.encode("Bravo", MessageEffect.CONFETTI)
        assertEquals("Bravo", encoded.filter { it.isLetterOrDigit() })
    }
}
