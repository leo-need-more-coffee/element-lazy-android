/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.videonotes

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import io.element.android.features.messages.impl.timeline.di.TimelineItemEventContentKey
import io.element.android.features.messages.impl.timeline.di.TimelineItemPresenterFactory
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.launch
import timber.log.Timber

@BindingContainer
@ContributesTo(RoomScope::class)
interface VideoNotePlaybackPresenterModule {
    @Binds
    @IntoMap
    @TimelineItemEventContentKey(TimelineItemVideoContent::class)
    fun bindVideoNotePlaybackPresenterFactory(factory: VideoNotePlaybackPresenter.Factory): TimelineItemPresenterFactory<*, *>
}

@AssistedInject
class VideoNotePlaybackPresenter(
    private val videoNoteMediaRepoFactory: VideoNoteMediaRepo.Factory,
    private val playbackCoordinator: VideoNotePlaybackCoordinator,
    @Assisted private val content: TimelineItemVideoContent,
) : Presenter<VideoNotePlaybackState> {
    @AssistedFactory
    fun interface Factory : TimelineItemPresenterFactory<TimelineItemVideoContent, VideoNotePlaybackState> {
        override fun create(content: TimelineItemVideoContent): VideoNotePlaybackPresenter
    }

    @Composable
    override fun present(): VideoNotePlaybackState {
        val coroutineScope = rememberCoroutineScope()
        // Use filename as key so it stays stable across local-echo → server-event transition
        val key = remember(content) { content.filename }
        // remember(key) instead of remember(content): survives the local-echo→server-event
        // content change, so localMediaUri and playback are not interrupted on send.
        var localMediaUri: Uri? by remember(key) { mutableStateOf(initialLocalMediaUri()) }
        var isLoading by remember(key) { mutableStateOf(false) }
        val isActive = content.isVideoNote && playbackCoordinator.activeKey == key
        val keyHash = remember(key) { key.hashCode() }

        // Declared before the LaunchedEffects that reference it
        fun ensureLoaded() {
            if (!content.isVideoNote || localMediaUri != null || isLoading) {
                Timber.tag("VideoNotePlayback").d(
                    "presenter.ensureLoaded skipped keyHash=%d isVideoNote=%s hasLocalUri=%s isLoading=%s",
                    keyHash,
                    content.isVideoNote,
                    localMediaUri != null,
                    isLoading,
                )
                return
            }
            Timber.tag("VideoNotePlayback").d("presenter.ensureLoaded start keyHash=%d", keyHash)
            isLoading = true
            coroutineScope.launch {
                val repo = videoNoteMediaRepoFactory.create(
                    mediaSource = content.mediaSource,
                    mimeType = content.mimeType,
                    filename = content.filename,
                )
                repo.getMediaFile()
                    .onSuccess {
                        localMediaUri = it.toUri()
                        Timber.tag("VideoNotePlayback").d("presenter.ensureLoaded success keyHash=%d", keyHash)
                    }
                    .onFailure { Timber.e(it, "Failed to load video note media") }
                isLoading = false
                Timber.tag("VideoNotePlayback").d(
                    "presenter.ensureLoaded finish keyHash=%d hasLocalUri=%s",
                    keyHash,
                    localMediaUri != null,
                )
            }
        }

        LaunchedEffect(content, keyHash) {
            Timber.tag("VideoNotePlayback").d(
                "presenter.created contentIdentity=%d keyHash=%d isVideoNote=%s hasInitialLocalUri=%s",
                System.identityHashCode(content),
                keyHash,
                content.isVideoNote,
                localMediaUri != null,
            )
        }

        LaunchedEffect(keyHash, isActive, isLoading, localMediaUri != null, playbackCoordinator.activeKey) {
            Timber.tag("VideoNotePlayback").d(
                "presenter.state keyHash=%d isActive=%s isLoading=%s hasLocalUri=%s coordinatorHash=%s",
                keyHash,
                isActive,
                isLoading,
                localMediaUri != null,
                playbackCoordinator.activeKey?.hashCode()?.toString() ?: "null",
            )
        }

        // When content transitions (local-echo → server event) while the note is active,
        // localMediaUri is reset (initialLocalMediaUri returns null for mxc://). Re-trigger download.
        LaunchedEffect(content) {
            if (isActive && localMediaUri == null && !isLoading) {
                ensureLoaded()
            }
        }

        return VideoNotePlaybackState(
            localMediaUri = localMediaUri,
            isLoading = isLoading,
            isActive = isActive,
            eventSink = { event ->
                when (event) {
                    VideoNotePlaybackEvent.TogglePlayback -> {
                        Timber.tag("VideoNotePlayback").d(
                            "presenter.event TogglePlayback keyHash=%d coordinatorBefore=%s",
                            keyHash,
                            playbackCoordinator.activeKey?.hashCode()?.toString() ?: "null",
                        )
                        playbackCoordinator.toggle(key)
                        if (playbackCoordinator.activeKey == key) {
                            ensureLoaded()
                        }
                    }
                    VideoNotePlaybackEvent.PlaybackEnded -> {
                        Timber.tag("VideoNotePlayback").d("presenter.event PlaybackEnded keyHash=%d", keyHash)
                        playbackCoordinator.clear(key)
                    }
                }
            },
        )
    }

    private fun initialLocalMediaUri(): Uri? {
        if (!content.isVideoNote) return null
        val source = content.mediaSource.safeUrl
        return if (source.startsWith("mxc://") || source.isBlank()) null else source.toUri()
    }
}
