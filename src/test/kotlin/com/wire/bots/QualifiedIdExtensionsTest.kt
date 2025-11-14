package com.wire.bots

import com.wire.bots.infrastructure.utils.toQualifiedId
import com.wire.bots.infrastructure.utils.toRawString
import com.wire.sdk.model.QualifiedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class QualifiedIdExtensionsTest {
    @Test
    fun `toRawString should return correct string representation`() {
        val uuid = UUID.randomUUID()
        val domain = "example.com"
        val qualifiedId = QualifiedId(uuid, domain)
        val expected = "$uuid@$domain"
        assertEquals(expected, qualifiedId.toRawString())
    }

    @Test
    fun `toQualifiedId should parse valid string`() {
        val uuid = UUID.randomUUID()
        val domain = "example.com"
        val raw = "$uuid@$domain"
        val qualifiedId = raw.toQualifiedId()
        assertEquals(uuid, qualifiedId.id)
        assertEquals(domain, qualifiedId.domain)
    }

    @Test
    fun `toQualifiedId should throw for invalid format`() {
        val invalidRaw = "not-a-qualified-id"
        assertThrows(IllegalArgumentException::class.java) {
            invalidRaw.toQualifiedId()
        }
    }

    @Test
    fun `toQualifiedId should throw for invalid UUID`() {
        val invalidRaw = "not-a-uuid@example.com"
        assertThrows(IllegalArgumentException::class.java) {
            invalidRaw.toQualifiedId()
        }
    }
}
