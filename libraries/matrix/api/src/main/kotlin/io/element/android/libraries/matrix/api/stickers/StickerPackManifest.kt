/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.stickers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val STICKER_PACK_ACCOUNT_DATA_EVENT_TYPE = "io.element.elementx.sticker_packs"

@Serializable
data class StickerPackAccountData(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    val packs: List<StickerPackManifest> = emptyList(),
)

@Serializable
data class StickerPackManifest(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val description: String? = null,
    val author: String? = null,
    val license: String? = null,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    val items: List<StickerPackItem>,
)

@Serializable
data class StickerPackItem(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val description: String? = null,
    val source: StickerPackAsset,
    val thumbnail: StickerPackAsset? = null,
)

@Serializable
data class StickerPackAsset(
    val url: String,
    @SerialName("mime_type")
    val mimeType: String,
    val format: StickerFormat = StickerFormat.STATIC,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("size_bytes")
    val sizeBytes: Long? = null,
)

@Serializable
enum class StickerFormat {
    @SerialName("static")
    STATIC,

    @SerialName("webm")
    WEBM,

    @SerialName("tgs")
    TGS,
}
