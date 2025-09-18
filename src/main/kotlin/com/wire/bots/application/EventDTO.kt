package com.wire.bots.application

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wire.integrations.jvm.model.QualifiedId

interface EventDTO {
    val type: EventTypeDTO
    val userId: String?
    val conversationId: QualifiedId
}

@Serializable
data class MessageEventDTO(
    override val type: EventTypeDTO,
    override val userId: String? = null,
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
    override val userId: String? = null,
    override val conversationId: QualifiedId,
    val text: TextContent? = null,
    val handle: String? = null,
    val locale: String? = null,
    val conversation: String? = null,
    val messageId: String? = null,
    val refMessageId: String? = null,
    val emoji: String? = null,
    val buttonId: String? = null,
    val referencedMessageId: String? = null,
    val sender: String? = null
) : EventDTO

@Serializable
data class TextContent(
    val data: String,
    // todo: map later or never.
    @Transient val mentions: List<String> = emptyList()
)
