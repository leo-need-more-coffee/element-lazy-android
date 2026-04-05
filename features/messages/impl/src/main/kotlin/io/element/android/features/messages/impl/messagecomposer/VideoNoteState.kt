/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
sealed interface VideoNoteState {
    data object Hidden : VideoNoteState

    data object RequestingPermissions : VideoNoteState

    data class Processing(val uri: Uri) : VideoNoteState

    data class Recording(
        val recordingId: Int,
        val elapsedTime: Duration,
        val isLocked: Boolean,
        val action: RecordingAction,
    ) : VideoNoteState

    enum class RecordingAction {
        Active,
        StopRequested,
        CancelRequested,
    }
}
