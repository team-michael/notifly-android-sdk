package tech.notifly.data

import com.google.gson.annotations.SerializedName

/**
 * JSON structure of modal_properties
 */
data class ModalPropertiesData(
    @SerializedName("template_name") val templateName: String,
    @SerializedName("width_vw") val widthVw: Int,
    @SerializedName("height_vh") val heightVh: Int,
    @SerializedName("position") val position: String
)