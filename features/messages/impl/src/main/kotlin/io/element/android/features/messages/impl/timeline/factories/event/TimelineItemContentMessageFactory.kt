/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import android.text.style.URLSpan
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import dev.zacsweers.metro.Inject
import io.element.android.features.location.api.Location
import io.element.android.features.messages.api.timeline.HtmlConverterProvider
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAudioContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEmoteContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemFileContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemNoticeContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVoiceContent
import io.element.android.features.messages.impl.utils.TextPillificationHelper
import io.element.android.libraries.androidutils.filesize.FileSizeFormatter
import io.element.android.libraries.androidutils.text.safeLinkify
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.timeline.item.event.AudioMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.EmoteMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.FileMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.ImageMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.LocationMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.NoticeMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.OtherMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.StickerMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.VideoMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.VoiceMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.matrix.ui.messages.toHtmlDocument
import io.element.android.libraries.mediaviewer.api.util.FileExtensionExtractor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Document
import kotlin.time.Duration

private const val MIN_IMAGE_SIZE = 1L
private const val MAX_IMAGE_SIZE = 10_000L
private const val MIN_ASPECT_RATIO = 0.001f
private const val MAX_ASPECT_RATIO = 10f
private const val MAX_STICKER_FILE_SIZE = 5_000_000L

