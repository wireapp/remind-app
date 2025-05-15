package com.wire.bots.infrastructure.jobs

import arrow.core.flatMap
import arrow.core.raise.either
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.token.TokenRepository
import com.wire.bots.infrastructure.repository.DefaultReminderRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class ReminderTaskExecutor(
    val reminderRepository: DefaultReminderRepository,
    val tokenRepository: TokenRepository,
    val outgoingMessageRepository: OutgoingMessageRepository
) {
    @Transactional
    fun doWork(taskId: String) {
        val reminder = reminderRepository.find("taskId", taskId).singleResult()
        outgoingMessageRepository.sendMessage(
            reminder.conversationId,
            "token",
            reminder.task
        ).flatMap {
            either {
                if (!reminder.isEternal) {
                    reminderRepository.delete(reminder)
                }
            }
        }
    }
}
