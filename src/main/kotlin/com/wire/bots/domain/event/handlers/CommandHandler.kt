package com.wire.bots.domain.event.handlers

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.usecase.CreateReminder
import com.wire.bots.domain.usecase.DeleteReminderUseCase
import com.wire.bots.domain.usecase.ListRemindersInConversation
import com.wire.bots.domain.user.Timezones
import com.wire.bots.infrastructure.utils.CronInterpreter
import com.wire.bots.infrastructure.utils.UsageMetrics
import com.wire.sdk.model.WireMessage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

@DomainComponent
class CommandHandler(
    private val outgoingMessageRepository: OutgoingMessageRepository,
    private val timezoneCommandHandler: TimezoneCommandHandler,
    private val createReminder: CreateReminder,
    private val listRemindersInConversation: ListRemindersInConversation,
    private val deleteReminder: DeleteReminderUseCase,
    private val usageMetrics: UsageMetrics
) : EventHandler<Command> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onEvent(event: Command): Either<Throwable, Unit> {
        logger.info(
            "Event will be processed. Event: ${event::class.simpleName}, " +
                "conversationId: ${event.conversationId}"
        )

        val result = when (event) {
            is Command.Help -> {
                usageMetrics.onHelpCommand()
                outgoingMessageRepository.sendMessage(
                    conversationId = event.conversationId,
                    messageContent = BuildMsg.helpMessage
                )
            }

            is Command.NewReminder -> {
                usageMetrics.onCreateCommand()
                handleNewReminder(event)
            }

            is Command.ListReminders -> {
                usageMetrics.onListCommand()
                getReminderListMessages(event)
            }

            is Command.DeleteReminder -> {
                usageMetrics.onDeleteCommand()
                deleteReminder(event)
            }

            is Command.SetTimezone -> {
                usageMetrics.onSetTimezoneCommand()
                timezoneCommandHandler.setTimezone(event)
            }
        }

        logger.info(
            "Event is processed successfully. Event: ${event::class.simpleName}, " +
                "conversationId: ${event.conversationId}"
        )

        return result
    }

    private fun getReminderListMessages(command: Command.ListReminders): Either<Throwable, Unit> =
        listRemindersInConversation(command.conversationId).flatMap { reminders ->
            (
                if (reminders.isEmpty()) {
                    sendNoRemindersInConversationMessage(command)
                } else {
                    sendListRemindersReply(command, reminders)
                }
            )
        }

    /**
     * Creates the reminder, unless it needs a timezone we don't have yet: in that case nothing is
     * scheduled and the user is asked for their timezone first.
     */
    private fun handleNewReminder(command: Command.NewReminder): Either<Throwable, Unit> =
        createReminder(command).flatMap { result ->
            when (result) {
                is CreateReminder.Result.TimezoneMissing ->
                    timezoneCommandHandler.requestTimezone(
                        conversationId = command.conversationId,
                        requesterId = command.requesterId
                    )

                is CreateReminder.Result.Created ->
                    sendReminderCreatedMessage(command, result.schedule)
            }
        }

    private fun sendReminderCreatedMessage(
        command: Command.NewReminder,
        schedule: ReminderNextSchedule
    ): Either<Throwable, Unit> =
        BuildMsg
            .createReminderCreationConfirmationMessage(schedule)
            .flatMap { message ->
                outgoingMessageRepository.sendCompositeMessage(
                    conversationId = command.conversationId,
                    messageContent = message,
                    buttonList = createButton(text = "Delete", id = schedule.reminder.taskId)
                )
            }

    // TODO: add function to retrive single reminder by id

    private fun deleteReminder(command: Command.DeleteReminder): Either<Throwable, Unit> {
        val isButtonAction = command.referencedMessageId != null

        val confirmationSent = if (isButtonAction) {
            sendButtonActionConfirmationMessage(command)
        } else {
            Either.Right(Unit)
        }

        return confirmationSent.flatMap {
            listRemindersInConversation(command.conversationId).flatMap { reminders ->
                val reminder = reminders.find { it.taskId == command.reminderId }
                if (reminder != null) {
                    deleteReminder.invoke(reminder.taskId, reminder.conversationId).flatMap {
                        val confirmationText = BuildMsg.createDeletedMessage(reminder)
                        if (isButtonAction) {
                            // Edit the original message to indicate deletion
                            outgoingMessageRepository.editCompositeMessage(
                                replacingMessageId = UUID.fromString(command.referencedMessageId),
                                conversationId = command.conversationId,
                                messageContent = confirmationText,
                                buttonList = emptyList()
                            )
                        } else {
                            // Send a new message to indicate deletion
                            outgoingMessageRepository.sendMessage(
                                conversationId = command.conversationId,
                                messageContent = confirmationText
                            )
                        }
                    }
                } else {
                    sendReminderNotFoundMessage(command)
                }
            }
        }
    }

    private fun sendListRemindersReply(
        command: Command.ListReminders,
        reminders: List<Reminder>
    ): Either<Throwable, Unit> =
        outgoingMessageRepository
            .sendMessage(
                conversationId = command.conversationId,
                messageContent = "The reminders in this conversation:\n"
            ).flatMap {
                reminders.fold(
                    Either.Right(Unit) as Either<Throwable, Unit>
                ) { acc, reminder ->
                    acc.flatMap {
                        outgoingMessageRepository.sendCompositeMessage(
                            conversationId = command.conversationId,
                            messageContent = BuildMsg.createListMessage(reminder),
                            buttonList = createButton(text = "Delete", id = reminder.taskId)
                        )
                    }
                }
            }

    private fun sendNoRemindersInConversationMessage(command: Command.ListReminders) =
        outgoingMessageRepository.sendMessage(
            conversationId = command.conversationId,
            messageContent = "There are no reminders yet in this conversation."
        )

    private fun sendReminderNotFoundMessage(
        command: Command.DeleteReminder
    ): Either<Throwable, Unit> {
        // The id is only useful for debugging, users never see it.
        logger.info(
            "Reminder to delete was not found. reminderId: ${command.reminderId}, " +
                "conversationId: ${command.conversationId}"
        )
        return outgoingMessageRepository.sendMessage(
            conversationId = command.conversationId,
            messageContent = "❌ This reminder no longer exists — it may have already been deleted."
        )
    }

    private fun sendButtonActionConfirmationMessage(command: Command.DeleteReminder) =
        outgoingMessageRepository.sendButtonActionConfirmation(
            conversationId = command.conversationId,
            referencedMessageId = command.referencedMessageId!!,
            sender = command.senderId,
            buttonId = command.reminderId
        )

    private fun createButton(
        text: String,
        id: String
    ): List<WireMessage.Button> =
        listOf(
            WireMessage.Button(
                text = text,
                id = id
            )
        )
}

