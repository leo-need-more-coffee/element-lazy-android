/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.stickers.StickerPackManifest
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.MessageComposerRecorderMode
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.libraries.textcomposer.model.aTextEditorStateRich
import io.element.android.wysiwyg.display.TextDisplay
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

open class MessageComposerStateProvider : PreviewParameterProvider<MessageComposerState> {
    override val values: Sequence<MessageComposerState>
        get() = sequenceOf(
            aMessageComposerState(),
        )
}

fun aMessageComposerState(
    textEditorState: TextEditorState = aTextEditorStateRich(),
    isFullScreen: Boolean = false,
    mode: MessageComposerMode = MessageComposerMode.Normal,
    showTextFormatting: Boolean = false,
    showAttachmentSourcePicker: Boolean = false,
    showStickerPicker: Boolean = false,
    isImportingStickerPack: Boolean = false,
    stickerPacks: ImmutableList<StickerPackManifest> = persistentListOf(),
    canShareLocation: Boolean = true,
    recorderMode: MessageComposerRecorderMode = MessageComposerRecorderMode.Audio,
    videoNoteState: VideoNoteState = VideoNoteState.Hidden,
    suggestions: ImmutableList<ResolvedSuggestion> = persistentListOf(),
    slashCommandAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (MessageComposerEvent) -> Unit = {},
) = MessageComposerState(
    textEditorState = textEditorState,
    isFullScreen = isFullScreen,
    mode = mode,
    showTextFormatting = showTextFormatting,
    showAttachmentSourcePicker = showAttachmentSourcePicker,
    showStickerPicker = showStickerPicker,
    isImportingStickerPack = isImportingStickerPack,
    stickerPacks = stickerPacks,
    canShareLocation = canShareLocation,
    recorderMode = recorderMode,
    videoNoteState = videoNoteState,
    suggestions = suggestions,
    resolveMentionDisplay = { _, _ -> TextDisplay.Plain },
    resolveAtRoomMentionDisplay = { TextDisplay.Plain },
    slashCommandAction = slashCommandAction,
    eventSink = eventSink,
)
