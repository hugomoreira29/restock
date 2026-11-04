package com.example.restock

import com.example.restock.data.InviteCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {

    @Test
    fun `aceita codigos com espacos ou minusculas`() {
        assertEquals("ABC123", InviteCodes.normalize("  abc123 "))
    }

    @Test
    fun `recusa texto que partiria o caminho do documento`() {
        assertNull(InviteCodes.normalize("ABC/12"))
        assertNull(InviteCodes.normalize("../../"))
    }

    @Test
    fun `recusa tamanhos errados`() {
        assertNull(InviteCodes.normalize(""))
        assertNull(InviteCodes.normalize("ABC12"))
        assertNull(InviteCodes.normalize("ABC1234"))
    }

    @Test
    fun `os codigos gerados sao sempre validos`() {
        repeat(100) {
            val code = InviteCodes.generate()
            assertEquals(code, InviteCodes.normalize(code))
        }
    }

    @Test
    fun `o convite expira exatamente no limite`() {
        assertFalse(InviteCodes.isExpired(expiresAt = 1000, now = 999))
        assertTrue(InviteCodes.isExpired(expiresAt = 1000, now = 1000))
    }
}
