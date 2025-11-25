package com.wire.bots.infrastructure.repository

import com.wire.sdk.model.QualifiedId
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "REMINDERS")
data class ReminderEntity(
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Convert(converter = QualifiedIdConverter::class)
    @Column(name = "conversation_id") val conversationId: QualifiedId,
    @Column(name = "task_id") val taskId: String,
    @Column(name = "task") val task: String,
    @Column(name = "scheduled_at") val scheduledAt: Instant? = null,
    @Column(name = "scheduled_cron") val scheduledCron: String? = null,
    @Column(name = "is_eternal") val isEternal: Boolean = false
) : PanacheEntity()
