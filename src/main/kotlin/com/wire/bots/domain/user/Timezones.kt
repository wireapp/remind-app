package com.wire.bots.domain.user

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The timezone identifiers the app accepts, for example "Europe/Berlin".
 *
 * Only identifiers known to the JVM tz database are accepted, so a reminder is never scheduled
 * against a zone we cannot resolve.
 */
object Timezones {
    private val OFFSET: DateTimeFormatter = DateTimeFormatter.ofPattern("ZZZZ")

    private val byLowercaseId: Map<String, ZoneId> =
        ZoneId.getAvailableZoneIds().associateBy({ it.lowercase() }, ZoneId::of)

    /**
     * Returns the [ZoneId] for [input], or `null` when it is not a known timezone identifier.
     * The lookup is case-insensitive, so "europe/berlin" is accepted as well.
     */
    fun parse(input: String): ZoneId? = byLowercaseId[input.trim().lowercase()]

    /**
     * The offset [zoneId] is currently on, for example "GMT+02:00". Shown back to the user so
     * they can sanity check the timezone they picked.
     */
    fun currentOffsetOf(zoneId: ZoneId): String = OFFSET.format(ZonedDateTime.now(zoneId))
}
