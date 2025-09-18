package com.wire.bots.infrastructure.utils

object CronInterpreter {
    private const val CRON_PARTS_SIZE = 6
    private const val CRON_SECOND_INDEX = 0
    private const val CRON_MINUTE_INDEX = 1
    private const val CRON_HOUR_INDEX = 2
    private const val CRON_DAY_INDEX = 3
    private const val CRON_MONTH_INDEX = 4
    private const val CRON_WEEKDAY_INDEX = 5

    fun cronToText(cron: String): String {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size < CRON_PARTS_SIZE) return "(invalid cron)"

        return when {
            isEveryHour(parts) -> "every hour"
            isEveryDayAtHour(parts) -> "every day at ${formatHour(parts)}:00"
            isEveryDay(parts) -> "every day at ${formatHour(parts)}:${formatMinute(parts)}"
            isEveryWeek(parts) -> "every week on ${parts[CRON_WEEKDAY_INDEX]} at " +
                "${formatHour(parts)}:${formatMinute(parts)}"
            isEveryMonth(parts) -> "every month on day ${parts[CRON_DAY_INDEX]} at " +
                "${formatHour(parts)}:${formatMinute(parts)}"
            else -> "every (custom cron: $cron)"
        }
    }

    fun textToCron(text: String): String {
        val lower = text.trim().lowercase()
        val timeRegex = Regex("(\\d{1,2}):(\\d{2})")
        val timeMatch = timeRegex.find(lower)
        val (hour, minute) = timeMatch?.destructured?.let {
            it.component1() to it.component2()
        } ?: ("10" to "00")

        val dayMap = mapOf(
            "mon" to "MON",
            "monday" to "MON",
            "tue" to "TUE",
            "tuesday" to "TUE",
            "wed" to "WED",
            "wednesday" to "WED",
            "thu" to "THU",
            "thursday" to "THU",
            "fri" to "FRI",
            "friday" to "FRI",
            "sat" to "SAT",
            "saturday" to "SAT",
            "sun" to "SUN",
            "sunday" to "SUN"
        )

        val days = Helpers.extractDays(lower, dayMap)
        if (days != null) {
            return "0 $minute $hour ? * $days"
        }

        return Helpers.mapTextToCron(lower, hour, minute)
    }

    private fun isEveryHour(parts: List<String>) =
        parts[CRON_HOUR_INDEX] == "*" &&
            parts[CRON_DAY_INDEX] == "*" &&
            parts[CRON_MONTH_INDEX] == "*" &&
            parts[CRON_WEEKDAY_INDEX] == "*"

    private fun isEveryDayAtHour(parts: List<String>) =
        parts[CRON_SECOND_INDEX] == "0" &&
            parts[CRON_MINUTE_INDEX] == "0" &&
            parts[CRON_DAY_INDEX] == "?" &&
            parts[CRON_MONTH_INDEX] == "*" &&
            parts[CRON_WEEKDAY_INDEX] == "*"

    private fun isEveryDay(parts: List<String>) =
        (parts[CRON_DAY_INDEX] == "*" && parts[CRON_WEEKDAY_INDEX] == "?") ||
            (parts[CRON_DAY_INDEX] == "?" && parts[CRON_WEEKDAY_INDEX] == "*")

    private fun isEveryMonth(parts: List<String>) =
        parts[CRON_MONTH_INDEX] == "*" &&
            parts[CRON_WEEKDAY_INDEX] == "*"

    private fun isEveryWeek(parts: List<String>) =
        parts[CRON_WEEKDAY_INDEX] != "*" && parts[CRON_WEEKDAY_INDEX] != "?"

    private fun formatHour(parts: List<String>) = parts[CRON_HOUR_INDEX].padStart(2, '0')

    private fun formatMinute(parts: List<String>) = parts[CRON_MINUTE_INDEX].padStart(2, '0')

    private object Helpers {
        private const val DAY_NAME_LENGTH = 3

        /**
         * Extracts days from a string like "every mon, tue, wed" or "every monday, tuesday, wednesday".
         * Returns a comma-separated string of the corresponding cron day abbreviations.
         */
        fun extractDays(
            lower: String,
            dayMap: Map<String, String>
        ): String? {
            // Find the 'every ...' part and extract day tokens
            val everyPattern = Regex("every ([^@\\d]+)", RegexOption.IGNORE_CASE)
            val match = everyPattern.find(lower)
            val daysRaw = match?.groupValues?.get(1) ?: return null
            // Split by comma or space, trim, and filter out empty
            val tokens = daysRaw
                .split(",", " ")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
            // Map both short and full names, deduplicate, preserve order
            val seen = mutableSetOf<String>()
            val cronDays = tokens
                .mapNotNull { token ->
                    val key = if (token.length >
                        DAY_NAME_LENGTH
                    ) {
                        token.take(DAY_NAME_LENGTH)
                    } else {
                        token
                    }
                    dayMap[key] ?: dayMap[token]
                }.filter { seen.add(it) }
            return if (cronDays.isNotEmpty()) cronDays.joinToString(",") else null
        }

        fun mapTextToCron(
            lower: String,
            hour: String,
            minute: String
        ): String =
            when {
                lower.contains("every day") -> "0 $minute $hour ? * *"
                lower.contains("every weekday") -> "0 $minute $hour ? * MON-FRI"
                lower.contains("every monday") -> "0 $minute $hour ? * MON"
                lower.contains("every tuesday") -> "0 $minute $hour ? * TUE"
                lower.contains("every wednesday") -> "0 $minute $hour ? * WED"
                lower.contains("every thursday") -> "0 $minute $hour ? * THU"
                lower.contains("every friday") -> "0 $minute $hour ? * FRI"
                lower.contains("every saturday") -> "0 $minute $hour ? * SAT"
                lower.contains("every sunday") -> "0 $minute $hour ? * SUN"
                else -> throw IllegalArgumentException("Unsupported schedule: $lower")
            }
    }
}
