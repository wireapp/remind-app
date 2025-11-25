package com.wire.bots.infrastructure.repository

import com.wire.bots.infrastructure.utils.toRawString
import com.wire.bots.infrastructure.utils.toQualifiedId
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import com.wire.sdk.model.QualifiedId

@Converter(autoApply = true)
class QualifiedIdConverter : AttributeConverter<QualifiedId, String> {
    override fun convertToDatabaseColumn(attribute: QualifiedId): String = attribute.toRawString()

    override fun convertToEntityAttribute(dbData: String): QualifiedId = dbData.toQualifiedId()
}
