package com.wire.bots.application

import com.wire.sdk.model.QualifiedId
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

interface EventDTO {
    val type: EventTypeDTO

    /** The user the event came from. Personal settings, like the timezone, are keyed on it. */
    val senderId: QualifiedId
    val conversationId: QualifiedId
}

@Serializable
data class MessageEventDTO(
    override val type: EventTypeDTO,
    override val senderId: QualifiedId,
    override val conversationId: QualifiedId,
    val text: TextContent? = null,
    val handle: String? = null,
    val locale: String? = null,
    val conversation: String? = null,
    val messageId: String? = null,
    val refMessageId: String? = null,
    val emoji: String? = null
) : EventDTO

@Serializable
data class ButtonActionEventDTO(
    override val type: EventTypeDTO,
    override val senderId: QualifiedId,
    override val conversationId: QualifiedId,
    val text: TextContent? = null,
    val handle: String? = null,
    val locale: String? = null,
    val conversation: String? = null,
    val messageId: String? = null,
    val refMessageId: String? = null,
    val emoji: String? = null,
    val buttonId: String? = null,
    val referencedMessageId: String? = null
) : EventDTO

@Serializable
data class TextContent(
    val data: String,
    // todo: map later or never.
    @Transient val mentions: List<String> = emptyList()
)
