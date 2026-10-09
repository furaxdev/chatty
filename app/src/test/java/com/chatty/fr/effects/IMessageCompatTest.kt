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
    fun `mention iPhone avec caracteres speciaux`() {
        // « é » décomposé (e + accent combinant), espace insécable, marque invisible finale
        val decomposed = "Yo\n(envoye\u0301 avec des lasers)"
        assertEquals("Yo" to MessageEffect.LASERS, EffectCodec.decode(decomposed))
        assertEquals("Yo" to MessageEffect.LASERS, EffectCodec.decode("Yo\n(envoyé\u00A0avec\u202Fdes lasers)\u200E"))
        assertEquals("Yo" to MessageEffect.LASERS, EffectCodec.decode("Yo (ENVOYÉ AVEC DES LASERS) "))
    }

    @Test
    fun `envoi avec mention iPhone puis relecture par Chatty`() {
        MessageEffect.entries.forEach { effect ->
            val sent = EffectCodec.encode("Salut", effect, iPhoneLabel = true)
            assertEquals("Salut" to effect, EffectCodec.decode(sent))
        }
    }

    @Test
    fun `lien vers l'animation pour les iPhone`() {
        val visible = EffectCodec.encode("Hi", MessageEffect.LASERS, iPhoneLabel = true)
            .filter { it != '\u2063' && it != '\u200B' }
        assertEquals("Hi\n🪩 Voir l'effet : https://furaxdev.github.io/chatty/e/#lasers.SGk", visible)
        // Effets propres à Chatty aussi : la page web les rejoue tous.
        val snow = EffectCodec.encode("Hey", MessageEffect.SNOW, iPhoneLabel = true)
        assertEquals(true, snow.contains("https://furaxdev.github.io/chatty/e/#snow.SGV5"))
    }

    @Test
    fun `lien recu sans marqueur Chatty`() {
        // Ex. un SMS transféré ou copié : le lien seul suffit à retrouver l'effet.
        assertEquals(
            "Bonne nuit" to MessageEffect.SHOOTING_STARS,
            EffectCodec.decode("Bonne nuit\n🌠 Voir l'effet : https://furaxdev.github.io/chatty/e/#shooting_stars.Qm9ubmUgbnVpdA"),
        )
    }

    @Test
    fun `texte accentue dans le lien`() {
        val link = IMessageCompat.link("Ça marche 🎉", MessageEffect.CONFETTI)
        val b64 = link.substringAfter("#confetti.")
        assertEquals("Ça marche 🎉", String(java.util.Base64.getUrlDecoder().decode(b64), Charsets.UTF_8))
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
