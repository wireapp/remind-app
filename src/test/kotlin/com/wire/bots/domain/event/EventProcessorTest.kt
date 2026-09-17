package com.wire.bots.domain.event

import arrow.core.right
import com.wire.bots.domain.event.handlers.CommandHandler
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class EventProcessorTest {
    private val commandHandler = mockk<CommandHandler>()
    private val outgoingMessageRepository = mockk<OutgoingMessageRepository>()
    private val eventProcessor = EventProcessor(commandHandler, outgoingMessageRepository)

    @Test
    fun `given the handler throws, when processing a command, then the user is told`() {
        every { commandHandler.onEvent(any()) } throws
            RuntimeException("value too long for type character varying(250)")
        val message = slot<String>()
        every {
            outgoingMessageRepository.sendMessage(any(), capture(message))
        } returns Unit.right()

        eventProcessor.process(NEW_REMINDER)

        verify(exactly = 1) { outgoingMessageRepository.sendMessage(CONVERSATION_ID, any()) }
        assertTrue(message.captured.isNotBlank(), "the reply must not be empty")
    }

    @Test
    fun `given the handler throws, when processing a command, then the error is returned`() {
        val thrown = RuntimeException("boom")
        every { commandHandler.onEvent(any()) } throws thrown
        every { outgoingMessageRepository.sendMessage(any(), any()) } returns Unit.right()

        val result = eventProcessor.process(NEW_REMINDER)

        assertTrue(result.isLeft(), "a thrown failure must not be reported as success")
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "wire.com")
        val NEW_REMINDER = Command.NewReminder(
            conversationId = CONVERSATION_ID,
            requesterId = QualifiedId(UUID.randomUUID(), "wire.com"),
            task = "check this",
            schedule = "today at 14:00"
        )
    }
}
