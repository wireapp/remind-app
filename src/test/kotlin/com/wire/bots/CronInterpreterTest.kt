package com.wire.bots

import com.wire.bots.infrastructure.utils.CronInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CronInterpreterTest {
    @Test
    fun `should return every hour for valid hourly cron`() {
        val cron = "* * * * * *"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every hour", result)
    }

    @Test
    fun `should return every day at specific hour for valid daily cron`() {
        val cron = "0 0 10 ? * *"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every day at 10:00", result)
    }

    @Test
    fun `should return every day at specific time for valid daily cron`() {
        val cron = "0 30 14 ? * *"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every day at 14:30", result)
    }

    @Test
    fun `should return every month on specific day for valid monthly cron`() {
        val cron = "0 15 8 5 * *"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every month on day 5 at 08:15", result)
    }

    @Test
    fun `should return every week on specific day for valid weekly cron`() {
        val cron = "0 45 16 ? * MON"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every week on MON at 16:45", result)
    }

    @Test
    fun `should return every week on multiple days for valid weekly cron`() {
        val cron = "0 00 10 ? * MON,WED,FRI"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every week on MON,WED,FRI at 10:00", result)
    }

    @Test
    fun `should return every week on single day for valid weekly cron`() {
        val cron = "0 45 16 ? * MON"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every week on MON at 16:45", result)
    }

    @Test
    fun `should return every week on multiple days for valid weekly cron with mixed case`() {
        val cron = "0 15 8 ? * tue,THU,sat"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every week on tue,THU,sat at 08:15", result)
    }

    @Test
    fun `should return every week on multiple days for valid weekly cron with all days`() {
        val cron = "0 00 10 ? * MON,TUE,WED,THU,FRI,SAT,SUN"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every week on MON,TUE,WED,THU,FRI,SAT,SUN at 10:00", result)
    }

    @Test
    fun `should return every week on day range for valid weekly cron`() {
        val cron = "0 00 10 ? * MON-FRI"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every week on MON-FRI at 10:00", result)
    }

    @Test
    fun `should return custom cron for unhandled patterns`() {
        val cron = "0 0 12 1 1 ?"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("every (custom cron: $cron)", result)
    }

    @Test
    fun `should return invalid cron for malformed input`() {
        val cron = "0 0 12"
        val result = CronInterpreter.cronToText(cron)
        assertEquals("(invalid cron)", result)
    }

    @Test
    fun `should parse single short day`() {
        val cron = CronInterpreter.textToCron("every mon at 10:00")
        assertEquals("0 00 10 ? * MON", cron)
    }

    @Test
    fun `should parse single full day`() {
        val cron = CronInterpreter.textToCron("every Monday at 10:00")
        assertEquals("0 00 10 ? * MON", cron)
    }

    @Test
    fun `should parse multiple short days`() {
        val cron = CronInterpreter.textToCron("every mon,wed,fri at 10:00")
        assertEquals("0 00 10 ? * MON,WED,FRI", cron)
    }

    @Test
    fun `should parse multiple full days`() {
        val cron = CronInterpreter.textToCron("every Monday, Wednesday, Friday at 10:00")
        assertEquals("0 00 10 ? * MON,WED,FRI", cron)
    }

    @Test
    fun `should parse mixed short and full days`() {
        val cron = CronInterpreter.textToCron("every MON, tuesday, fri at 10:00")
        assertEquals("0 00 10 ? * MON,TUE,FRI", cron)
    }

    @Test
    fun `should deduplicate days and ignore case`() {
        val cron = CronInterpreter.textToCron("every MON, mon, Mon at 10:00")
        assertEquals("0 00 10 ? * MON", cron)
    }

    @Test
    fun `should parse every day`() {
        val cron = CronInterpreter.textToCron("every day at 10:00")
        assertEquals("0 00 10 ? * *", cron)
    }

    @Test
    fun `should parse every weekday`() {
        val cron = CronInterpreter.textToCron("every weekday at 10:00")
        assertEquals("0 00 10 ? * MON-FRI", cron)
    }

    @Test
    fun `should throw on invalid day`() {
        try {
            CronInterpreter.textToCron("every blursday at 10:00")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assert(true)
        }
    }
}
