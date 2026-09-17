package com.wire.bots.infrastructure.utils

/**
 * What a user writes is their own content, so it must never reach the logs. The types carrying
 * it print this placeholder instead of the text, which keeps log lines readable without
 * exposing the message.
 */
const val HIDDEN = "<hidden>"
