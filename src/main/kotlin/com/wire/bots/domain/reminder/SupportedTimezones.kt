package com.wire.bots.domain.reminder

import java.time.ZoneId

/**
 * The only timezone labels this app accepts for `/remind set timezone`.
 *
 * EST, CST, MST and PST map internally to their DST-aware zone IDs
 * (e.g. EST5EDT) since plain "EST" is not a valid Java timezone ID on its own.
 * CET, MET, WET, EET, GMT and UTC map to themselves directly.
 * The remaining labels map to real IANA zone identifiers.
 */
object SupportedTimezones {
    private val ALIASES: Map<String, String> = mapOf(
        "UTC" to "UTC",
        "GMT" to "GMT",
        "CET" to "CET",
        "MET" to "MET",
        "WET" to "WET",
        "EET" to "EET",
        "EST" to "EST5EDT",
        "CST" to "CST6CDT",
        "MST" to "MST7MDT",
        "PST" to "PST8PDT",
        "BST" to "Europe/London",
        "IST" to "Asia/Kolkata",
        "JST" to "Asia/Tokyo",
        "AEST" to "Australia/Sydney",
        "SGT" to "Asia/Singapore",
        "HKT" to "Asia/Hong_Kong",
        "HST" to "Pacific/Honolulu",
        "NZST" to "Pacific/Auckland"
    )

    val LABELS: Set<String> = ALIASES.keys

    fun resolve(label: String): ZoneId? = ALIASES[label.trim().uppercase()]?.let { ZoneId.of(it) }

    /**
     * Comma-separated list of accepted labels, for embedding inside a code block.
     */
    fun helpBlock(): String = LABELS.sorted().joinToString(", ")
}
