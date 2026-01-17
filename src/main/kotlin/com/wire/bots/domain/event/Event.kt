package com.wire.bots.domain.event

import com.wire.bots.domain.reminder.Reminder
import com.wire.sdk.model.QualifiedId
import java.util.UUID

sealed class Command(
    open val conversationId: QualifiedId
) {
    /**
     * Help event for bot usage
     */
    data class Help(
        override val conversationId: QualifiedId
    ) : Command(conversationId)

    /**
     * New reminder event, for the target conversation.
     */
    data class NewReminder(
        override val conversationId: QualifiedId,
        val reminder: Reminder
    ) : Command(conversationId)

    /**
     * List reminders event, for the target conversation.
     */
    data class ListReminders(
        override val conversationId: QualifiedId
    ) : Command(conversationId)

    /**
     * Delete reminder event, for the target conversation.
     */
    data class DeleteReminder(
        override val conversationId: QualifiedId,
        val reminderId: String,
        val referencedMessageId: String? = null,
        val senderId: QualifiedId? = null
    ) : Command(conversationId)
}

sealed class BotError(
    open val conversationId: QualifiedId,
    open val reason: String = "Core error"
) : Exception() {
    /**
     * An event that can be ignored-skipped by the bot
     * For example, user added to the conversation, mentions, etc.
     *
     * This event should be logged, but not processed.
     */
    data object Skip : BotError(
        conversationId = QualifiedId(UUID(0, 0), ""),
        reason = "Skip event"
    ) {
        override val conversationId: QualifiedId
            get() = error("Skip event: no conversationId available")
        override val reason: String
            get() = error("Skip event: no reason available")
    }

    /**
     * Unknown event, or error while parsing the event by the bot.o
     */
    data class Unknown(
        override val conversationId: QualifiedId,
        override val reason: String = "Unknown event"
    ) : BotError(conversationId, reason)

    /**
     * Error while processing the reminder.
     */
    data class ReminderError(
        override val conversationId: QualifiedId,
        val errorType: ErrorType
    ) : BotError(conversationId, errorType.message)

    enum class ErrorType(
        val message: String
    ) {
        DATE_IN_PAST("❌ Reminder date is in the past. Please provide a date in the future."),
        INCREMENT_IN_TIMEUNIT(
            "❌ Increment in time units is not allowed, try again with days, weeks or greater."
        ),
        PARSE_ERROR(
            "❌ I'm sorry, I didn't catch that. I can get a little confused at times. " +
                "Please try again with a different format or see examples with `/remind help`."
        ),
        EMPTY_REMINDER_TASK(
            "❌ Reminder message can't be empty. Please provide what you want to be reminded about."
        ),
        INVALID_REMINDER_ID(
            "❌ Invalid reminder ID. Please provide a valid reminder ID to delete."
        ),
        INVALID_REMINDER_USAGE(
            "❌ Invalid reminder usage. Please use the correct format:\n" +
                """
                ```
                /remind to "What" "When"
                ```
                For more examples, check the help command:
                ```
                /remind help
                ```
                """.trimIndent()
        )
    }
}
