/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.videonotes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.RoomScope
import timber.log.Timber

interface VideoNotePlaybackCoordinator {
    val activeKey: String?
    fun toggle(key: String)
    fun clear(key: String)
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
@Inject
class DefaultVideoNotePlaybackCoordinator : VideoNotePlaybackCoordinator {
    override var activeKey: String? by mutableStateOf(null)
        private set

    override fun toggle(key: String) {
        val previousKey = activeKey
        activeKey = if (activeKey == key) null else key
        Timber.tag("VideoNotePlayback").d(
            "coordinator.toggle keyHash=%d previousHash=%s nextHash=%s",
            key.hashCode(),
            previousKey?.hashCode()?.toString() ?: "null",
            activeKey?.hashCode()?.toString() ?: "null",
        )
    }

    override fun clear(key: String) {
        if (activeKey == key) {
            Timber.tag("VideoNotePlayback").d("coordinator.clear keyHash=%d", key.hashCode())
            activeKey = null
        } else {
            Timber.tag("VideoNotePlayback").d(
                "coordinator.clear ignored keyHash=%d activeHash=%s",
                key.hashCode(),
                activeKey?.hashCode()?.toString() ?: "null",
            )
        }
    }
}
