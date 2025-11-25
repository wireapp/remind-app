package com.wire.bots.infrastructure.utils

import com.wire.sdk.model.QualifiedId
import java.util.UUID

fun QualifiedId.toRawString(): String = "$id@$domain"

fun String.toQualifiedId(): QualifiedId {
    val splitQualifiedId = this.split("@")
    if (splitQualifiedId.size != 2) require(false) { "Invalid QualifiedId format" }
    val uuid = UUID.fromString(splitQualifiedId[0])
    val domain = splitQualifiedId[1]
    return QualifiedId(uuid, domain)
}
