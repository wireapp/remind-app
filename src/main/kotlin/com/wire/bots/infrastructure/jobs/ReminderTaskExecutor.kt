package com.wire.bots.infrastructure.jobs

import arrow.core.Either
import arrow.core.flatten
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.infrastructure.repository.DefaultReminderRepository
import com.wire.sdk.exception.WireException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory

@ApplicationScoped
class ReminderTaskExecutor(
    val reminderRepository: DefaultReminderRepository,
    val outgoingMessageRepository: OutgoingMessageRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun doWork(taskId: String) {
        val reminder = reminderRepository.find("taskId", taskId).singleResult()

        Either
            .catch {
                outgoingMessageRepository.sendMessage(
                    conversationId = reminder.conversationId,
                    messageContent = reminder.task
                )
            }.flatten()
            .fold(
                ifLeft = { error ->
                    if (error !is WireException.ClientError || !error.isConversationGone()) {
                        throw error
                    }

                    logger.warn(
                        "Conversation {} is no longer reachable ({}), keeping the reminder " +
                            "until its conversation event cleans it up",
                        reminder.conversationId,
                        error.response.label
                    )
                },
                ifRight = {
                    if (!reminder.isEternal) {
                        reminderRepository.delete(reminder)
                    }
                }
            )
    }

    /**
     * The backend answers a conversation the app cannot reach with "Conversation access denied"
     * (403 access-denied, the app was removed) or "Conversation not found" (404 no-conversation,
     * it was deleted). The status alone is not enough: 403 is also returned for unrelated
     * permission or policy reasons, which have to surface as failures. Anything else is left to
     * fail.
     */
    private fun WireException.ClientError.isConversationGone(): Boolean =
        (response.code == FORBIDDEN && response.label == ACCESS_DENIED) ||
            (response.code == NOT_FOUND && response.label == NO_CONVERSATION)

    private companion object {
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val ACCESS_DENIED = "access-denied"
        const val NO_CONVERSATION = "no-conversation"
    }
}
