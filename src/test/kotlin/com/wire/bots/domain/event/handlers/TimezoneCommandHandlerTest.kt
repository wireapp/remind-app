package com.wire.bots.domain.event.handlers

import arrow.core.Either
import com.wire.bots.domain.conversation.ConversationRepository
import com.wire.bots.domain.event.Command
import com.wire.bots.domain.message.OutgoingMessageRepository
import com.wire.bots.domain.user.UserTimezoneRepository
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.UUID

class TimezoneCommandHandlerTest {
    @Test
    fun `given set-timezone in the 1 to 1 conversation, then it is stored and confirmed`() {
        val fixture = Fixture()
        every {
            fixture.conversations.getOrCreateOneToOneConversation(REQUESTER_ID)
        } returns Either.Right(ONE_TO_ONE_ID)
        every { fixture.timezones.save(REQUESTER_ID, BERLIN) } returns Either.Right(Unit)
        val message = slot<String>()
        every {
            fixture.outgoing.sendMessage(ONE_TO_ONE_ID, capture(message))
        } returns Either.Right(Unit)

        fixture.handler
            .setTimezone(command(conversationId = ONE_TO_ONE_ID))
            .fold({ fail("expected success: $it") }) {}

        verify { fixture.timezones.save(REQUESTER_ID, BERLIN) }
        assertTrue(message.captured.contains("Europe/Berlin"), message.captured)
    }

    @Test
    fun `given set-timezone in a group conversation, then nothing is stored`() {
        val fixture = Fixture()
        every {
            fixture.conversations.getOrCreateOneToOneConversation(REQUESTER_ID)
        } returns Either.Right(ONE_TO_ONE_ID)
        val message = slot<String>()
        every {
            fixture.outgoing.sendMessage(GROUP_ID, capture(message))
        } returns Either.Right(Unit)

        fixture.handler
            .setTimezone(command(conversationId = GROUP_ID))
            .fold({ fail("expected success: $it") }) {}

        verify(exactly = 0) { fixture.timezones.save(any(), any()) }
        assertTrue(message.captured.contains("1:1 chat"), message.captured)
    }

    @Test
    fun `given a reminder in a group needs a timezone, then the user is asked in the 1 to 1`() {
        val fixture = Fixture()
        every {
            fixture.conversations.getOrCreateOneToOneConversation(REQUESTER_ID)
        } returns Either.Right(ONE_TO_ONE_ID)
        val prompt = slot<String>()
        val notice = slot<String>()
        every {
            fixture.outgoing.sendMessage(ONE_TO_ONE_ID, capture(prompt))
        } returns Either.Right(Unit)
        every {
            fixture.outgoing.sendMessage(GROUP_ID, capture(notice))
        } returns Either.Right(Unit)

        fixture.handler
            .requestTimezone(conversationId = GROUP_ID, requesterId = REQUESTER_ID)
            .fold({ fail("expected success: $it") }) {}

        assertTrue(prompt.captured.contains("/remind set-timezone"), prompt.captured)
        assertTrue(notice.captured.contains("direct message"), notice.captured)
    }

    @Test
    fun `given a reminder in the 1 to 1 needs a timezone, then the user is asked only once`() {
        val fixture = Fixture()
        every {
            fixture.conversations.getOrCreateOneToOneConversation(REQUESTER_ID)
        } returns Either.Right(ONE_TO_ONE_ID)
        every {
            fixture.outgoing.sendMessage(ONE_TO_ONE_ID, any())
        } returns Either.Right(Unit)

        fixture.handler
            .requestTimezone(conversationId = ONE_TO_ONE_ID, requesterId = REQUESTER_ID)
            .fold({ fail("expected success: $it") }) {}

        verify(exactly = 1) { fixture.outgoing.sendMessage(ONE_TO_ONE_ID, any()) }
    }

    private fun command(conversationId: QualifiedId) =
        Command.SetTimezone(
            conversationId = conversationId,
            requesterId = REQUESTER_ID,
            zoneId = BERLIN
        )

    private class Fixture {
        val outgoing = mockk<OutgoingMessageRepository>()
        val conversations = mockk<ConversationRepository>()
        val timezones = mockk<UserTimezoneRepository>()
        val handler = TimezoneCommandHandler(outgoing, conversations, timezones)
    }

    private companion object {
        val ONE_TO_ONE_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val GROUP_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val REQUESTER_ID = QualifiedId(UUID.randomUUID(), "example.com")
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
