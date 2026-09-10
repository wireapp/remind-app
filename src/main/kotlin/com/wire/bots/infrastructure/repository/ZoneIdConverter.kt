package com.wire.bots.infrastructure.repository

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.ZoneId

@Converter(autoApply = true)
class ZoneIdConverter : AttributeConverter<ZoneId, String> {
    override fun convertToDatabaseColumn(attribute: ZoneId): String = attribute.id

    override fun convertToEntityAttribute(dbData: String): ZoneId = ZoneId.of(dbData)
}
