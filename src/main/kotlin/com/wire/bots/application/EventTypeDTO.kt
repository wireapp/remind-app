package com.wire.bots.application

import kotlinx.serialization.SerialName

enum class EventTypeDTO {
    @SerialName("conversation.bot_request")
    BOT_REQUEST,

    @SerialName("conversation.bot_removed")
    BOT_REMOVED,

    @SerialName("conversation.init")
    CONVERSATION_INIT,

    @SerialName("conversation.new_text")
    NEW_TEXT,

    @SerialName("conversation.image.preview")
    IMAGE_PREVIEW,

    @SerialName("conversation.file.preview")
    FILE_PREVIEW,

    @SerialName("conversation.audio.preview")
    AUDIO_PREVIEW,

    @SerialName("conversation.asset.data")
    ASSET_DATA,

    @SerialName("conversation.poll.action")
    POLL_ACTION,

    @SerialName("conversation.user_joined")
    USER_JOINED,

    @SerialName("conversation.reaction")
    REACTION,

    @SerialName("conversation.button.action")
    BUTTON_ACTION
}
