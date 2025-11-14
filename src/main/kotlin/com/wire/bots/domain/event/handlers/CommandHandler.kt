package com.wire.bots.domain.event.handlers

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.reminder.getNextSchedules
import com.wire.bots.domain.usecase.DeleteReminderUseCase
import com.wire.bots.domain.usecase.ListRemindersInConversation
import com.wire.bots.domain.usecase.SaveReminderSchedule
import com.wire.bots.domain.usecase.SaveReminderSchedule.Companion.MAX_REMINDER_JOBS
import com.wire.bots.infrastructure.utils.CronInterpreter
import com.wire.integrations.jvm.model.WireMessage
import org.slf4j.LoggerFactory
import java.util.UUID

@DomainComponent
class CommandHandler(
    private val outgoingMessageRepository: OutgoingMessageRepository,
    private val saveReminderSchedule: SaveReminderSchedule,
    private val listRemindersInConversation: ListRemindersInConversation,
    private val deleteReminder: DeleteReminderUseCase
) : EventHandler<Command> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onEvent(event: Command): Either<Throwable, Unit> {
        logger.info(
            "Event will be processed. Event: ${event::class.simpleName}, " +
                "conversationId: ${event.conversationId}"
        )

        val result = when (event) {
            is Command.LegacyHelp ->
                outgoingMessageRepository.sendMessage(
                    conversationId = event.conversationId,
                    messageContent = BuildMsg.createLegacyHelpMessage()
                )

            is Command.Help ->
                outgoingMessageRepository.sendMessage(
                    conversationId = event.conversationId,
                    messageContent = BuildMsg.createHelpMessage()
                )

            is Command.NewReminder -> handleNewReminder(event)
            is Command.ListReminders -> getReminderListMessages(event)
            is Command.DeleteReminder -> deleteReminder(event)
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
                        val confirmationText = "The reminder '${reminder.task}' was deleted."
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
    fun createLegacyHelpMessage(): String =
        "**Hi, I\\'m the Remind App.**\nPlease use my specific help command\n" +
            "```\n/remind help\n```\n"

    fun createHelpMessage(): String =
        """
            **Hi, I'm the Remind App.**
            **I can help you to create reminders for your conversations, or yourself.**
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
            4. You can delete a reminder with the following command:
            (Get the <reminderId> from the `/remind list` command)
            ```
            /remind delete <reminderId>
            ```
        """.trimIndent()

    fun createReminderCreationConfirmationMessage(
        reminderNextSchedule: ReminderNextSchedule
    ): Either<Throwable, String> =
        Either.catch {
            when (val reminder = reminderNextSchedule.reminder) {
                is Reminder.SingleReminder -> {
                    "I will remind you to **'${reminder.task}'** at:\n" +
                        "**${reminderNextSchedule.nextSchedules.first()}**.\n"
                }

                is Reminder.RecurringReminder -> {
                    "I will periodically remind you to **'${reminder.task}'**.\n" +
                        "\nThe next ${reminderNextSchedule.nextSchedules.size} " +
                        "schedules for the reminder is:\n" +
                        reminderNextSchedule.nextSchedules.joinToString("\n") {
                            "- $it"
                        }
                }
            }
        }

    fun createListMessage(reminder: Reminder): String {
        val message = "'${reminder.task}' at: ${
            when (reminder) {
                is Reminder.SingleReminder -> reminder.scheduledAt
                is Reminder.RecurringReminder -> CronInterpreter.cronToText(
                    reminder.scheduledCron
                )
            }
        }"
        return message
    }
}
