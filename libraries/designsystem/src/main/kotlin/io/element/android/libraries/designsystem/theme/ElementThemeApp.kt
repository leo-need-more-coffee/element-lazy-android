/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.compound.theme.AccentPresets
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.LocalBubbleRadius
import io.element.android.compound.theme.LocalChatBgColor
import io.element.android.compound.theme.LocalIncomingBubbleColor
import io.element.android.compound.theme.LocalOutgoingBubbleColor
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.isDark
import io.element.android.compound.theme.mapToTheme
import io.element.android.compound.theme.neutralDarkColors
import io.element.android.compound.theme.parseAccentColor
import io.element.android.compound.theme.parseBubbleRadiusOption
import io.element.android.compound.theme.parseFontSizeOption
import io.element.android.compound.theme.roundedShapes
import io.element.android.compound.theme.scaledTypography
import io.element.android.compound.theme.withAppBackground
import io.element.android.compound.theme.withAccentColor
import io.element.android.compound.theme.defaultCompoundMaterialTypography
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.preferences.api.store.AppPreferencesStore

val LocalBuildMeta = staticCompositionLocalOf {
    BuildMeta(
        isDebuggable = true,
        buildType = BuildType.DEBUG,
        applicationName = "MyApp",
        productionApplicationName = "MyAppProd",
        desktopApplicationName = "MyAppDesktop",
        applicationId = "AppId",
        isEnterpriseBuild = false,
        lowPrivacyLoggingEnabled = false,
        versionName = "aVersion",
        versionCode = 123,
        gitRevision = "aRevision",
        gitBranchName = "aBranch",
        flavorDescription = "aFlavor",
        flavorShortDescription = "aFlavorShort",
    )
}

/**
 * Theme to use for all the regular screens of the application.
 * Will manage the light / dark theme based on the user preference.
 * Applies neutral dark colors, custom accent colors, custom chat backgrounds,
 * message text sizing, and custom bubble corner radius.
 */
@Composable
fun ElementThemeApp(
    appPreferencesStore: AppPreferencesStore,
    compoundLight: SemanticColors,
    compoundDark: SemanticColors,
    buildMeta: BuildMeta,
    content: @Composable () -> Unit,
) {
    val theme by remember {
        appPreferencesStore.getThemeFlow().mapToTheme()
    }
        .collectAsState(initial = Theme.System)

    val accentColorHex by remember {
        appPreferencesStore.getAccentColorFlow()
    }.collectAsState(initial = null)

    val appBgColorHex by remember {
        appPreferencesStore.getAppBgColorFlow()
    }.collectAsState(initial = null)

    val fontSizeName by remember {
        appPreferencesStore.getFontSizeFlow()
    }.collectAsState(initial = null)

    val chatBgHex by remember {
        appPreferencesStore.getChatBgColorFlow()
    }.collectAsState(initial = null)

    val bubbleRadiusName by remember {
        appPreferencesStore.getBubbleRadiusFlow()
    }.collectAsState(initial = null)

    val outgoingBubbleColorHex by remember {
        appPreferencesStore.getOutgoingBubbleColorFlow()
    }.collectAsState(initial = null)

    val incomingBubbleColorHex by remember {
        appPreferencesStore.getIncomingBubbleColorFlow()
    }.collectAsState(initial = null)

    LaunchedEffect(theme) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
                Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    val accent = parseAccentColor(accentColorHex) ?: AccentPresets.default
    val appBgColor = parseAccentColor(appBgColorHex)

    // Use neutral dark (no blue tint) instead of the default blue-tinted dark
    val neutralDark = neutralDarkColors.copy(
        bgActionPrimaryRest = compoundDark.bgActionPrimaryRest,
        bgActionPrimaryHovered = compoundDark.bgActionPrimaryHovered,
        bgActionPrimaryPressed = compoundDark.bgActionPrimaryPressed,
        bgActionPrimaryDisabled = compoundDark.bgActionPrimaryDisabled,
    )

    val effectiveDark = (appBgColor?.let(neutralDark::withAppBackground) ?: neutralDark).withAccentColor(accent)
    val effectiveLight = (appBgColor?.let(compoundLight::withAppBackground) ?: compoundLight).withAccentColor(accent)

    val fontSizeOption = parseFontSizeOption(fontSizeName)
    val typography = scaledTypography(defaultCompoundMaterialTypography(), fontSizeOption.scale)

    val chatBgColor = parseAccentColor(chatBgHex) // reuse hex parser — works for any color
    val bubbleRadiusOption = parseBubbleRadiusOption(bubbleRadiusName)
    val outgoingBubbleColor = parseAccentColor(outgoingBubbleColorHex)
    val incomingBubbleColor = parseAccentColor(incomingBubbleColorHex)

    CompositionLocalProvider(
        LocalBuildMeta provides buildMeta,
        LocalChatBgColor provides chatBgColor,
        LocalBubbleRadius provides bubbleRadiusOption.radius,
        LocalOutgoingBubbleColor provides outgoingBubbleColor,
        LocalIncomingBubbleColor provides incomingBubbleColor,
    ) {
        ElementTheme(
            darkTheme = theme.isDark(),
            compoundLight = effectiveLight,
            compoundDark = effectiveDark,
            shapes = roundedShapes,
            typography = typography,
            content = content,
        )
    }
}
