package com.wire.bots.infrastructure.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "CONVERSATION_SETTINGS")
data class ConversationSettingsEntity(
    @Id
    @Column(name = "conversation_id") val conversationId: String,
    @Column(name = "timezone") val timezone: String = "UTC"
) : PanacheEntityBase
