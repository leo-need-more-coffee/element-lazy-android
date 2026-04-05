/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes as Media3MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.file.createTmpFile
import io.element.android.libraries.androidutils.file.safeDelete
import io.element.android.libraries.androidutils.media.runAndRelease
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.stickers.STICKER_PACK_ACCOUNT_DATA_EVENT_TYPE
import io.element.android.libraries.matrix.api.stickers.StickerPackAccountData
import io.element.android.libraries.matrix.api.stickers.StickerFormat
import io.element.android.libraries.matrix.api.stickers.StickerPackAsset
import io.element.android.libraries.matrix.api.stickers.StickerPackItem
import io.element.android.libraries.matrix.api.stickers.StickerPackManifest
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

interface StickerPackService {
    suspend fun getPacks(): Result<List<StickerPackManifest>>
    suspend fun savePacks(packs: List<StickerPackManifest>): Result<Unit>
    suspend fun addPack(pack: StickerPackManifest): Result<Unit>
    suspend fun removePack(packId: String): Result<Unit>
    suspend fun importPack(sourceUrl: String): Result<StickerPackManifest>
    suspend fun importPackArchive(uri: Uri): Result<StickerPackManifest>
}

@ContributesBinding(SessionScope::class)
class DefaultStickerPackService(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
    private val jsonProvider: JsonProvider,
) : StickerPackService {
    private companion object {
        const val THUMBNAIL_MAX_SIZE = 160
        const val THUMBNAIL_QUALITY = 72
        const val VIDEO_STICKER_MAX_SIZE = 320
        const val VIDEO_STICKER_TARGET_FRAME_RATE = 15
    }

    private val json: Json
        get() = jsonProvider()

    override suspend fun getPacks(): Result<List<StickerPackManifest>> = withContext(dispatchers.io) {
        matrixClient.getAccountData(STICKER_PACK_ACCOUNT_DATA_EVENT_TYPE)
            .mapCatching { content ->
                if (content.isNullOrBlank()) {
                    emptyList()
                } else {
                    json.decodeFromString<StickerPackAccountData>(content).packs
                }
            }
    }

    override suspend fun savePacks(packs: List<StickerPackManifest>): Result<Unit> = withContext(dispatchers.io) {
        val content = json.encodeToString(
            StickerPackAccountData(
                packs = packs.distinctBy { it.id }
            )
        )
        matrixClient.setAccountData(
            eventType = STICKER_PACK_ACCOUNT_DATA_EVENT_TYPE,
            content = content,
        )
    }

    override suspend fun addPack(pack: StickerPackManifest): Result<Unit> = withContext(dispatchers.io) {
        getPacks().fold(
            onSuccess = { existingPacks ->
                savePacks(existingPacks + pack)
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun removePack(packId: String): Result<Unit> = withContext(dispatchers.io) {
        getPacks().fold(
            onSuccess = { existingPacks ->
                savePacks(existingPacks.filterNot { it.id == packId })
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun importPack(sourceUrl: String): Result<StickerPackManifest> = withContext(dispatchers.io) {
        loadPack(sourceUrl).fold(
            onSuccess = { pack ->
                addPack(pack).map { pack }
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun importPackArchive(uri: Uri): Result<StickerPackManifest> = withContext(dispatchers.io) {
        loadPackArchive(uri).fold(
            onSuccess = { pack ->
                addPack(pack).map { pack }
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun loadPack(sourceUrl: String): Result<StickerPackManifest> {
        val bytesResult = if (sourceUrl.startsWith("mxc://")) {
            matrixClient.matrixMediaLoader.loadMediaContent(MediaSource(sourceUrl))
        } else {
            matrixClient.getUrl(sourceUrl)
        }
        return bytesResult.mapCatching { bytes ->
            json.decodeFromString<StickerPackManifest>(bytes.decodeToString()).copy(sourceUrl = sourceUrl)
        }
    }

    private suspend fun loadPackArchive(uri: Uri): Result<StickerPackManifest> = runCatching {
        val archiveName = resolveArchiveDisplayName(uri)
        val parsedArchive = parseArchive(uri)
        require(parsedArchive.items.isNotEmpty()) { "Sticker pack archive does not contain supported files" }

        val packDisplayName = parsedArchive.metadata?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: formatDisplayName(archiveName.substringBeforeLast('.'))
        val pack = StickerPackManifest(
            id = buildPackId(packDisplayName),
            displayName = packDisplayName,
            description = parsedArchive.metadata?.description,
            author = parsedArchive.metadata?.author,
            license = parsedArchive.metadata?.license,
            items = parsedArchive.items,
        )
        val manifestContent = json.encodeToString(pack).encodeToByteArray()
        val manifestUrl = matrixClient.uploadMedia(MimeTypes.Json, manifestContent).getOrThrow()
        pack.copy(sourceUrl = manifestUrl)
    }

    private suspend fun parseArchive(uri: Uri): ParsedArchive {
        // Phase 1: read all ZIP bytes and parse via the central directory.
        // ZipInputStream cannot read STORED entries with data descriptors (flag bit 3) because
        // it relies on the local file header size field, which is 0 when a data descriptor is used.
        // Parsing the central directory gives us the correct offsets and sizes without JVM path validation.
        val zipBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to open sticker pack archive")
        val rawEntries = parseZipCentralDirectory(zipBytes)

        // Phase 2: process entries — may involve network calls (media upload).
        val usedItemIds = linkedSetOf<String>()
        var metadata: ArchiveMetadata? = null
        val items = mutableListOf<StickerPackItem>()
        for ((name, entryBytes) in rawEntries) {
            if (metadata == null) metadata = parseMetadataEntry(name, entryBytes)
            buildArchiveItem(name, entryBytes, usedItemIds)?.let(items::add)
        }

        return ParsedArchive(metadata = metadata, items = items)
    }

    private suspend fun buildArchiveItem(
        entryName: String,
        bytes: ByteArray,
        usedItemIds: MutableSet<String>,
    ): StickerPackItem? {
        val fileType = resolveFileType(entryName) ?: return null
        Timber.d("Uploading sticker file $entryName: ${bytes.size} bytes, mime=${fileType.mimeType}")
        if (bytes.isEmpty()) {
            Timber.w("Skipping sticker file $entryName — empty bytes")
            return null
        }
        val preparedVideo = if (fileType.format == StickerFormat.WEBM) {
            prepareVideoSticker(bytes)
        } else {
            null
        }
        val uploadBytes = preparedVideo?.bytes ?: bytes
        val uploadMimeType = preparedVideo?.mimeType ?: fileType.mimeType
        val uploadedUrl = matrixClient.uploadMedia(uploadMimeType, uploadBytes).getOrElse { cause ->
            Timber.w(cause, "Skipping sticker file $entryName — upload failed")
            return null
        }
        val displayName = formatDisplayName(entryName.substringBeforeLast('.'))
        val itemId = buildUniqueItemId(displayName, usedItemIds)
        val dimensions = when {
            preparedVideo != null -> preparedVideo.width to preparedVideo.height
            fileType.format == StickerFormat.STATIC -> extractImageDimensions(bytes)
            else -> null
        }
        val thumbnail = preparedVideo?.thumbnail ?: createThumbnailAsset(bytes, fileType, dimensions)
        val source = StickerPackAsset(
            url = uploadedUrl,
            mimeType = uploadMimeType,
            format = fileType.format,
            width = dimensions?.first,
            height = dimensions?.second,
            sizeBytes = uploadBytes.size.toLong(),
        )

        return StickerPackItem(
            id = itemId,
            displayName = displayName,
            source = source,
            thumbnail = thumbnail ?: source.takeIf { fileType.format == StickerFormat.STATIC },
        )
    }

    private fun parseMetadataEntry(entryName: String, bytes: ByteArray): ArchiveMetadata? {
        val normalizedName = entryName.lowercase(Locale.ROOT)
        if (normalizedName != "pack.json" && normalizedName != "sticker-pack.json" && normalizedName != "manifest.json") {
            return null
        }
        return runCatching {
            json.decodeFromString<ArchiveMetadata>(bytes.decodeToString())
        }.getOrNull()
    }

    private fun resolveArchiveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameColumn)
            }
        }
        return uri.lastPathSegment ?: "Sticker pack"
    }

    private fun buildPackId(displayName: String): String {
        val slug = slugify(displayName).ifBlank { "sticker-pack" }
        return "$slug-${UUID.randomUUID().toString().substringBefore('-')}"
    }

    private fun buildUniqueItemId(displayName: String, usedItemIds: MutableSet<String>): String {
        val baseId = slugify(displayName).ifBlank { "sticker" }
        var candidate = baseId
        var index = 2
        while (!usedItemIds.add(candidate)) {
            candidate = "$baseId-$index"
            index += 1
        }
        return candidate
    }

    private fun formatDisplayName(rawValue: String): String {
        return rawValue
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Sticker" }
    }

    private fun slugify(value: String): String {
        return value.lowercase(Locale.ROOT)
            .map { character ->
                when {
                    character.isLetterOrDigit() -> character
                    else -> '-'
                }
            }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    private fun resolveFileType(entryName: String): ArchiveFileType? {
        return when (entryName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> ArchiveFileType(MimeTypes.Png, StickerFormat.STATIC)
            "jpg", "jpeg" -> ArchiveFileType(MimeTypes.Jpeg, StickerFormat.STATIC)
            "webp" -> ArchiveFileType(MimeTypes.WebP, StickerFormat.STATIC)
            "gif" -> ArchiveFileType(MimeTypes.Gif, StickerFormat.STATIC)
            "webm" -> ArchiveFileType("video/webm", StickerFormat.WEBM)
            "tgs" -> ArchiveFileType("application/x-tgsticker", StickerFormat.TGS)
            else -> null
        }
    }

    private fun extractImageDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private suspend fun createThumbnailAsset(
        bytes: ByteArray,
        fileType: ArchiveFileType,
        dimensions: Pair<Int, Int>?,
    ): StickerPackAsset? {
        if (fileType.format != StickerFormat.STATIC) return null
        val sourceWidth = dimensions?.first ?: return null
        val sourceHeight = dimensions.second
        if (sourceWidth <= THUMBNAIL_MAX_SIZE && sourceHeight <= THUMBNAIL_MAX_SIZE) return null

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = THUMBNAIL_MAX_SIZE.toFloat() / max(bitmap.width, bitmap.height).toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        val thumbnailBytes = scaledBitmap.toWebpByteArray()
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()

        val thumbnailUrl = matrixClient.uploadMedia(MimeTypes.WebP, thumbnailBytes).getOrElse { cause ->
            Timber.w(cause, "Skipping sticker thumbnail upload")
            return null
        }
        return StickerPackAsset(
            url = thumbnailUrl,
            mimeType = MimeTypes.WebP,
            format = StickerFormat.STATIC,
            width = targetWidth,
            height = targetHeight,
            sizeBytes = thumbnailBytes.size.toLong(),
        )
    }

    private fun Bitmap.toWebpByteArray(): ByteArray {
        return java.io.ByteArrayOutputStream().use { outputStream ->
            compress(Bitmap.CompressFormat.WEBP_LOSSY, THUMBNAIL_QUALITY, outputStream)
            outputStream.toByteArray()
        }
    }

    private suspend fun prepareVideoSticker(bytes: ByteArray): PreparedVideoSticker? {
        val inputFile = context.createTmpFile(extension = "webm")
        val outputFile = context.createTmpFile(extension = "mp4")
        return try {
            inputFile.writeBytes(bytes)
            val transcodedFile = transcodeStickerVideo(inputFile, outputFile)
            val finalFile = transcodedFile ?: inputFile
            val dimensions = extractVideoDimensions(finalFile) ?: return null
            val thumbnail = createVideoThumbnailAsset(finalFile)
            PreparedVideoSticker(
                bytes = finalFile.readBytes(),
                mimeType = if (transcodedFile != null) MimeTypes.Mp4 else "video/webm",
                width = dimensions.first,
                height = dimensions.second,
                thumbnail = thumbnail,
            )
        } finally {
            inputFile.safeDelete()
            outputFile.safeDelete()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcodeStickerVideo(inputFile: File, outputFile: File): File? {
        val dimensions = extractVideoDimensions(inputFile) ?: return null
        val targetSize = scaleToStickerSize(dimensions.first, dimensions.second)
        val outputMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
            .setFrameRate(VIDEO_STICKER_TARGET_FRAME_RATE)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            targetSize.first,
                            targetSize.second,
                            Presentation.LAYOUT_SCALE_TO_FIT,
                        )
                    )
                )
            )
            .build()

        return suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(Media3MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Timber.w(exportException, "Failed to transcode sticker video")
                        continuation.resume(null)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                transformer.cancel()
            }

            kotlinx.coroutines.CoroutineScope(dispatchers.main).launch {
                transformer.start(outputMediaItem, outputFile.path)
            }
        }
    }

    private fun extractVideoDimensions(file: File): Pair<Int, Int>? = runCatching {
        MediaMetadataRetriever().runAndRelease {
            setDataSource(file.absolutePath)
            val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0) {
                width to height
            } else {
                getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { it.width to it.height }
            }
        }
    }.getOrNull()

    private suspend fun createVideoThumbnailAsset(file: File): StickerPackAsset? {
        val bitmap = runCatching {
            MediaMetadataRetriever().runAndRelease {
                setDataSource(file.absolutePath)
                getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull() ?: return null
        val scale = THUMBNAIL_MAX_SIZE.toFloat() / max(bitmap.width, bitmap.height).toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val thumbnailBytes = scaledBitmap.toWebpByteArray()
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()

        val thumbnailUrl = matrixClient.uploadMedia(MimeTypes.WebP, thumbnailBytes).getOrElse { cause ->
            Timber.w(cause, "Skipping sticker video thumbnail upload")
            return null
        }
        return StickerPackAsset(
            url = thumbnailUrl,
            mimeType = MimeTypes.WebP,
            format = StickerFormat.STATIC,
            width = targetWidth,
            height = targetHeight,
            sizeBytes = thumbnailBytes.size.toLong(),
        )
    }

    private fun scaleToStickerSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1f, VIDEO_STICKER_MAX_SIZE.toFloat() / max(width, height).toFloat())
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }
}

/**
 * Parses a ZIP archive by reading its central directory, bypassing JVM path validation.
 * This handles non-compliant archives (e.g. Telegram packs) that have:
 *  - Entry names with leading slashes (rejected by ZipEntry constructor)
 *  - STORED entries with data descriptors (flag bit 3), where the local file header has size=0
 *
 * Returns a list of (filename, bytes) pairs for all non-directory entries.
 */
private fun parseZipCentralDirectory(bytes: ByteArray): List<Pair<String, ByteArray>> {
    // Find End of Central Directory record by scanning backwards for signature PK\x05\x06
    var eocdPos = bytes.size - 22
    while (eocdPos >= 0) {
        if (bytes[eocdPos] == 0x50.toByte() && bytes[eocdPos + 1] == 0x4b.toByte() &&
            bytes[eocdPos + 2] == 0x05.toByte() && bytes[eocdPos + 3] == 0x06.toByte()
        ) break
        eocdPos--
    }
    require(eocdPos >= 0) { "Not a valid ZIP archive (no EOCD record)" }

    val cdOffset = bytes.zipUInt32(eocdPos + 16).toInt()
    val cdCount = bytes.zipUInt16(eocdPos + 10)

    val result = mutableListOf<Pair<String, ByteArray>>()
    var pos = cdOffset
    repeat(cdCount) {
        require(bytes[pos] == 0x50.toByte() && bytes[pos + 1] == 0x4b.toByte() &&
            bytes[pos + 2] == 0x01.toByte() && bytes[pos + 3] == 0x02.toByte()
        ) { "Invalid central directory signature at $pos" }
        val compressedSize = bytes.zipUInt32(pos + 20).toInt()
        val fileSize = bytes.zipUInt32(pos + 24).toInt()
        val nameLen = bytes.zipUInt16(pos + 28)
        val extraLen = bytes.zipUInt16(pos + 30)
        val commentLen = bytes.zipUInt16(pos + 32)
        val localOffset = bytes.zipUInt32(pos + 42).toInt()
        val name = String(bytes, pos + 46, nameLen, Charsets.UTF_8)
        pos += 46 + nameLen + extraLen + commentLen

        if (!name.endsWith('/')) {
            val cleanName = name.trimStart('/').substringAfterLast('/').trim()
            if (cleanName.isNotEmpty() && !cleanName.startsWith('.')) {
                // Read data from the local file header, using the central directory size
                val localNameLen = bytes.zipUInt16(localOffset + 26)
                val localExtraLen = bytes.zipUInt16(localOffset + 28)
                val dataStart = localOffset + 30 + localNameLen + localExtraLen
                val dataEnd = dataStart + if (fileSize > 0) fileSize else compressedSize
                result.add(cleanName to bytes.copyOfRange(dataStart, dataEnd))
            }
        }
    }
    return result
}

private fun ByteArray.zipUInt16(offset: Int): Int =
    (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.zipUInt32(offset: Int): Long =
    (this[offset].toLong() and 0xff) or
        ((this[offset + 1].toLong() and 0xff) shl 8) or
        ((this[offset + 2].toLong() and 0xff) shl 16) or
        ((this[offset + 3].toLong() and 0xff) shl 24)

private data class ParsedArchive(
    val metadata: ArchiveMetadata?,
    val items: List<StickerPackItem>,
)

private data class PreparedVideoSticker(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val thumbnail: StickerPackAsset?,
)

@Serializable
private data class ArchiveMetadata(
    @SerialName("display_name")
    val displayName: String? = null,
    val description: String? = null,
    val author: String? = null,
    val license: String? = null,
)

private data class ArchiveFileType(
    val mimeType: String,
    val format: StickerFormat,
)
