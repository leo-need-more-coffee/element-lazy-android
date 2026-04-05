/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.compound.theme.AccentPresets
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.toHexString
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferenceDropdown
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ListSupportingTextDefaults
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromMeBackground
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.analytics.compose.LocalAnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AdvancedSettingsView(
    state: AdvancedSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analyticsService = LocalAnalyticsService.current

    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = snackbarMessage)
    var colorPickerTarget by remember { mutableStateOf<ThemeColorPickerTarget?>(null) }

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_advanced_settings),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) {
        PreferenceCategory(title = stringResource(id = R.string.screen_advanced_settings_appearance_section)) {
            ThemePreviewCard(state = state)
            PreferenceDropdown(
                title = stringResource(id = CommonStrings.common_appearance),
                selectedOption = state.theme,
                options = ThemeOption.entries.toImmutableList(),
                onSelectOption = { themeOption ->
                    state.eventSink(AdvancedSettingsEvents.SetTheme(themeOption))
                }
            )
            ThemeColorSetting(
                title = stringResource(id = R.string.screen_advanced_settings_accent_color_title),
                selectedColor = state.accentColor,
                onClick = { colorPickerTarget = ThemeColorPickerTarget.Accent },
                onReset = if (state.accentColor != AccentPresets.default) {
                    { state.eventSink(AdvancedSettingsEvents.SetAccentColor(AccentPresets.default)) }
                } else {
                    null
                },
            )
            ThemeColorSetting(
                title = stringResource(id = R.string.screen_advanced_settings_app_bg_title),
                selectedColor = state.appBgColor,
                defaultColor = ElementTheme.colors.bgCanvasDefault,
                onClick = { colorPickerTarget = ThemeColorPickerTarget.AppBackground },
                onReset = if (state.appBgColor != null) {
                    { state.eventSink(AdvancedSettingsEvents.SetAppBgColor(null)) }
                } else {
                    null
                },
            )
            PreferenceDropdown(
                title = stringResource(id = R.string.screen_advanced_settings_font_size_title),
                selectedOption = state.fontSize,
                options = FontSizePreferenceOption.entries.toImmutableList(),
                onSelectOption = { option ->
                    state.eventSink(AdvancedSettingsEvents.SetFontSize(option.option))
                }
            )
            ThemeColorSetting(
                title = stringResource(id = R.string.screen_advanced_settings_chat_bg_title),
                selectedColor = state.chatBgColor,
                defaultColor = ElementTheme.colors.bgCanvasDefault,
                onClick = { colorPickerTarget = ThemeColorPickerTarget.ChatBackground },
                onReset = if (state.chatBgColor != null) {
                    { state.eventSink(AdvancedSettingsEvents.SetChatBgColor(null)) }
                } else {
                    null
                },
            )
            ThemeColorSetting(
                title = stringResource(id = R.string.screen_advanced_settings_outgoing_bubble_color_title),
                selectedColor = state.outgoingBubbleColor,
                defaultColor = ElementTheme.colors.messageFromMeBackground,
                onClick = { colorPickerTarget = ThemeColorPickerTarget.OutgoingBubble },
                onReset = if (state.outgoingBubbleColor != null) {
                    { state.eventSink(AdvancedSettingsEvents.SetOutgoingBubbleColor(null)) }
                } else {
                    null
                },
            )
            ThemeColorSetting(
                title = stringResource(id = R.string.screen_advanced_settings_incoming_bubble_color_title),
                selectedColor = state.incomingBubbleColor,
                defaultColor = ElementTheme.colors.messageFromOtherBackground,
                onClick = { colorPickerTarget = ThemeColorPickerTarget.IncomingBubble },
                onReset = if (state.incomingBubbleColor != null) {
                    { state.eventSink(AdvancedSettingsEvents.SetIncomingBubbleColor(null)) }
                } else {
                    null
                },
            )
            PreferenceDropdown(
                title = stringResource(id = R.string.screen_advanced_settings_bubble_radius_title),
                selectedOption = state.bubbleRadius,
                options = BubbleRadiusPreferenceOption.entries.toImmutableList(),
                onSelectOption = { option ->
                    state.eventSink(AdvancedSettingsEvents.SetBubbleRadius(option.option))
                }
            )
        }
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = CommonStrings.action_view_source))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_view_source_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isDeveloperModeEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetDeveloperModeEnabled(!state.isDeveloperModeEnabled)) }
        )
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isSharePresenceEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetSharePresenceEnabled(!state.isSharePresenceEnabled)) }
        )
        val compressImages = state.mediaOptimizationState?.shouldCompressImages

        when (state.mediaOptimizationState) {
            null -> Unit
            is MediaOptimizationState.AllMedia -> {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_description))
                    },
                    trailingContent = ListItemContent.Switch(
                        checked = compressImages ?: false,
                    ),
                    onClick = {
                        val newValue = !(compressImages ?: false)
                        analyticsService.captureInteraction(
                            if (newValue) {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                            } else {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                            }
                        )
                        state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                    }
                )
            }
            is MediaOptimizationState.Split -> {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_description))
                    },
                    trailingContent = ListItemContent.Switch(
                        checked = compressImages ?: false,
                    ),
                    onClick = {
                        val newValue = !(compressImages ?: false)
                        analyticsService.captureInteraction(
                            if (newValue) {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                            } else {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                            }
                        )
                        state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                    }
                )

                var displaySelectorDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_title))
                    },
                    supportingContent = {
                        val description = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_description)
                        val quality = when (state.mediaOptimizationState.videoPreset) {
                            VideoCompressionPreset.LOW -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                            VideoCompressionPreset.STANDARD -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                            VideoCompressionPreset.HIGH -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                        }
                        val descriptionWithValue = remember(quality) {
                            String.format(description, quality)
                        }
                        Text(text = descriptionWithValue)
                    },
                    onClick = { displaySelectorDialog = true },
                )

                if (displaySelectorDialog) {
                    VideoQualitySelectorDialog(
                        selectedPreset = state.mediaOptimizationState.videoPreset,
                        onSubmit = { preset ->
                            state.eventSink(AdvancedSettingsEvents.SetVideoUploadQuality(preset))
                            displaySelectorDialog = false
                        },
                        onDismiss = { displaySelectorDialog = false },
                    )
                }
            }
        }

        ModerationAndSafety(state)
    }

    ColorPickerDialog(
        show = colorPickerTarget != null,
        type = ColorPickerType.Classic(
            showAlphaBar = false,
        ),
        onDismissRequest = {
            colorPickerTarget = null
        },
        onPickedColor = { color ->
            when (colorPickerTarget) {
                ThemeColorPickerTarget.Accent -> state.eventSink(AdvancedSettingsEvents.SetAccentColor(color))
                ThemeColorPickerTarget.AppBackground -> state.eventSink(AdvancedSettingsEvents.SetAppBgColor(color))
                ThemeColorPickerTarget.ChatBackground -> state.eventSink(AdvancedSettingsEvents.SetChatBgColor(color))
                ThemeColorPickerTarget.OutgoingBubble -> state.eventSink(AdvancedSettingsEvents.SetOutgoingBubbleColor(color))
                ThemeColorPickerTarget.IncomingBubble -> state.eventSink(AdvancedSettingsEvents.SetIncomingBubbleColor(color))
                null -> Unit
            }
            colorPickerTarget = null
        },
    )
}

