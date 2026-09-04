package com.wire.bots.infrastructure

import com.wire.bots.domain.reminder.Reminder
import com.wire.bots.infrastructure.client.MessagePayload
import com.wire.bots.infrastructure.client.OutgoingMessage
import com.wire.bots.infrastructure.client.OutgoingMessageType
import com.wire.bots.infrastructure.repository.ReminderEntity
import java.time.ZoneId

fun Reminder.toEntity(): ReminderEntity =
    when (this) {
        is Reminder.RecurringReminder ->
            ReminderEntity(
                conversationId = this.conversationId,
                taskId = this.taskId,
                task = this.task,
                createdAt = this.createdAt,
                scheduledCron = this.scheduledCron,
                zoneId = this.zoneId.id,
                isEternal = true
            )

        is Reminder.SingleReminder ->
            ReminderEntity(
                conversationId = this.conversationId,
                taskId = this.taskId,
                task = this.task,
                scheduledAt = this.scheduledAt,
                createdAt = this.createdAt,
                zoneId = this.zoneId.id,
                isEternal = false
            )
    }

fun ReminderEntity.toDomain(): Reminder {
    val zone = ZoneId.of(this.zoneId)
    return when (isEternal) {
        true -> return Reminder.RecurringReminder(
            conversationId = this.conversationId,
            taskId = this.taskId,
            task = this.task,
            scheduledCron = this.scheduledCron ?: error(
                "scheduledCron is null for RecurringReminder"
            ),
            createdAt = this.createdAt,
            zoneId = zone
        )

        false ->
            Reminder.SingleReminder(
                conversationId = this.conversationId,
                taskId = this.taskId,
                task = this.task,
                scheduledAt = this.scheduledAt ?: error("scheduledAt is null for SingleReminder"),
                createdAt = this.createdAt,
                zoneId = zone
            )
    }
}

fun String.toOutgoingMessage(): OutgoingMessage =
    OutgoingMessage(
        type = OutgoingMessageType.Text,
        text = MessagePayload.Text(this)
    )
