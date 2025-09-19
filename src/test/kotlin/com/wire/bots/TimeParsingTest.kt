package com.wire.bots

import com.mdimension.jchronic.Chronic
import com.mdimension.jchronic.Options
import com.mdimension.jchronic.tags.Pointer
import com.wire.bots.infrastructure.utils.CronInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date

class TimeParsingTest {
    @Test
    fun givenTomorrowIsGiven_thenTheDateTimeShouldMatchForNowPlusOneDay() {
        // given
        val now = Date.from(Instant.now(Clock.systemUTC()))
        val expected = now.toInstant().plus(1, ChronoUnit.DAYS)

        // when
        val options = Options(Pointer.PointerType.FUTURE)
        options.now = Calendar
            .getInstance(
                java.util.TimeZone.getTimeZone("UTC")
            ).apply { time = now }
        val dateSpan = Chronic.parse("tomorrow", options)

        // then
        with(dateSpan.beginCalendar) {
            assertEquals(
                expected.truncatedTo(ChronoUnit.DAYS),
                toInstant().truncatedTo(ChronoUnit.DAYS)
            )
        }
    }

    @Test
    fun givenOneWeekIsGiven_thenTheDateTimeShouldMatchForNowPlus1Week() {
        // given
        val now = Date.from(Instant.now(Clock.systemDefaultZone()))
        val expected = now.toInstant().plus(7, ChronoUnit.DAYS)

        // when
        val dateSpan = Chronic.parse("in one week", JCHRONIC_OPTS)

        // then
        with(dateSpan.beginCalendar) {
            assertEquals(
                expected.truncatedTo(ChronoUnit.DAYS),
                toInstant().truncatedTo(ChronoUnit.DAYS)
            )
        }
    }

    @Test
    fun givenAnSpecificDateIsGiven_thenTheDateTimeShouldMatch() {
        // given
        val expected = LocalDateTime.of(2222, 11, 15, 10, 0)

        // when
        val dateSpan = Chronic.parse("15 November 2222 at 10am", JCHRONIC_OPTS)

        // then
        with(dateSpan.beginCalendar) {
            assertEquals(expected, toInstant().atZone(timeZone.toZoneId()).toLocalDateTime())
        }
    }

    @Test
    fun givenARecurringExpression_thenTheResultIsAValidCronExpression() {
        val parsed = CronInterpreter.textToCron("every monday at 10:00")
        assertEquals("0 00 10 ? * MON", parsed)
    }

    companion object {
        private val JCHRONIC_OPTS = Options(Pointer.PointerType.FUTURE)
    }
}
