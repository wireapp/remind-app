package com.wire.bots.infrastructure.repository

import com.wire.sdk.model.QualifiedId
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.ZoneId

@Entity
@Table(name = "USER_TIMEZONES")
class UserTimezoneEntity(
    @Column(name = "user_id") var userId: QualifiedId,
    @Column(name = "zone_id") var zoneId: ZoneId,
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
) : PanacheEntity()
