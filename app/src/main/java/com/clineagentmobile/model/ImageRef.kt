package com.clineagentmobile.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an image reference for multimodal AI input.
 */
data class ImageRef(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("data_url") val dataUrl: String,
    @SerializedName("mime_type") val mimeType: String = "image/jpeg",
    @SerializedName("name") val name: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("source") val source: Source = Source.USER,
    @SerializedName("path") val path: String? = null
) {
    enum class Source {
        @SerializedName("user") USER,
        @SerializedName("camera") CAMERA,
        @SerializedName("gallery") GALLERY,
        @SerializedName("clipboard") CLIPBOARD,
        @SerializedName("document") DOCUMENT
    }

    companion object {
        fun fromJson(json: Map<String, Any>): ImageRef {
            val sourceName = (json["source"] as? String)?.uppercase() ?: "USER"
            return ImageRef(
                id = json["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                dataUrl = json["data_url"] as? String ?: "",
                mimeType = json["mime_type"] as? String ?: "image/jpeg",
                name = json["name"] as? String,
                width = (json["width"] as? Number)?.toInt(),
                height = (json["height"] as? Number)?.toInt(),
                source = try {
                    Source.valueOf(sourceName)
                } catch (e: Exception) {
                    Source.USER
                },
                path = json["path"] as? String
            )
        }
    }
}
