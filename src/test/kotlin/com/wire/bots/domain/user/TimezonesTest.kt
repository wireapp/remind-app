package com.wire.bots.domain.user

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneId

class TimezonesTest {
    @Test
    fun `given a known timezone name, then it is accepted`() {
        assertEquals(ZoneId.of("Europe/Berlin"), Timezones.parse("Europe/Berlin"))
        assertEquals(ZoneId.of("Europe/Istanbul"), Timezones.parse("Europe/Istanbul"))
        assertEquals(Timezones.DEFAULT, Timezones.parse("UTC"))
    }

    @Test
    fun `given a known timezone name in another case or padded, then it is accepted`() {
        assertEquals(ZoneId.of("Europe/Berlin"), Timezones.parse("  europe/BERLIN "))
    }

    @Test
    fun `given something that is not a timezone, then it is rejected`() {
        assertNull(Timezones.parse("Mars/Olympus"))
        assertNull(Timezones.parse("berlin"))
        assertNull(Timezones.parse("UTC+2"))
        assertNull(Timezones.parse(""))
    }

    @Test
    fun `given a timezone, then its current offset is readable`() {
        assertEquals("GMT+05:30", Timezones.currentOffsetOf(ZoneId.of("Asia/Kolkata")))
        assertEquals("GMT", Timezones.currentOffsetOf(Timezones.DEFAULT))
    }
}
