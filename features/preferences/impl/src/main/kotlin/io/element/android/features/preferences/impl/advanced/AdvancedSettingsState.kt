/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.element.android.compound.theme.BubbleRadiusOption
import io.element.android.compound.theme.FontSizeOption
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.DropdownOption
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.libraries.ui.strings.CommonStrings

data class AdvancedSettingsState(
    val isDeveloperModeEnabled: Boolean,
    val isSharePresenceEnabled: Boolean,
    val mediaOptimizationState: MediaOptimizationState?,
    val theme: ThemeOption,
    val accentColor: Color,
    val appBgColor: Color?,
    val fontSize: FontSizePreferenceOption,
    val chatBgColor: Color?,
    val outgoingBubbleColor: Color?,
    val incomingBubbleColor: Color?,
    val bubbleRadius: BubbleRadiusPreferenceOption,
    val mediaPreviewConfigState: MediaPreviewConfigState,
    val eventSink: (AdvancedSettingsEvents) -> Unit
)

sealed interface MediaOptimizationState {
    data class AllMedia(val isEnabled: Boolean) : MediaOptimizationState
    data class Split(
        val compressImages: Boolean,
        val videoPreset: VideoCompressionPreset,
    ) : MediaOptimizationState

    val shouldCompressImages: Boolean get() = when (this) {
        is AllMedia -> isEnabled
        is Split -> compressImages
    }
}

enum class ThemeOption : DropdownOption {
    System {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(CommonStrings.common_system)
    },
    Dark {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(CommonStrings.common_dark)
    },
    Light {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(CommonStrings.common_light)
    }
}

enum class FontSizePreferenceOption(val option: FontSizeOption) : DropdownOption {
    Small(FontSizeOption.Small) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.screen_advanced_settings_font_size_small)
    },
    Normal(FontSizeOption.Normal) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.screen_advanced_settings_font_size_normal)
    },
    Large(FontSizeOption.Large) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.screen_advanced_settings_font_size_large)
    },
}

enum class BubbleRadiusPreferenceOption(val option: BubbleRadiusOption) : DropdownOption {
    Sharp(BubbleRadiusOption.Sharp) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.screen_advanced_settings_bubble_radius_sharp)
    },
    Standard(BubbleRadiusOption.Standard) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.screen_advanced_settings_bubble_radius_standard)
    },
    Round(BubbleRadiusOption.Round) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.screen_advanced_settings_bubble_radius_round)
    },
}
