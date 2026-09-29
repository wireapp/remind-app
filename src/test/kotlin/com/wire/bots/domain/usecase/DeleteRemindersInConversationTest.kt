package com.wire.bots.domain.usecase

import arrow.core.left
import arrow.core.right
import com.wire.bots.domain.reminder.ReminderJobRepository
import com.wire.bots.domain.reminder.ReminderRepository
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DeleteRemindersInConversationTest {
    private val reminderRepository = mockk<ReminderRepository>()
    private val reminderJobRepository = mockk<ReminderJobRepository>()
    private val deleteRemindersInConversation = DeleteRemindersInConversation(
        reminderRepository = reminderRepository,
        reminderJobRepository = reminderJobRepository
    )

    @Test
    fun `given a conversation, when deleting its reminders, then jobs go before the rows`() {
        every {
            reminderJobRepository.cancelAllReminderJobsInConversation(CONVERSATION_ID)
        } returns Unit.right()
        every {
            reminderRepository.deleteRemindersByConversationId(CONVERSATION_ID)
        } returns 3L.right()

        val result = deleteRemindersInConversation(CONVERSATION_ID)

        // A job whose reminder is already gone fails when it fires, so it has to go first.
        verifyOrder {
            reminderJobRepository.cancelAllReminderJobsInConversation(CONVERSATION_ID)
            reminderRepository.deleteRemindersByConversationId(CONVERSATION_ID)
        }
        assertEquals(3L, result.getOrNull(), "the number of deleted reminders must be reported")
    }

    @Test
    fun `given cancelling the jobs fails, when deleting, then the rows are still deleted`() {
        every {
            reminderJobRepository.cancelAllReminderJobsInConversation(CONVERSATION_ID)
        } returns RuntimeException("quartz is having a bad day").left()
        every {
            reminderRepository.deleteRemindersByConversationId(CONVERSATION_ID)
        } returns 2L.right()

        val result = deleteRemindersInConversation(CONVERSATION_ID)

        verify(exactly = 1) {
            reminderRepository.deleteRemindersByConversationId(CONVERSATION_ID)
        }
        assertTrue(result.isLeft(), "a failed cancellation must not be reported as success")
    }

    @Test
    fun `given deleting the rows fails, when deleting, then the failure is returned`() {
        every {
            reminderJobRepository.cancelAllReminderJobsInConversation(CONVERSATION_ID)
        } returns Unit.right()
        every {
            reminderRepository.deleteRemindersByConversationId(CONVERSATION_ID)
        } returns RuntimeException("boom").left()

        val result = deleteRemindersInConversation(CONVERSATION_ID)

        assertTrue(result.isLeft(), "a failed deletion must not be reported as success")
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "wire.com")
    }
}
