/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.videonotes

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class VideoNotePlaybackState(
    val localMediaUri: Uri?,
    val isLoading: Boolean,
    val isActive: Boolean,
    val eventSink: (VideoNotePlaybackEvent) -> Unit,
)

sealed interface VideoNotePlaybackEvent {
    data object TogglePlayback : VideoNotePlaybackEvent
    data object PlaybackEnded : VideoNotePlaybackEvent
}
