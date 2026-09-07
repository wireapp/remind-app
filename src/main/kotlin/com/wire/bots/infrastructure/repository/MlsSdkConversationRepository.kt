package com.wire.bots.infrastructure.repository

import arrow.core.Either
import com.wire.bots.application.MlsSdkClient
import com.wire.bots.domain.conversation.ConversationRepository
import com.wire.sdk.model.QualifiedId
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MlsSdkConversationRepository(
    private val sdkClient: MlsSdkClient
) : ConversationRepository {
    override fun getOrCreateOneToOneConversation(
        userId: QualifiedId
    ): Either<Throwable, QualifiedId> =
        Either.catch {
            val manager = sdkClient.getManager()
            manager.getOneToOneConversationByUserId(userId)?.id
                ?: manager.createOneToOneConversation(userId)
        }
}
