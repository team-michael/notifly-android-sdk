package tech.notifly.command.models

import android.content.Context

interface PayloadBase {
    val context: Context
}

data class SetUserIdPayload(
    override val context: Context, val userId: String? = null
) : PayloadBase

data class SetUserPropertiesPayload(
    override val context: Context, val params: Map<String, Any?>
) : PayloadBase

data class TrackEventPayload(
    override val context: Context,
    val eventName: String,
    val eventParams: Map<String, Any?> = emptyMap(),
    val segmentationEventParamKeys: List<String>? = null,
    val isInternalEvent: Boolean = false
) : PayloadBase