package com.chatty.fr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFormatTest {

    @Test
    fun `reponse aller-retour`() {
        val encoded = MessageFormat.encodeReply("On se voit à 18h ?", "Carrément !")
        val (quote, text) = MessageFormat.decodeReply(encoded)
        assertEquals("On se voit à 18h ?", quote)
        assertEquals("Carrément !", text)
    }

    @Test
    fun `message sans citation`() {
        assertEquals(null to "Salut", MessageFormat.decodeReply("Salut"))
    }

    @Test
    fun `reaction Chatty aller-retour`() {
        val r = MessageFormat.parseReaction(MessageFormat.encodeReaction("❤️", "Bonne nuit"))!!
        assertEquals("❤️", r.emoji)
        assertEquals("Bonne nuit", r.target)
        assertFalse(r.removed)
        val removed = MessageFormat.parseReaction(MessageFormat.encodeReaction("❤️", "Bonne nuit", removed = true))!!
        assertTrue(removed.removed)
    }

    @Test
    fun `tapback iPhone`() {
        val fr = MessageFormat.parseReaction("A aimé « On mange où ? »")!!
        assertEquals("👍", fr.emoji)
        assertEquals("On mange où ?", fr.target)
        val en = MessageFormat.parseReaction("Laughed at “lol t'es sérieux”")!!
        assertEquals("😂", en.emoji)
        assertEquals("lol t'es sérieux", en.target)
    }

    @Test
    fun `reaction Google Messages anglais`() {
        val r = MessageFormat.parseReaction("Reacted 🔥 to \"New phone who dis\"")!!
        assertEquals("🔥", r.emoji)
        assertEquals("New phone who dis", r.target)
    }

    @Test
    fun `texte normal n'est pas une reaction`() {
        assertNull(MessageFormat.parseReaction("J'ai aimé le film"))
    }

    @Test
    fun `correspondance tronquee`() {
        assertTrue(MessageFormat.matches("Un très long message qui a été cou…", "Un très long message qui a été coupé parce qu'il dépasse"))
        assertFalse(MessageFormat.matches("Autre chose", "Bonjour"))
    }

    @Test
    fun `codes de verification`() {
        assertEquals("482913", MessageFormat.findOtp("Votre code de vérification est 482913. Ne le partagez pas."))
        assertEquals("123456", MessageFormat.findOtp("G-123456 is your Google verification code"))
        assertEquals("123456", MessageFormat.findOtp("Code : 123 456"))
        assertNull(MessageFormat.findOtp("Rendez-vous au 12 rue de Paris à 1830"))
    }
}