@Composable
private fun VideoQualitySelectorDialog(
    selectedPreset: VideoCompressionPreset,
    onSubmit: (VideoCompressionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val videoPresets = VideoCompressionPreset.entries
    var localSelectedPreset by remember { mutableStateOf(selectedPreset) }
    ListDialog(
        title = stringResource(CommonStrings.dialog_video_quality_selector_title),
        subtitle = stringResource(CommonStrings.dialog_default_video_quality_selector_subtitle),
        onSubmit = { onSubmit(localSelectedPreset) },
        onDismissRequest = onDismiss,
        applyPaddingToContents = false,
    ) {
        for (preset in videoPresets) {
            val isSelected = preset == localSelectedPreset
            item(
                key = preset,
                contentType = preset,
            ) {
                val title = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                    VideoCompressionPreset.STANDARD -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                    VideoCompressionPreset.HIGH -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                }
                val subtitle = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(CommonStrings.common_video_quality_low_description)
                    VideoCompressionPreset.STANDARD -> stringResource(CommonStrings.common_video_quality_standard_description)
                    VideoCompressionPreset.HIGH -> stringResource(CommonStrings.common_video_quality_high_description)
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = title,
                            style = ElementTheme.typography.fontBodyLgMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    },
                    leadingContent = ListItemContent.RadioButton(
                        selected = isSelected,
                    ),
                    onClick = {
                        localSelectedPreset = preset
                    },
                )
            }
        }
    }
}

