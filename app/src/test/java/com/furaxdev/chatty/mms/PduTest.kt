package com.furaxdev.chatty.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PduTest {

    @Test
    fun `index des types bien connus`() {
        assertEquals(0x03, Pdu.WellKnownTypes.indexOf("text/plain"))
        assertEquals(0x1E, Pdu.WellKnownTypes.indexOf("image/jpeg"))
        assertEquals(0x20, Pdu.WellKnownTypes.indexOf("image/png"))
        assertEquals(0x23, Pdu.WellKnownTypes.indexOf("application/vnd.wap.multipart.mixed"))
        assertEquals(0x33, Pdu.WellKnownTypes.indexOf("application/vnd.wap.multipart.related"))
        assertEquals(0x3E, Pdu.WellKnownTypes.indexOf("application/vnd.wap.mms-message"))
    }

    @Test
    fun `send-req aller-retour`() {
        val image = ByteArray(5000) { (it % 251).toByte() }
        val pdu = Pdu.buildSendReq(
            listOf("+33612345678", "06 98 76 54 32"),
            "Salut la team 🎉",
            listOf(Pdu.Attachment("image/jpeg", image, "photo.jpg")),
        )
        val m = Pdu.parse(pdu)
        assertNotNull(m)
        m!!
        assertEquals(Pdu.SEND_REQ, m.type)
        assertEquals(listOf("+33612345678", "0698765432"), m.to)
        assertEquals("application/vnd.wap.multipart.related", m.contentType)
        assertEquals(3, m.parts.size)
        assertEquals("application/smil", m.parts[0].contentType)
        val img = m.parts.first { it.contentType == "image/jpeg" }
        assertArrayEquals(image, img.data)
        assertEquals("photo.jpg", img.name)
        assertEquals("<photo.jpg>", img.contentId)
        val text = m.parts.first { it.isText }
        assertEquals(Pdu.CHARSET_UTF8, text.charset)
        assertEquals("Salut la team 🎉", text.text())
    }

    @Test
    fun `notification-ind`() {
        // m-notification-ind construit à la main, comme envoyé par un opérateur.
        val out = java.io.ByteArrayOutputStream()
        fun b(vararg v: Int) = v.forEach { out.write(it) }
        fun t(s: String) { out.write(s.toByteArray()); out.write(0) }
        b(0x8C, 0x82) // type = notification-ind
        b(0x98); t("abc123")
        b(0x8D, 0x92)
        b(0x89); val from = "+33611223344/TYPE=PLMN".toByteArray(); b(from.size + 2, 0x80); out.write(from); b(0)
        b(0x8A, 0x80) // classe
        b(0x8E, 0x02, 0x0B, 0xB8) // taille = 3000
        b(0x88, 0x05, 0x81, 0x03, 0x02, 0xA3, 0x00) // expiry relatif
        b(0x83); t("http://mms.operateur.fr/get?id=42")
        val m = Pdu.parse(out.toByteArray())!!
        assertEquals(Pdu.NOTIFICATION_IND, m.type)
        assertEquals("abc123", m.transactionId)
        assertEquals("+33611223344", m.from)
        assertEquals(3000L, m.messageSize)
        assertEquals("http://mms.operateur.fr/get?id=42", m.contentLocation)
    }

    @Test
    fun `notifyresp-ind`() {
        val m = Pdu.parse(Pdu.buildNotifyRespInd("xyz"))!!
        assertEquals(Pdu.NOTIFYRESP_IND, m.type)
        assertEquals("xyz", m.transactionId)
        assertNull(m.contentType)
    }

    @Test
    fun `uintvar sur plusieurs octets`() {
        val big = ByteArray(200_000) { 7 }
        val m = Pdu.parse(Pdu.buildSendReq(listOf("+33600000000"), null, listOf(Pdu.Attachment("image/png", big, "a.png"))))!!
        assertEquals(200_000, m.parts.first { it.contentType == "image/png" }.data.size)
    }
}