@Inject
class TimelineItemContentMessageFactory(
    private val fileSizeFormatter: FileSizeFormatter,
    private val fileExtensionExtractor: FileExtensionExtractor,
    private val htmlConverterProvider: HtmlConverterProvider,
    private val permalinkParser: PermalinkParser,
    private val textPillificationHelper: TextPillificationHelper,
) {
    fun create(
        content: MessageContent,
        senderId: UserId,
        senderProfile: ProfileDetails,
        eventId: EventId?,
    ): TimelineItemEventContent {
        return when (val messageType = content.type) {
            is EmoteMessageType -> {
                val senderDisambiguatedDisplayName = senderProfile.getDisambiguatedDisplayName(senderId)
                val emoteBody = "* $senderDisambiguatedDisplayName ${messageType.body.trimEnd()}"
                val dom = messageType.formatted?.toHtmlDocument(
                    permalinkParser = permalinkParser,
                    prefix = "* $senderDisambiguatedDisplayName",
                )
                val formattedBody = dom?.let(::parseHtml)
                    ?: textPillificationHelper.pillify(emoteBody).safeLinkify()
                TimelineItemEmoteContent(
                    body = emoteBody,
                    htmlDocument = dom,
                    formattedBody = formattedBody,
                    isEdited = content.isEdited,
                )
            }
            is ImageMessageType -> {
                val dom = messageType.formattedCaption?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedCaption = dom?.let(::parseHtml)
                    ?: messageType.caption?.withLinks()
                // Coerce the image sizes and prevent invalid aspect ratios, which can cause crashes
                val width = messageType.info?.width?.coerceIn(MIN_IMAGE_SIZE, MAX_IMAGE_SIZE)
                val height = messageType.info?.height?.coerceIn(MIN_IMAGE_SIZE, MAX_IMAGE_SIZE)
                val aspectRatio = aspectRatioOf(width, height)?.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
                TimelineItemImageContent(
                    filename = messageType.filename,
                    fileSize = messageType.info?.size ?: 0,
                    caption = messageType.caption?.trimEnd(),
                    formattedCaption = formattedCaption,
                    isEdited = content.isEdited,
                    mediaSource = messageType.source,
                    thumbnailSource = messageType.info?.thumbnailSource,
                    mimeType = messageType.info?.mimetype ?: MimeTypes.OctetStream,
                    blurhash = messageType.info?.blurhash,
                    width = width?.toInt(),
                    height = height?.toInt(),
                    thumbnailWidth = messageType.info?.thumbnailInfo?.width?.coerceIn(MIN_IMAGE_SIZE, MAX_IMAGE_SIZE)?.toInt(),
                    thumbnailHeight = messageType.info?.thumbnailInfo?.height?.coerceIn(MIN_IMAGE_SIZE, MAX_IMAGE_SIZE)?.toInt(),
                    aspectRatio = aspectRatio,
                    formattedFileSize = fileSizeFormatter.format(messageType.info?.size ?: 0),
                    fileExtension = fileExtensionExtractor.extractFromName(messageType.filename)
                )
            }
            is StickerMessageType -> {
                val dom = messageType.formattedCaption?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedCaption = dom?.let(::parseHtml)
                    ?: messageType.caption?.withLinks()
                createStickerContent(
                    filename = messageType.filename,
                    fileSize = messageType.info?.size ?: 0,
                    caption = messageType.caption?.trimEnd(),
                    formattedCaption = formattedCaption,
                    isEdited = content.isEdited,
                    mediaSource = messageType.source,
                    thumbnailSource = messageType.info?.thumbnailSource,
                    mimeType = messageType.info?.mimetype ?: MimeTypes.OctetStream,
                    blurhash = messageType.info?.blurhash,
                    width = messageType.info?.width,
                    height = messageType.info?.height,
                )
            }
            is LocationMessageType -> {
                val location = Location.fromGeoUri(messageType.geoUri)
                val body = messageType.body.trimEnd()
                if (location == null) {
                    TimelineItemTextContent(
                        body = body,
                        htmlDocument = null,
                        formattedBody = body,
                        isEdited = content.isEdited,
                    )
                } else {
                    TimelineItemLocationContent(
                        body = body,
                        location = location,
                        description = messageType.description,
                        senderId = senderId,
                        senderProfile = senderProfile,
                        assetType = messageType.assetType,
                        mode = TimelineItemLocationContent.Mode.Static
                    )
                }
            }
            is VideoMessageType -> {
                val dom = messageType.formattedCaption?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedCaption = dom?.let(::parseHtml)
                    ?: messageType.caption?.withLinks()
                val isVideoNote = messageType.filename.startsWith("video_note_")
                val mimeType = messageType.info?.mimetype ?: MimeTypes.OctetStream
                val fileExtension = fileExtensionExtractor.extractFromName(messageType.filename)
                if (!isVideoNote && isLikelyStickerAsset(
                        filename = messageType.filename,
                        caption = messageType.caption,
                        mimeType = mimeType,
                        fileExtension = fileExtension,
                        fileSize = messageType.info?.size,
                    )
                ) {
                    return createStickerContent(
                        filename = messageType.filename,
                        fileSize = messageType.info?.size ?: 0,
                        caption = messageType.caption?.trimEnd(),
                        formattedCaption = formattedCaption,
                        isEdited = content.isEdited,
                        mediaSource = messageType.source,
                        thumbnailSource = messageType.info?.thumbnailSource,
                        mimeType = mimeType,
                        blurhash = messageType.info?.blurhash,
                        width = messageType.info?.width ?: messageType.info?.thumbnailInfo?.width,
                        height = messageType.info?.height ?: messageType.info?.thumbnailInfo?.height,
                    )
                }
                val aspectRatio = if (isVideoNote) 1.0f else aspectRatioOf(messageType.info?.width, messageType.info?.height)
                TimelineItemVideoContent(
                    filename = messageType.filename,
                    fileSize = messageType.info?.size ?: 0,
                    caption = messageType.caption?.trimEnd(),
                    formattedCaption = formattedCaption,
                    isEdited = content.isEdited,
                    thumbnailSource = messageType.info?.thumbnailSource,
                    mediaSource = messageType.source,
                    mimeType = mimeType,
                    width = messageType.info?.width?.toInt(),
                    height = messageType.info?.height?.toInt(),
                    thumbnailWidth = messageType.info?.thumbnailInfo?.width?.toInt(),
                    thumbnailHeight = messageType.info?.thumbnailInfo?.height?.toInt(),
                    duration = messageType.info?.duration ?: Duration.ZERO,
                    blurHash = messageType.info?.blurhash,
                    aspectRatio = aspectRatio,
                    formattedFileSize = fileSizeFormatter.format(messageType.info?.size ?: 0),
                    fileExtension = fileExtension,
                )
            }
            is AudioMessageType -> {
                val dom = messageType.formattedCaption?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedCaption = dom?.let(::parseHtml)
                    ?: messageType.caption?.withLinks()
                TimelineItemAudioContent(
                    filename = messageType.filename,
                    fileSize = messageType.info?.size ?: 0,
                    caption = messageType.caption?.trimEnd(),
                    formattedCaption = formattedCaption,
                    isEdited = content.isEdited,
                    mediaSource = messageType.source,
                    duration = messageType.info?.duration ?: Duration.ZERO,
                    mimeType = messageType.info?.mimetype ?: MimeTypes.OctetStream,
                    formattedFileSize = fileSizeFormatter.format(messageType.info?.size ?: 0),
                    fileExtension = fileExtensionExtractor.extractFromName(messageType.filename),
                )
            }
            is VoiceMessageType -> {
                val dom = messageType.formattedCaption?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedCaption = dom?.let(::parseHtml)
                    ?: messageType.caption?.withLinks()
                TimelineItemVoiceContent(
                    eventId = eventId,
                    filename = messageType.filename,
                    fileSize = messageType.info?.size ?: 0,
                    caption = messageType.caption?.trimEnd(),
                    formattedCaption = formattedCaption,
                    isEdited = content.isEdited,
                    mediaSource = messageType.source,
                    duration = messageType.info?.duration ?: Duration.ZERO,
                    mimeType = messageType.info?.mimetype ?: MimeTypes.OctetStream,
                    waveform = messageType.details?.waveform?.toImmutableList() ?: persistentListOf(),
                    formattedFileSize = fileSizeFormatter.format(messageType.info?.size ?: 0),
                    fileExtension = fileExtensionExtractor.extractFromName(messageType.filename)
                )
            }
            is FileMessageType -> {
                val dom = messageType.formattedCaption?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedCaption = dom?.let(::parseHtml)
                    ?: messageType.caption?.withLinks()
                val fileExtension = fileExtensionExtractor.extractFromName(messageType.filename)
                val mimeType = messageType.info?.mimetype ?: MimeTypes.fromFileExtension(fileExtension)
                if (isLikelyStickerAsset(
                        filename = messageType.filename,
                        caption = messageType.caption,
                        mimeType = mimeType,
                        fileExtension = fileExtension,
                        fileSize = messageType.info?.size,
                    )
                ) {
                    return createStickerContent(
                        filename = messageType.filename,
                        fileSize = messageType.info?.size ?: 0,
                        caption = messageType.caption?.trimEnd(),
                        formattedCaption = formattedCaption,
                        isEdited = content.isEdited,
                        mediaSource = messageType.source,
                        thumbnailSource = messageType.info?.thumbnailSource,
                        mimeType = mimeType,
                        blurhash = null,
                        width = messageType.info?.thumbnailInfo?.width,
                        height = messageType.info?.thumbnailInfo?.height,
                    )
                }
                TimelineItemFileContent(
                    filename = messageType.filename,
                    fileSize = messageType.info?.size ?: 0,
                    caption = messageType.caption?.trimEnd(),
                    formattedCaption = formattedCaption,
                    isEdited = content.isEdited,
                    thumbnailSource = messageType.info?.thumbnailSource,
                    mediaSource = messageType.source,
                    mimeType = mimeType,
                    formattedFileSize = fileSizeFormatter.format(messageType.info?.size ?: 0),
                    fileExtension = fileExtension
                )
            }
            is NoticeMessageType -> {
                val body = messageType.body.trimEnd()
                val dom = messageType.formatted?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedBody = dom?.let(::parseHtml)
                    ?: textPillificationHelper.pillify(body).safeLinkify()
                val htmlDocument = messageType.formatted?.toHtmlDocument(permalinkParser = permalinkParser)
                TimelineItemNoticeContent(
                    body = body,
                    htmlDocument = htmlDocument,
                    formattedBody = formattedBody,
                    isEdited = content.isEdited,
                )
            }
            is TextMessageType -> {
                val body = messageType.body.trimEnd()
                val dom = messageType.formatted?.toHtmlDocument(permalinkParser = permalinkParser)
                val formattedBody = dom?.let(::parseHtml)
                    ?: textPillificationHelper.pillify(body).safeLinkify()
                val htmlDocument = messageType.formatted?.toHtmlDocument(permalinkParser = permalinkParser)
                TimelineItemTextContent(
                    body = body,
                    htmlDocument = htmlDocument,
                    formattedBody = formattedBody,
                    isEdited = content.isEdited,
                )
            }
            is OtherMessageType -> {
                val body = messageType.body.trimEnd()
                TimelineItemTextContent(
                    body = body,
                    htmlDocument = null,
                    formattedBody = textPillificationHelper.pillify(body).safeLinkify(),
                    isEdited = content.isEdited,
                )
            }
        }
    }

    private fun aspectRatioOf(width: Long?, height: Long?): Float? {
        val result = if (height != null && width != null) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }

        return result?.takeIf { it.isFinite() }
    }

    private fun isLikelyStickerAsset(
        filename: String,
        caption: String?,
        mimeType: String,
        fileExtension: String,
        fileSize: Long?,
    ): Boolean {
        val isWebm = mimeType.equals("video/webm", ignoreCase = true) || fileExtension.equals("webm", ignoreCase = true)
        if (!isWebm) return false
        val normalizedCaption = caption?.trim()?.takeIf { it.isNotEmpty() }
        val isCaptionLikeFilename = normalizedCaption == null || normalizedCaption == filename
        return isCaptionLikeFilename || (fileSize != null && fileSize <= MAX_STICKER_FILE_SIZE)
    }

    private fun createStickerContent(
        filename: String,
        fileSize: Long,
        caption: String?,
        formattedCaption: CharSequence?,
        isEdited: Boolean,
        mediaSource: io.element.android.libraries.matrix.api.media.MediaSource,
        thumbnailSource: io.element.android.libraries.matrix.api.media.MediaSource?,
        mimeType: String,
        blurhash: String?,
        width: Long?,
        height: Long?,
    ): TimelineItemStickerContent {
        val safeWidth = width?.coerceIn(MIN_IMAGE_SIZE, MAX_IMAGE_SIZE)
        val safeHeight = height?.coerceIn(MIN_IMAGE_SIZE, MAX_IMAGE_SIZE)
        val aspectRatio = aspectRatioOf(safeWidth, safeHeight)?.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        return TimelineItemStickerContent(
            filename = filename,
            fileSize = fileSize,
            caption = caption,
            formattedCaption = formattedCaption,
            isEdited = isEdited,
            mediaSource = mediaSource,
            thumbnailSource = thumbnailSource,
            mimeType = mimeType,
            blurhash = blurhash,
            width = safeWidth?.toInt(),
            height = safeHeight?.toInt(),
            aspectRatio = aspectRatio,
            formattedFileSize = fileSizeFormatter.format(fileSize),
            fileExtension = fileExtensionExtractor.extractFromName(filename)
        )
    }

    private fun parseHtml(document: Document): CharSequence? {
        return htmlConverterProvider.provide()
            .fromDocumentToSpans(document)
            .let { textPillificationHelper.pillify(it) }
            .safeLinkify()
    }
}

@Suppress("USELESS_ELVIS")
private fun String.withLinks(): CharSequence? {
    // Note: toSpannable() can return null when running unit tests
    val spannable = safeLinkify().toSpannable() ?: return null
    return spannable.takeIf { spannable.getSpans<URLSpan>(0, length).isNotEmpty() }
}
