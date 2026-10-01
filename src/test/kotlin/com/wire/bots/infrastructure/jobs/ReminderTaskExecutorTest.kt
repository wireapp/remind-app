package com.wire.bots.infrastructure.jobs

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.infrastructure.repository.DefaultReminderRepository
import com.wire.bots.infrastructure.repository.ReminderEntity
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.StandardError
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.quarkus.hibernate.orm.panache.kotlin.PanacheQuery
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * A reminder is only consumed once it is delivered. A failed send leaves it in place, whether the
 * send throws the failure or returns it: deleting reminders is left to the conversation events.
 */
class ReminderTaskExecutorTest {
    private val reminderRepository = mockk<DefaultReminderRepository>()
    private val outgoingMessageRepository = mockk<OutgoingMessageRepository>()
    private val executor = ReminderTaskExecutor(
        reminderRepository = reminderRepository,
        outgoingMessageRepository = outgoingMessageRepository
    )

    @Test
    fun `given a one-off reminder, when it is sent, then it is deleted`() {
        val reminder = givenReminder(isEternal = false)
        givenSendReturns(Unit.right())

        executor.doWork(TASK_ID)

        verify(exactly = 1) { reminderRepository.delete(reminder) }
    }

    @Test
    fun `given a recurring reminder, when it is sent, then it is kept`() {
        givenReminder(isEternal = true)
        givenSendReturns(Unit.right())

        executor.doWork(TASK_ID)

        verify(exactly = 0) { reminderRepository.delete(any<ReminderEntity>()) }
    }

    @Test
    fun `given the app was removed, when sending, then the reminder is kept without failing`() {
        givenReminder(isEternal = true)
        givenSendThrows(clientError(code = 403, label = "access-denied"))

        executor.doWork(TASK_ID)

        verify(exactly = 0) { reminderRepository.delete(any<ReminderEntity>()) }
    }

    @Test
    fun `given the conversation was deleted, when sending, then the reminder is kept`() {
        givenReminder(isEternal = false)
        givenSendThrows(clientError(code = 404, label = "no-conversation"))

        executor.doWork(TASK_ID)

        // Only the conversation-deleted event deletes it, not a failed send.
        verify(exactly = 0) { reminderRepository.delete(any<ReminderEntity>()) }
    }

    @Test
    fun `given a returned gone conversation, when sending, then the reminder is kept`() {
        givenReminder(isEternal = false)
        givenSendReturns(clientError(code = 404, label = "no-conversation").left())

        executor.doWork(TASK_ID)

        verify(exactly = 0) { reminderRepository.delete(any<ReminderEntity>()) }
    }

    @Test
    fun `given an unrelated 403, when sending, then it is rethrown and nothing is deleted`() {
        givenReminder(isEternal = false)
        val error = clientError(code = 403, label = "missing-legalhold-consent")
        givenSendThrows(error)

        val thrown = assertThrows<WireException.ClientError> { executor.doWork(TASK_ID) }

        assertSame(error, thrown)
        verify(exactly = 0) { reminderRepository.delete(any<ReminderEntity>()) }
    }

    @Test
    fun `given a returned failure, when sending, then it is rethrown and nothing is deleted`() {
        givenReminder(isEternal = false)
        val error = WireException.ServerError(
            response = StandardError(code = 500, label = "server-error", message = "Oops"),
            throwable = null
        )
        givenSendReturns(error.left())

        val thrown = assertThrows<WireException.ServerError> { executor.doWork(TASK_ID) }

        // Before, a returned failure would have counted as delivered and deleted the reminder.
        assertSame(error, thrown)
        verify(exactly = 0) { reminderRepository.delete(any<ReminderEntity>()) }
    }

    private fun givenReminder(isEternal: Boolean): ReminderEntity {
        val reminder = ReminderEntity(
            conversationId = CONVERSATION_ID,
            taskId = TASK_ID,
            task = "water the plants",
            zoneId = ZoneId.of("UTC"),
            scheduledAt = if (isEternal) null else Instant.now(),
            scheduledCron = if (isEternal) "0 0 9 * * ?" else null,
            isEternal = isEternal
        )
        val query = mockk<PanacheQuery<ReminderEntity>>()
        every { query.singleResult() } returns reminder
        every { reminderRepository.find("taskId", TASK_ID) } returns query
        every { reminderRepository.delete(any<ReminderEntity>()) } just runs
        return reminder
    }

    private fun givenSendReturns(result: Either<Throwable, Unit>) {
        every {
            outgoingMessageRepository.sendMessage(CONVERSATION_ID, any())
        } returns result
    }

    private fun givenSendThrows(error: Throwable) {
        every { outgoingMessageRepository.sendMessage(CONVERSATION_ID, any()) } throws error
    }

    private fun clientError(
        code: Int,
        label: String
    ) = WireException.ClientError(
        response = StandardError(code = code, label = label, message = label),
        throwable = null
    )

    private companion object {
        const val TASK_ID = "a-task"
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "wire.com")
    }
}