object BuildMsg {
    val helpMessage =
        """
            1. You can create one time reminders, for example:
            ```
            /remind to "do something" "in 5 minutes"
            /remind to "do something" "today at 21:00"
            /remind to "do something" "18/09/2025 at 09:45"
            /remind to "do something" "next monday at 17:00"
            ```
            2. You can also create recurring reminders, for example:
            ```
            /remind to "Start the daily stand up" "every day at 10:00"
            /remind to "Start the weekly stand up" "every weekday at 10:00"
            /remind to "Start the weekly stand up" "every Monday at 10:00"
            /remind to "Start the weekly stand up" "every MON, Tue, Friday at 10:00"
            ```
            3. You can list all the active reminders in the conversation with the following command:
            ```
            /remind list
            ```
            4. Each reminder in the `/remind list` response has a Delete button,
            use it to remove that reminder.
            5. The times you give me are read in your own timezone.
            Set it once, in our 1:1 chat, with:
            ```
            /remind set-timezone "Europe/Berlin"
            ```
        """.trimIndent()

    val timezoneRequestMessage =
        """
            🌍 I don't know your timezone yet, so I can't schedule that reminder correctly.

            Tell me your timezone with:
            ```
            /remind set-timezone "Europe/Berlin"
            ```
            Use the timezone name for your city, like `Europe/Berlin` or `America/Detroit`.

            Once it's set, please send your reminder command again.
        """.trimIndent()

    val timezoneRequestNotice =
        """
            🌍 I couldn't set that reminder because I don't know your timezone yet.
            I've sent you a direct message — set it there, then send this command again.
        """.trimIndent()

    val setTimezoneInPrivateMessage =
        """
            🌍 Your timezone is a personal setting, so let's keep it out of this conversation.
            Send me `/remind set-timezone "Europe/Berlin"` in our 1:1 chat instead.
        """.trimIndent()

    fun timezoneSavedMessage(zoneId: ZoneId): String =
        "✅ Timezone set to `$zoneId` (currently ${Timezones.currentOffsetOf(zoneId)}).\n" +
            "From now on I read the times you give me in this timezone."

    fun createReminderCreationConfirmationMessage(
        reminderNextSchedule: ReminderNextSchedule
    ): Either<Throwable, String> =
        Either.catch {
            when (val reminder = reminderNextSchedule.reminder) {
                is Reminder.SingleReminder -> {
                    "🔔 Reminder created · “${reminder.task}” · ${scheduleText(reminder)}"
                }

                is Reminder.RecurringReminder -> {
                    "🔔 Reminder created · “${reminder.task}” · ${scheduleText(reminder)}\n" +
                        "\nThe next ${reminderNextSchedule.nextSchedules.size} " +
                        "schedules for the reminder is:\n" +
                        reminderNextSchedule.nextSchedules.joinToString("\n") {
                            "- ${formatSchedule(it, reminder.zoneId)}"
                        }
                }
            }
        }

    fun createListMessage(reminder: Reminder): String =
        "🔔 “${reminder.task}” · ${scheduleText(reminder)}"

    fun createDeletedMessage(reminder: Reminder): String =
        "🗑️ Reminder deleted · “${reminder.task}” · ${scheduleText(reminder)}"

    /**
     * A schedule always carries the timezone it was created in, named the same way the user
     * named it, so a one-off and a recurring reminder read alike:
     *
     * - `Wed 9 Sep 2026 at 10:00 (Europe/Berlin)`
     * - `every day at 10:00 (Europe/Berlin)`
     */
    private fun scheduleText(reminder: Reminder): String {
        val schedule = when (reminder) {
            is Reminder.SingleReminder -> formatSchedule(reminder.scheduledAt, reminder.zoneId)
            is Reminder.RecurringReminder -> CronInterpreter.cronToText(reminder.scheduledCron)
        }
        return "$schedule (${reminder.zoneId})"
    }

    /**
     * Reminders are parsed and fired in the timezone their creator picked, so schedules are
     * rendered in that same zone. The zone itself is added by the caller, which knows whether
     * it was already named nearby.
     */
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy 'at' HH:mm", Locale.ENGLISH)

    private fun formatSchedule(
        instant: Instant,
        zoneId: ZoneId
    ): String = dateFormatter.withZone(zoneId).format(instant)

    private fun formatSchedule(
        date: Date,
        zoneId: ZoneId
    ): String = formatSchedule(date.toInstant(), zoneId)

    val welcomeText =
        "👋 Hi, I'm the Remind App. Thanks for adding me to the conversation.\n" +
            "You can use me to create reminders for your conversations, or yourself.\n" +
            "I'm here to help make everyday work a little easier.\n\n" +
            "Choose a command to get started:\n" +
            helpMessage
}