@Composable
private fun ModerationAndSafety(
    state: AdvancedSettingsState,
    modifier: Modifier = Modifier,
) {
    PreferenceCategory(
        modifier = modifier,
        title = stringResource(R.string.screen_advanced_settings_moderation_and_safety_section_title),
        showTopDivider = true
    ) {
        PreferenceSwitch(
            title = stringResource(R.string.screen_advanced_settings_hide_invite_avatars_toggle_title),
            isChecked = state.mediaPreviewConfigState.hideInviteAvatars,
            onCheckedChange = {
                state.eventSink(AdvancedSettingsEvents.SetHideInviteAvatars(it))
            },
            enabled = !state.mediaPreviewConfigState.setHideInviteAvatarsAction.isLoading()
        )
        ListSectionHeader(
            title = stringResource(R.string.screen_advanced_settings_show_media_timeline_title),
            hasDivider = false,
            description = {
                ListSupportingText(
                    text = stringResource(R.string.screen_advanced_settings_show_media_timeline_subtitle),
                    contentPadding = ListSupportingTextDefaults.Padding.None,
                )
            }
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_hide)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Off,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Off))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_private_rooms)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Private,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Private))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_show)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.On,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.On))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
    }
}

@Composable
private fun ThemePreviewCard(
    state: AdvancedSettingsState,
    modifier: Modifier = Modifier,
) {
    val appBackgroundColor = state.appBgColor ?: ElementTheme.colors.bgCanvasDefault
    val previewBackgroundColor = state.chatBgColor ?: appBackgroundColor
    val outgoingBubbleColor = state.outgoingBubbleColor ?: ElementTheme.colors.messageFromMeBackground
    val incomingBubbleColor = state.incomingBubbleColor ?: ElementTheme.colors.messageFromOtherBackground
    val composerColor = state.appBgColor?.let { appBackgroundColor.copy(alpha = 0.92f) } ?: ElementTheme.colors.bgCanvasDefaultLevel1
    val bubbleShape = RoundedCornerShape(state.bubbleRadius.option.radius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(appBackgroundColor)
            .border(
                width = 1.dp,
                color = ElementTheme.colors.borderDisabled,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_advanced_settings_theme_preview_title),
                style = ElementTheme.typography.fontBodyLgMedium,
            )
            Text(
                text = stringResource(R.string.screen_advanced_settings_theme_preview_subtitle),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(previewBackgroundColor)
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PreviewBubble(
                        text = stringResource(R.string.screen_advanced_settings_theme_preview_incoming),
                        bubbleColor = incomingBubbleColor,
                        shape = bubbleShape,
                        alignMine = false,
                    )
                    PreviewBubble(
                        text = stringResource(R.string.screen_advanced_settings_theme_preview_outgoing),
                        bubbleColor = outgoingBubbleColor,
                        shape = bubbleShape,
                        alignMine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(composerColor)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.screen_advanced_settings_theme_preview_composer),
                                color = ElementTheme.colors.textSecondary,
                                style = ElementTheme.typography.fontBodyMdRegular,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ElementTheme.colors.bgAccentRest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CompoundIcons.ArrowRight(),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBubble(
    text: String,
    bubbleColor: Color,
    shape: RoundedCornerShape,
    alignMine: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignMine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = ElementTheme.typography.fontBodyMdRegular,
            )
        }
    }
}

@Composable
private fun ThemeColorSetting(
    title: String,
    selectedColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    defaultColor: Color = selectedColor ?: ElementTheme.colors.bgCanvasDefault,
    onReset: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(text = title)
        },
        supportingContent = {
            Text(
                text = selectedColor?.toHexString() ?: stringResource(id = R.string.screen_advanced_settings_chat_bg_default)
            )
        },
        trailingContent = ListItemContent.Custom { _ ->
            ColorSwatch(color = selectedColor ?: defaultColor)
        },
        modifier = modifier,
        onClick = onClick,
    )
    if (onReset != null) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.screen_advanced_settings_color_picker_reset),
                    color = ElementTheme.colors.textActionAccent,
                )
            },
            modifier = Modifier.padding(start = 16.dp),
            onClick = onReset,
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 1.dp,
                color = ElementTheme.colors.borderDisabled,
                shape = CircleShape,
            )
    )
}

private enum class ThemeColorPickerTarget {
    Accent,
    AppBackground,
    ChatBackground,
    OutgoingBubble,
    IncomingBubble,
}

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewLightPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewLight { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewDarkPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewDark { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: AdvancedSettingsState) {
    AdvancedSettingsView(
        state = state,
        onBackClick = { }
    )
}

@Composable
@PreviewsDayNight
internal fun VideoQualitySelectorDialogPreview() {
    ElementPreview {
        VideoQualitySelectorDialog(
            selectedPreset = VideoCompressionPreset.STANDARD,
            onSubmit = { /* no-op */ },
            onDismiss = { /* no-op */ }
        )
    }
}
