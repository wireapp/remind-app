package com.wire.bots.domain.event.handlers

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.reminder.ConversationSettingsRepository
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.reminder.SupportedTimezones
import com.wire.bots.domain.reminder.getNextSchedules
import com.wire.bots.domain.usecase.DeleteReminderUseCase
import com.wire.bots.domain.usecase.ListRemindersInConversation
import com.wire.bots.domain.usecase.SaveReminderSchedule
import com.wire.bots.domain.usecase.SaveReminderSchedule.Companion.MAX_REMINDER_JOBS
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
    private val saveReminderSchedule: SaveReminderSchedule,
    private val listRemindersInConversation: ListRemindersInConversation,
    private val deleteReminder: DeleteReminderUseCase,
    private val usageMetrics: UsageMetrics,
    private val conversationSettingsRepository: ConversationSettingsRepository
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

            is Command.SetTimezone -> setTimezone(event)

            is Command.ShowTimezone -> showTimezone(event)
        }

        logger.info(
            "Event is processed successfully. Event: ${event::class.simpleName}, " +
                    "conversationId: ${event.conversationId}"
        )

        return result
    }

    private fun setTimezone(command: Command.SetTimezone): Either<Throwable, Unit> =
        conversationSettingsRepository
            .setTimezone(command.conversationId, command.zoneId)
            .flatMap {
                outgoingMessageRepository.sendMessage(
                    conversationId = command.conversationId,
                    messageContent = "🌍 Timezone for this conversation set to `${command.label}`."
                )
            }

    private fun showTimezone(command: Command.ShowTimezone): Either<Throwable, Unit> {
        val current = conversationSettingsRepository.getTimezone(command.conversationId)
        return outgoingMessageRepository.sendMessage(
            conversationId = command.conversationId,
            messageContent = "🌍 This conversation's timezone is currently set to `${current.id}`."
        )
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

    private fun handleNewReminder(command: Command.NewReminder): Either<Throwable, Unit> =
        // First, create the confirmation message. This can fail if the cron is invalid.
        BuildMsg
            .createReminderCreationConfirmationMessage(
                ReminderNextSchedule(
                    command.reminder,
                    command.reminder.getNextSchedules(MAX_REMINDER_JOBS)
                )
            ).flatMap { message ->
                // Only if the message is created successfully, save the reminder.
                saveReminderSchedule(command.reminder).flatMap {
                    outgoingMessageRepository.sendCompositeMessage(
                        conversationId = command.conversationId,
                        messageContent = message,
                        buttonList = createButton(text = "Delete", id = command.reminder.taskId)
                    )
                }
            }

    // TODO: add function to retrive single reminder by id

    private fun deleteReminder(command: Command.DeleteReminder): Either<Throwable, Unit> {
        val isButtonAction = command.referencedMessageId != null && command.senderId != null

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

    private fun sendReminderNotFoundMessage(command: Command.DeleteReminder) =
        outgoingMessageRepository.sendMessage(
            conversationId = command.conversationId,
            messageContent = "❌ The reminder with id '${command.reminderId}' was not found."
        )

    private fun sendButtonActionConfirmationMessage(command: Command.DeleteReminder) =
        outgoingMessageRepository.sendButtonActionConfirmation(
            conversationId = command.conversationId,
            referencedMessageId = command.referencedMessageId!!,
            sender = command.senderId!!,
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
        "1. You can create one time reminders, for example:\n" +
                "```\n" +
                "/remind to \"do something\" \"in 5 minutes\"\n" +
                "/remind to \"do something\" \"today at 21:00\"\n" +
                "/remind to \"do something\" \"18/09/2025 at 09:45\"\n" +
                "/remind to \"do something\" \"next monday at 17:00\"\n" +
                "```\n" +
                "2. You can also create recurring reminders, for example:\n" +
                "```\n" +
                "/remind to \"Start the daily stand up\" \"every day at 10:00\"\n" +
                "/remind to \"Start the weekly stand up\" \"every weekday at 10:00\"\n" +
                "/remind to \"Start the weekly stand up\" \"every Monday at 10:00\"\n" +
                "/remind to \"Start the weekly stand up\" \"every MON, Tue, Friday at 10:00\"\n" +
                "```\n" +
                "3. You can list all the active reminders in the conversation with the following command:\n" +
                "```\n" +
                "/remind list\n" +
                "```\n" +
                "Each reminder has a Delete button you can use to remove it.\n" +
                "4. All reminders use this conversation's timezone, which defaults to CET until someone sets it:\n" +
                "```\n" +
                "/remind set timezone <zone>\n" +
                "```\n" +
                "Supported values: ${SupportedTimezones.helpBlock()}\n" +
                "5. You can check which timezone this conversation is currently using with:\n" +
                "```\n" +
                "/remind show timezone\n" +
                "```"

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
        "🔔 “${reminder.task}” · ${scheduleText(reminder)} (ID: ${reminder.taskId})"

    fun createDeletedMessage(reminder: Reminder): String =
        "🗑️ Reminder deleted · “${reminder.task}” · ${scheduleText(reminder)}"

    private fun scheduleText(reminder: Reminder): String =
        when (reminder) {
            is Reminder.SingleReminder -> formatSchedule(reminder.scheduledAt, reminder.zoneId)
            is Reminder.RecurringReminder ->
                "${CronInterpreter.cronToText(reminder.scheduledCron)} (${reminder.zoneId})"
        }

    private fun formatSchedule(
        instant: Instant,
        zoneId: ZoneId
    ): String =
        DateTimeFormatter
            .ofPattern("EEE d MMM yyyy 'at' HH:mm z", Locale.ENGLISH)
            .withZone(zoneId)
            .format(instant)

    private fun formatSchedule(
        date: Date,
        zoneId: ZoneId
    ): String = formatSchedule(date.toInstant(), zoneId)

    val welcomeText =
        "👋 Hi, I'm the Remind App. Thanks for adding me to the conversation.\n" +
                "You can use me to create reminders for your conversations, or yourself.\n" +
                "I'm here to help make everyday work a little easier.\n" +
                "Choose a command to get started:\n" +
                helpMessage
}
