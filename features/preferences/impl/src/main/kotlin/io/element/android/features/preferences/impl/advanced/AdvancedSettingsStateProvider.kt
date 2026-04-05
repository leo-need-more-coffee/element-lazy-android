/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.compound.theme.AccentPresets
import io.element.android.compound.theme.ChatBgPresets
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset

open class AdvancedSettingsStateProvider : PreviewParameterProvider<AdvancedSettingsState> {
    override val values: Sequence<AdvancedSettingsState>
        get() = sequenceOf(
            aAdvancedSettingsState(),
            aAdvancedSettingsState(isDeveloperModeEnabled = true),
            aAdvancedSettingsState(isSharePresenceEnabled = true),
            aAdvancedSettingsState(mediaOptimizationState = MediaOptimizationState.AllMedia(isEnabled = true)),
            aAdvancedSettingsState(hideInviteAvatars = true),
            aAdvancedSettingsState(timelineMediaPreviewValue = MediaPreviewValue.Off),
            aAdvancedSettingsState(setHideInviteAvatarsAction = AsyncAction.Loading),
            aAdvancedSettingsState(setTimelineMediaPreviewAction = AsyncAction.Loading),
            aAdvancedSettingsState(mediaOptimizationState = MediaOptimizationState.Split(
                compressImages = true,
                videoPreset = VideoCompressionPreset.HIGH,
            )),
        )
}

fun aAdvancedSettingsState(
    isDeveloperModeEnabled: Boolean = false,
    isSharePresenceEnabled: Boolean = false,
    mediaOptimizationState: MediaOptimizationState = MediaOptimizationState.AllMedia(isEnabled = false),
    theme: ThemeOption = ThemeOption.System,
    accentColor: Color = AccentPresets.default,
    appBgColor: Color? = null,
    fontSize: FontSizePreferenceOption = FontSizePreferenceOption.Normal,
    chatBgColor: Color? = ChatBgPresets.Default,
    outgoingBubbleColor: Color? = null,
    incomingBubbleColor: Color? = null,
    bubbleRadius: BubbleRadiusPreferenceOption = BubbleRadiusPreferenceOption.Standard,
    hideInviteAvatars: Boolean = false,
    timelineMediaPreviewValue: MediaPreviewValue = MediaPreviewValue.On,
    setTimelineMediaPreviewAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    setHideInviteAvatarsAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (AdvancedSettingsEvents) -> Unit = {},
) = AdvancedSettingsState(
    isDeveloperModeEnabled = isDeveloperModeEnabled,
    isSharePresenceEnabled = isSharePresenceEnabled,
    mediaOptimizationState = mediaOptimizationState,
    theme = theme,
    accentColor = accentColor,
    appBgColor = appBgColor,
    fontSize = fontSize,
    chatBgColor = chatBgColor,
    outgoingBubbleColor = outgoingBubbleColor,
    incomingBubbleColor = incomingBubbleColor,
    bubbleRadius = bubbleRadius,
    mediaPreviewConfigState = MediaPreviewConfigState(
        hideInviteAvatars = hideInviteAvatars,
        timelineMediaPreviewValue = timelineMediaPreviewValue,
        setTimelineMediaPreviewAction = setTimelineMediaPreviewAction,
        setHideInviteAvatarsAction = setHideInviteAvatarsAction
    ),
    eventSink = eventSink
)
