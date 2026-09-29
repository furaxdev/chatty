package com.chatty.fr.ui

import com.chatty.fr.data.Contact
import com.chatty.fr.data.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListFilterTest {
    private fun conv(address: String, name: String? = null) = Conversation(
        threadId = 1, address = address, contact = Contact(name, address), snippet = "", snippetEffect = null,
        date = 0, unreadCount = 0, lastIsMine = false,
    )

    @Test
    fun `expediteurs pro`() {
        assertTrue(ListFilter.isBusiness(conv("AMAZON")))
        assertTrue(ListFilter.isBusiness(conv("38000")))
        assertTrue(ListFilter.isBusiness(conv("La Poste")))
    }

    @Test
    fun `contacts perso`() {
        assertFalse(ListFilter.isBusiness(conv("+33612345678")))
        assertFalse(ListFilter.isBusiness(conv("0612345678", "Maman")))
        assertFalse(ListFilter.isBusiness(conv("+33611111111,+33622222222")))
    }
}
