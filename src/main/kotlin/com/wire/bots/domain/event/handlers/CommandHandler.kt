package com.wire.bots.domain.event.handlers

import arrow.core.Either
import arrow.core.flatMap
import com.wire.bots.domain.DomainComponent
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.domain.reminder.ReminderNextSchedule
import com.wire.bots.domain.usecase.DeleteReminderUseCase
import com.wire.bots.domain.usecase.ListRemindersInConversation
import com.wire.bots.domain.usecase.SaveReminderSchedule
import com.wire.bots.infrastructure.utils.CronInterpreter
import com.wire.integrations.jvm.model.WireMessage

@DomainComponent
class CommandHandler(
    private val outgoingMessageRepository: OutgoingMessageRepository,
    private val saveReminderSchedule: SaveReminderSchedule,
    private val listRemindersInConversation: ListRemindersInConversation,
    private val deleteReminder: DeleteReminderUseCase
) : EventHandler<Command> {
    override fun onEvent(event: Command): Either<Throwable, Unit> =
        when (event) {
            is Command.LegacyHelp ->
                outgoingMessageRepository.sendMessage(
                    conversationId = event.conversationId,
                    messageContent = createLegacyHelpMessage()
                )

            is Command.Help ->
                outgoingMessageRepository.sendMessage(
                    conversationId = event.conversationId,
                    messageContent = createHelpMessage()
                )

            is Command.NewReminder -> handleNewReminder(event)
            is Command.ListReminders -> getReminderListMessages(event)
            is Command.DeleteReminder -> deleteReminder(event)
        }

    private fun getReminderListMessages(command: Command.ListReminders): Either<Throwable, Unit> =
        listRemindersInConversation(command.conversationId).flatMap { reminders ->
            (
                if (reminders.isEmpty()) {
                    outgoingMessageRepository.sendMessage(
                        conversationId = command.conversationId,
                        messageContent = "There are no reminders yet in this conversation."
                    )
                } else {
                    outgoingMessageRepository.sendMessage(
                        conversationId = command.conversationId,
                        messageContent = "The reminders in this conversation:\n"
                    )
                    reminders.forEach {
                        val message = "'${it.task}' at: ${
                            when (it) {
                                is Reminder.SingleReminder -> it.scheduledAt
                                is Reminder.RecurringReminder -> CronInterpreter.cronToText(
                                    it.scheduledCron
                                )
                            }
                        }"
                        val delButton: List<WireMessage.Button> = listOf(
                            WireMessage.Button(
                                text = "Delete",
                                id = it.taskId
                            )
                        )
                        outgoingMessageRepository.sendCompositeMessage(
                            conversationId = command.conversationId,
                            messageContent = message,
                            buttonList = delButton
                        )
                    }
                }
            ) as Either<Throwable, Unit>
        }

    private fun handleNewReminder(command: Command.NewReminder): Either<Throwable, Unit> =
        saveReminderSchedule(command.reminder).flatMap {
            outgoingMessageRepository.sendMessage(
                conversationId = command.conversationId,
                messageContent = getCreatedMessage(it)
            )
        }

    // TODO: add function to retrive single reminder by id
    private fun deleteReminder(command: Command.DeleteReminder): Either<Throwable, Unit> =
        listRemindersInConversation(command.conversationId).flatMap { reminders ->
            val reminder = reminders.find { it.taskId == command.reminderId }
            if (reminder != null) {
                deleteReminder.invoke(reminder.taskId, reminder.conversationId).flatMap {
                    outgoingMessageRepository.sendMessage(
                        conversationId = command.conversationId,
                        messageContent = "The reminder '${reminder.task}' was deleted."
                    )
                }
            } else {
                outgoingMessageRepository.sendMessage(
                    conversationId = command.conversationId,
                    messageContent = "❌ The reminder with id '${command.reminderId}' was not found."
                )
            }
        }

    private fun getCreatedMessage(reminderNextSchedule: ReminderNextSchedule): String =
        when (val reminder = reminderNextSchedule.reminder) {
            is Reminder.SingleReminder -> {
                "I will remind you to **'${reminder.task}'** at:\n" +
                    "**${reminderNextSchedule.nextSchedules.first()}**.\n" +
                    "If you want to delete it, you can use the command:\n" +
                    """
                    ```
                    /remind delete ${reminder.taskId}
                    ```
                    """.trimIndent()
            }

            is Reminder.RecurringReminder -> {
                "I will periodically remind you to **'${reminder.task}'**.\n" +
                    "If you want to delete it, you can use the command:\n" +
                    """
                    ```
                    /remind delete ${reminder.taskId}
                    ```
                    """.trimIndent() +
                    "\nThe next ${reminderNextSchedule.nextSchedules.size} " +
                    "schedules for the reminder is:\n" +
                    reminderNextSchedule.nextSchedules.joinToString("\n") {
                        "- $it"
                    }
            }
        }

    companion object {
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
    }
}
