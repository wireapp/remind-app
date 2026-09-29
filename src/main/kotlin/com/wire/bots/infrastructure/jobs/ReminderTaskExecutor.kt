package com.wire.bots.infrastructure.jobs

import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.usecase.DeleteRemindersInConversation
import com.wire.bots.infrastructure.repository.DefaultReminderRepository
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory

@ApplicationScoped
class ReminderTaskExecutor(
    val reminderRepository: DefaultReminderRepository,
    val outgoingMessageRepository: OutgoingMessageRepository,
    val deleteRemindersInConversation: DeleteRemindersInConversation
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun doWork(taskId: String) {
        val reminder = reminderRepository.find("taskId", taskId).singleResult()

        try {
            outgoingMessageRepository.sendMessage(
                conversationId = reminder.conversationId,
                messageContent = reminder.task
            )
        } catch (unreachable: WireException.ClientError) {
            if (!unreachable.isConversationGone()) throw unreachable

            // The conversation is gone and we never heard about it: the event was missed, most
            // likely because the app was down when it arrived. Clean up now, otherwise every
            // remaining schedule of this conversation fails the same way.
            logger.warn(
                "Conversation {} is no longer reachable ({}), deleting its reminders",
                reminder.conversationId,
                unreachable.response.label
            )
            purgeConversation(reminder.conversationId)
            return
        }

        if (!reminder.isEternal) {
            reminderRepository.delete(reminder)
        }
    }

    private fun purgeConversation(conversationId: QualifiedId) {
        deleteRemindersInConversation(conversationId).onLeft { error ->
            logger.error(
                "Failed to delete the reminders of unreachable conversation $conversationId",
                error
            )
        }
    }

    /**
     * The backend answers a conversation the app cannot reach with "Conversation access denied"
     * (403, the app was removed) or "Conversation not found" (404, it was deleted). Anything else
     * may well be temporary, so it is left to fail and be retried.
     */
    private fun WireException.ClientError.isConversationGone(): Boolean =
        response.code == FORBIDDEN || response.code == NOT_FOUND

    private companion object {
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
    }
}
