package com.wire.bots.application

import arrow.core.left
import arrow.core.right
import com.wire.bots.domain.event.EventProcessor
import com.wire.bots.domain.usecase.DeleteRemindersInConversation
import com.wire.bots.infrastructure.utils.UsageMetrics
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The reminders of a conversation only make sense while the app can reach it, so the events that
 * take that access away have to delete them. [ReminderEventHandler.manager] is never touched by
 * these callbacks, so the handler can be built without a running SDK.
 */
class ReminderEventHandlerTest {
    private val eventProcessor = mockk<EventProcessor>()
    private val usageMetrics = mockk<UsageMetrics>(relaxed = true)
    private val deleteRemindersInConversation = mockk<DeleteRemindersInConversation>()
    private val handler = ReminderEventHandler(
        eventProcessor = eventProcessor,
        usageMetrics = usageMetrics,
        deleteRemindersInConversation = deleteRemindersInConversation,
        appUserId = APP_USER_ID
    )

    @Test
    fun `given a conversation is deleted, when handling the event, then its reminders go too`() {
        every { deleteRemindersInConversation(CONVERSATION_ID) } returns 2L.right()

        runBlocking { handler.onConversationDeleted(CONVERSATION_ID) }

        verify(exactly = 1) { deleteRemindersInConversation(CONVERSATION_ID) }
    }

    @Test
    fun `given the app is removed, when handling the leave event, then its reminders go too`() {
        every { deleteRemindersInConversation(CONVERSATION_ID) } returns 1L.right()

        runBlocking {
            handler.onUserLeftConversation(
                conversationId = CONVERSATION_ID,
                members = listOf(A_USER, QualifiedId(APP_USER_ID, "wire.com"))
            )
        }

        verify(exactly = 1) { deleteRemindersInConversation(CONVERSATION_ID) }
        verify(exactly = 1) { usageMetrics.onAppRemovedFromConversation() }
    }

    @Test
    fun `given a user leaves, when handling the leave event, then the reminders are kept`() {
        runBlocking {
            handler.onUserLeftConversation(
                conversationId = CONVERSATION_ID,
                members = listOf(A_USER, ANOTHER_USER)
            )
        }

        // The reminders belong to the conversation, not to whoever created them.
        verify(exactly = 0) { deleteRemindersInConversation(any()) }
        verify(exactly = 0) { usageMetrics.onAppRemovedFromConversation() }
    }

    @Test
    fun `given the app is on another domain, when it is removed, then its reminders go too`() {
        every { deleteRemindersInConversation(CONVERSATION_ID) } returns 1L.right()

        runBlocking {
            handler.onUserLeftConversation(
                conversationId = CONVERSATION_ID,
                members = listOf(QualifiedId(APP_USER_ID, "another.wire.com"))
            )
        }

        verify(exactly = 1) { deleteRemindersInConversation(CONVERSATION_ID) }
    }

    @Test
    fun `given the cleanup fails, when handling the event, then the failure is not thrown`() {
        every {
            deleteRemindersInConversation(CONVERSATION_ID)
        } returns RuntimeException("the database is gone as well").left()

        // There is no conversation left to report into, so a failure may only be logged.
        runBlocking { handler.onConversationDeleted(CONVERSATION_ID) }

        verify(exactly = 1) { deleteRemindersInConversation(CONVERSATION_ID) }
    }

    private companion object {
        val APP_USER_ID: UUID = UUID.randomUUID()
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "wire.com")
        val A_USER = QualifiedId(UUID.randomUUID(), "wire.com")
        val ANOTHER_USER = QualifiedId(UUID.randomUUID(), "wire.com")
    }
}
