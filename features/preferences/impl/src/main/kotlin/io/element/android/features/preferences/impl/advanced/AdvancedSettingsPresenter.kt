/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.compound.theme.AccentPresets
import io.element.android.compound.theme.BubbleRadiusOption
import io.element.android.compound.theme.FontSizeOption
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.mapToTheme
import io.element.android.compound.theme.parseAccentColor
import io.element.android.compound.theme.parseBubbleRadiusOption
import io.element.android.compound.theme.parseFontSizeOption
import io.element.android.compound.theme.toHexString
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Inject
class AdvancedSettingsPresenter(
    private val appPreferencesStore: AppPreferencesStore,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val mediaPreviewConfigStateStore: MediaPreviewConfigStateStore,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val featureFlagService: FeatureFlagService,
) : Presenter<AdvancedSettingsState> {
    @Composable
    override fun present(): AdvancedSettingsState {
        val isDeveloperModeEnabled by remember {
            appPreferencesStore.isDeveloperModeEnabledFlow()
        }.collectAsState(initial = false)
        val isSharePresenceEnabled by remember {
            sessionPreferencesStore.isSharePresenceEnabled()
        }.collectAsState(initial = true)
        val theme = remember {
            appPreferencesStore.getThemeFlow().mapToTheme()
        }.collectAsState(initial = Theme.System)

        val accentColorHex by remember {
            appPreferencesStore.getAccentColorFlow()
        }.collectAsState(initial = null)
        val accentColor = parseAccentColor(accentColorHex) ?: AccentPresets.default

        val appBgColorHex by remember {
            appPreferencesStore.getAppBgColorFlow()
        }.collectAsState(initial = null)
        val appBgColor = parseAccentColor(appBgColorHex)

        val fontSizeName by remember {
            appPreferencesStore.getFontSizeFlow()
        }.collectAsState(initial = null)
        val fontSize = when (parseFontSizeOption(fontSizeName)) {
            FontSizeOption.Small -> FontSizePreferenceOption.Small
            FontSizeOption.Normal -> FontSizePreferenceOption.Normal
            FontSizeOption.Large -> FontSizePreferenceOption.Large
        }

        val chatBgHex by remember {
            appPreferencesStore.getChatBgColorFlow()
        }.collectAsState(initial = null)
        val chatBgColor = parseAccentColor(chatBgHex)

        val outgoingBubbleColorHex by remember {
            appPreferencesStore.getOutgoingBubbleColorFlow()
        }.collectAsState(initial = null)
        val outgoingBubbleColor = parseAccentColor(outgoingBubbleColorHex)

        val incomingBubbleColorHex by remember {
            appPreferencesStore.getIncomingBubbleColorFlow()
        }.collectAsState(initial = null)
        val incomingBubbleColor = parseAccentColor(incomingBubbleColorHex)

        val bubbleRadiusName by remember {
            appPreferencesStore.getBubbleRadiusFlow()
        }.collectAsState(initial = null)
        val bubbleRadius = when (parseBubbleRadiusOption(bubbleRadiusName)) {
            BubbleRadiusOption.Sharp -> BubbleRadiusPreferenceOption.Sharp
            BubbleRadiusOption.Standard -> BubbleRadiusPreferenceOption.Standard
            BubbleRadiusOption.Round -> BubbleRadiusPreferenceOption.Round
        }

        val mediaPreviewConfigState = mediaPreviewConfigStateStore.state()

        val themeOption by remember {
            derivedStateOf {
                when (theme.value) {
                    Theme.System -> ThemeOption.System
                    Theme.Dark -> ThemeOption.Dark
                    Theme.Light -> ThemeOption.Light
                }
            }
        }

        val hasSplitMediaQualityOptions by produceState<Boolean?>(null) {
            value = featureFlagService.isFeatureEnabled(FeatureFlags.SelectableMediaQuality)
        }

        val mediaOptimizationState by produceState<MediaOptimizationState?>(null) {
            val hasSplitMediaQualityOptionsFlow = featureFlagService.isFeatureEnabledFlow(FeatureFlags.SelectableMediaQuality)
            combine(
                hasSplitMediaQualityOptionsFlow,
                sessionPreferencesStore.doesOptimizeImages(),
                sessionPreferencesStore.getVideoCompressionPreset()
            ) { hasSplitOptions, compressImages, videoPreset ->
                if (hasSplitMediaQualityOptions == true) {
                    value = MediaOptimizationState.Split(
                        compressImages = compressImages,
                        videoPreset = videoPreset,
                    )
                } else if (hasSplitMediaQualityOptions == false) {
                    value = MediaOptimizationState.AllMedia(isEnabled = compressImages)
                }
            }.collect()
        }

        fun handleEvent(event: AdvancedSettingsEvents) {
            when (event) {
                is AdvancedSettingsEvents.SetDeveloperModeEnabled -> sessionCoroutineScope.launch {
                    appPreferencesStore.setDeveloperModeEnabled(event.enabled)
                }
                is AdvancedSettingsEvents.SetSharePresenceEnabled -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setSharePresence(event.enabled)
                }
                is AdvancedSettingsEvents.SetCompressMedia -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setOptimizeImages(event.compress)
                }
                is AdvancedSettingsEvents.SetTheme -> sessionCoroutineScope.launch {
                    when (event.theme) {
                        ThemeOption.System -> appPreferencesStore.setTheme(Theme.System.name)
                        ThemeOption.Dark -> appPreferencesStore.setTheme(Theme.Dark.name)
                        ThemeOption.Light -> appPreferencesStore.setTheme(Theme.Light.name)
                    }
                }
                is AdvancedSettingsEvents.SetAccentColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setAccentColor(event.color.toHexString())
                }
                is AdvancedSettingsEvents.SetAppBgColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setAppBgColor(event.color?.toHexString())
                }
                is AdvancedSettingsEvents.SetFontSize -> sessionCoroutineScope.launch {
                    appPreferencesStore.setFontSize(event.fontSize.name)
                }
                is AdvancedSettingsEvents.SetChatBgColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatBgColor(event.color?.toHexString())
                }
                is AdvancedSettingsEvents.SetOutgoingBubbleColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setOutgoingBubbleColor(event.color?.toHexString())
                }
                is AdvancedSettingsEvents.SetIncomingBubbleColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setIncomingBubbleColor(event.color?.toHexString())
                }
                is AdvancedSettingsEvents.SetBubbleRadius -> sessionCoroutineScope.launch {
                    appPreferencesStore.setBubbleRadius(event.bubbleRadius.name)
                }
                is AdvancedSettingsEvents.SetHideInviteAvatars -> mediaPreviewConfigStateStore.setHideInviteAvatars(event.value)
                is AdvancedSettingsEvents.SetTimelineMediaPreviewValue -> mediaPreviewConfigStateStore.setTimelineMediaPreviewValue(event.value)
                is AdvancedSettingsEvents.SetCompressImages -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setOptimizeImages(event.compress)
                }
                is AdvancedSettingsEvents.SetVideoUploadQuality -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setVideoCompressionPreset(event.videoPreset)
                }
            }
        }

        return AdvancedSettingsState(
            isDeveloperModeEnabled = isDeveloperModeEnabled,
            isSharePresenceEnabled = isSharePresenceEnabled,
            mediaOptimizationState = mediaOptimizationState,
            theme = themeOption,
            accentColor = accentColor,
            appBgColor = appBgColor,
            fontSize = fontSize,
            chatBgColor = chatBgColor,
            outgoingBubbleColor = outgoingBubbleColor,
            incomingBubbleColor = incomingBubbleColor,
            bubbleRadius = bubbleRadius,
            mediaPreviewConfigState = mediaPreviewConfigState,
            eventSink = ::handleEvent,
        )
    }
}
