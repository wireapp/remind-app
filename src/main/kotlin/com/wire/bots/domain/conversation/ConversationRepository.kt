package com.wire.bots.domain.conversation

import arrow.core.Either
import com.wire.sdk.model.QualifiedId

interface ConversationRepository {
    /**
     * Returns the 1:1 conversation between the app and [userId], creating it when there is none
     * yet. This is where the app talks to a single user about their personal settings.
     */
    fun getOrCreateOneToOneConversation(userId: QualifiedId): Either<Throwable, QualifiedId>
}
