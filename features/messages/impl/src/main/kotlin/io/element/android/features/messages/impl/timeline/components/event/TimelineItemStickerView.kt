/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.features.messages.impl.stickers.AnimatedWebmStickerView
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContentProvider
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.features.messages.impl.timeline.protection.coerceRatioWhenHidingContent
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings

private const val STICKER_SIZE_IN_DP = 140

@Composable
fun TimelineItemStickerView(
    content: TimelineItemStickerContent,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = content.bestDescription.takeIf { it.isNotEmpty() } ?: stringResource(CommonStrings.common_image)
    val maxStickerWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.5f)
    Column(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        TimelineItemAspectRatioBox(
            modifier = Modifier.widthIn(max = maxStickerWidth),
            aspectRatio = coerceRatioWhenHidingContent(content.aspectRatio, hideMediaContent),
            minHeight = STICKER_SIZE_IN_DP,
            maxHeight = 184,
        ) {
            ProtectedView(
                hideContent = hideMediaContent,
                onShowClick = onShowClick,
            ) {
                val clickModifier = if (onContentClick != null) {
                    Modifier
                        .combinedClickable(
                            onClick = onContentClick,
                            onLongClick = onLongClick,
                            onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
                        )
                        .onKeyboardContextMenuAction(onLongClick)
                } else {
                    Modifier
                }
                if (content.mimeType.startsWith("video/")) {
                    AnimatedWebmStickerView(
                        mediaSource = content.preferredMediaSource
                            ?: content.mediaSource,
                        mimeType = content.mimeType,
                        filename = content.filename,
                        modifier = Modifier.fillMaxSize().then(clickModifier),
                    )
                } else {
                    AsyncImage(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(clickModifier),
                        model = MediaRequestData(
                            source = content.preferredMediaSource,
                            kind = MediaRequestData.Kind.File(
                                fileName = content.filename,
                                mimeType = content.mimeType,
                            ),
                        ),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        contentDescription = description,
                    )
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemStickerViewPreview(@PreviewParameter(TimelineItemStickerContentProvider::class) content: TimelineItemStickerContent) = ElementPreview {
    TimelineItemStickerView(
        content = content,
        hideMediaContent = false,
        onContentClick = {},
        onLongClick = {},
        onShowClick = {},
    )
}
