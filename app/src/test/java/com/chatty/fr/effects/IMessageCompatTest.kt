package com.chatty.fr.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IMessageCompatTest {

    @Test
    fun `mention iPhone anglaise`() {
        assertEquals("Bravo !" to MessageEffect.CONFETTI, EffectCodec.decode("Bravo ! (Sent with Confetti)"))
        assertEquals("CHUT" to MessageEffect.LOUD, EffectCodec.decode("CHUT (Sent with Loud Effect)"))
        assertEquals("Regarde" to MessageEffect.SHOOTING_STARS, EffectCodec.decode("Regarde (Sent with Shooting Star)"))
        assertEquals("Secret" to MessageEffect.INVISIBLE_INK, EffectCodec.decode("Secret (Sent with Invisible Ink)"))
    }

    @Test
    fun `mention francaise`() {
        assertEquals("Coucou" to MessageEffect.BALLOONS, EffectCodec.decode("Coucou (Envoyé avec l'effet « Ballons »)"))
        assertEquals("Wouah" to MessageEffect.FIREWORKS, EffectCodec.decode("Wouah (Envoyé avec Feux d'artifice)"))
    }

    @Test
    fun `mention iPhone francaise avec article`() {
        // Message réel reçu d'un iPhone (capture de Furax)
        assertEquals("Bcccnxnnx'f" to MessageEffect.LASERS, EffectCodec.decode("Bcccnxnnx'f\n(envoyé avec des lasers)"))
        assertEquals("Tkt" to MessageEffect.CONFETTI, EffectCodec.decode("Tkt (envoyé avec des confettis)"))
        assertEquals("Je t'aime" to MessageEffect.LOVE, EffectCodec.decode("Je t'aime (envoyé avec de l'amour)"))
        assertEquals("Allô" to MessageEffect.ECHO, EffectCodec.decode("Allô (envoyé avec un écho)"))
        assertEquals("Wow" to MessageEffect.FIREWORKS, EffectCodec.decode("Wow (envoyé avec des feux d'artifice)"))
        assertEquals("Hey" to MessageEffect.SHOOTING_STARS, EffectCodec.decode("Hey (envoyé avec une étoile filante)"))
        assertEquals("Chut" to MessageEffect.INVISIBLE_INK, EffectCodec.decode("Chut (envoyé avec de l'encre invisible)"))
        assertEquals("Ok" to MessageEffect.SLAM, EffectCodec.decode("Ok (Sent with Slam effect)"))
    }

    @Test
    fun `envoi avec mention iPhone puis relecture par Chatty`() {
        MessageEffect.entries.forEach { effect ->
            val sent = EffectCodec.encode("Salut", effect, iPhoneLabel = true)
            assertEquals("Salut" to effect, EffectCodec.decode(sent))
        }
    }

    @Test
    fun `seuls les effets iMessage recoivent la mention visible`() {
        val visible = EffectCodec.encode("Hey", MessageEffect.CONFETTI, iPhoneLabel = true).filter { it.code >= 32 && it != '⁣' && it != '​' }
        assertEquals("Hey (Envoyé avec l'effet « Confettis »)", visible)
        val snow = EffectCodec.encode("Hey", MessageEffect.SNOW, iPhoneLabel = true).filter { it.code >= 32 && it != '⁣' && it != '​' }
        assertEquals("Hey", snow)
    }

    @Test
    fun `parentheses ordinaires conservees`() {
        assertEquals("Rdv demain (vers 18h)" to null, EffectCodec.decode("Rdv demain (vers 18h)"))
        assertEquals("Envoyé (avec amour)" to null, EffectCodec.decode("Envoyé (avec amour)"))
    }

    @Test
    fun `mots-cles comme iMessage`() {
        assertEquals(MessageEffect.BALLOONS, IMessageCompat.keywordEffect("Joyeux anniversaire Furax !"))
        assertEquals(MessageEffect.BALLOONS, IMessageCompat.keywordEffect("happy birthday bro"))
        assertEquals(MessageEffect.CONFETTI, IMessageCompat.keywordEffect("Félicitations pour ton bac"))
        assertEquals(MessageEffect.FIREWORKS, IMessageCompat.keywordEffect("Bonne année à tous 🎉"))
        assertEquals(MessageEffect.CELEBRATION, IMessageCompat.keywordEffect("Joyeux Nouvel An chinois"))
        assertEquals(MessageEffect.LASERS, IMessageCompat.keywordEffect("pew pew"))
        assertNull(IMessageCompat.keywordEffect("On mange où ce soir ?"))
    }
}
