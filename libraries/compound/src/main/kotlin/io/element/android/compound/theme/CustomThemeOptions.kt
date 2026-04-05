/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark

/**
 * Neutral dark color scheme — no blue tint, zinc-style dark.
 */
val neutralDarkColors: SemanticColors = compoundColorsDark.copy(
    bgCanvasDefault = Color(0xFF18181B),
    bgCanvasDefaultLevel1 = Color(0xFF27272A),
    bgCanvasDisabled = Color(0xFF1F1F22),
    bgSubtlePrimary = Color(0xFF3F3F46),
    bgSubtleSecondary = Color(0xFF27272A),
    bgSubtleSecondaryLevel0 = Color(0xFF18181B),
    bgActionSecondaryRest = Color(0xFF18181B),
    bgActionTertiaryRest = Color(0xFF18181B),
    bgBadgeDefault = Color(0xFF18181B),
    gradientCriticalStop2 = Color(0xFF18181B),
    gradientInfoStop2 = Color(0xFF18181B),
)

/**
 * More rounded shapes for a modern feel.
 */
val roundedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Preset accent colors for the theme picker.
 */
object AccentPresets {
    val ElementGreen = Color(0xFF129A78)
    val Blue = Color(0xFF40A7E3)
    val Purple = Color(0xFF9C6FDE)
    val Orange = Color(0xFFE8793A)
    val Pink = Color(0xFFE9407A)
    val Teal = Color(0xFF00A99D)
    val Red = Color(0xFFE05252)
    val Indigo = Color(0xFF5C7CFA)

    val all = listOf(ElementGreen, Blue, Purple, Orange, Pink, Teal, Red, Indigo)
    val default = ElementGreen
}

/**
 * Returns a copy of these [SemanticColors] with all accent-related tokens replaced
 * by tonal variants derived from [accent].
 */
fun SemanticColors.withAccentColor(accent: Color): SemanticColors {
    val accentDark1 = lerp(accent, Color.Black, 0.18f)
    val accentDark2 = lerp(accent, Color.Black, 0.32f)
    val accentAlpha50 = accent.copy(alpha = 0.50f)
    val accentAlpha20 = accent.copy(alpha = 0.20f)
    val accentAlpha15 = accent.copy(alpha = 0.15f)
    val accentAlpha10 = accent.copy(alpha = 0.10f)
    val accentAlpha05 = accent.copy(alpha = 0.05f)
    val accentAlpha02 = accent.copy(alpha = 0.02f)

    return copy(
        bgAccentRest = accent,
        bgAccentHovered = accentDark1,
        bgAccentPressed = accentDark2,
        bgAccentSelected = accentAlpha20,
        bgBadgeAccent = accent,
        textBadgeAccent = if (isLight) accentDark2 else accent,
        iconAccentPrimary = accent,
        iconAccentTertiary = accentDark1,
        textActionAccent = accent,
        textSuccessPrimary = accent,
        iconSuccessPrimary = accent,
        borderAccentSubtle = accentAlpha50,
        borderSuccessSubtle = accentAlpha50,
        bgSuccessSubtle = accentAlpha20,
        gradientActionStop1 = accentDark2,
        gradientActionStop2 = accent,
        gradientActionStop3 = accentDark1,
        gradientActionStop4 = accentAlpha50,
        gradientSubtleStop1 = accentAlpha20,
        gradientSubtleStop2 = accentAlpha15,
        gradientSubtleStop3 = accentAlpha10,
        gradientSubtleStop4 = accentAlpha05,
        gradientSubtleStop5 = accentAlpha02,
    )
}

fun SemanticColors.withAppBackground(background: Color): SemanticColors {
    val level1 = if (isLight) lerp(background, Color.Black, 0.06f) else lerp(background, Color.White, 0.08f)
    val subtlePrimary = if (isLight) lerp(background, Color.Black, 0.12f) else lerp(background, Color.White, 0.14f)
    val subtleSecondary = if (isLight) lerp(background, Color.Black, 0.04f) else lerp(background, Color.White, 0.05f)
    val disabled = if (isLight) lerp(background, Color.Black, 0.08f) else lerp(background, Color.White, 0.06f)

    return copy(
        bgCanvasDefault = background,
        bgCanvasDefaultLevel1 = level1,
        bgCanvasDisabled = disabled,
        bgSubtlePrimary = subtlePrimary,
        bgSubtleSecondary = subtleSecondary,
        bgSubtleSecondaryLevel0 = background,
        bgActionSecondaryRest = background,
        bgActionTertiaryRest = background,
        bgBadgeDefault = background,
        gradientCriticalStop2 = background,
        gradientInfoStop2 = background,
    )
}

// ── Font size ─────────────────────────────────────────────────────────────────

enum class FontSizeOption(val scale: Float) {
    Small(0.87f),
    Normal(1.0f),
    Large(1.15f),
}

fun parseFontSizeOption(name: String?): FontSizeOption =
    FontSizeOption.entries.firstOrNull { it.name == name } ?: FontSizeOption.Normal

private fun TextStyle.scaled(scale: Float) = copy(
    fontSize = if (fontSize.type == TextUnitType.Sp) (fontSize.value * scale).sp else fontSize,
    lineHeight = if (lineHeight.type == TextUnitType.Sp) (lineHeight.value * scale).sp else lineHeight,
)

fun scaledTypography(base: Typography, scale: Float): Typography =
    if (scale == 1f) base else Typography(
        displayLarge = base.displayLarge.scaled(scale),
        displayMedium = base.displayMedium.scaled(scale),
        displaySmall = base.displaySmall.scaled(scale),
        headlineLarge = base.headlineLarge.scaled(scale),
        headlineMedium = base.headlineMedium.scaled(scale),
        headlineSmall = base.headlineSmall.scaled(scale),
        titleLarge = base.titleLarge.scaled(scale),
        titleMedium = base.titleMedium.scaled(scale),
        titleSmall = base.titleSmall.scaled(scale),
        bodyLarge = base.bodyLarge.scaled(scale),
        bodyMedium = base.bodyMedium.scaled(scale),
        bodySmall = base.bodySmall.scaled(scale),
        labelLarge = base.labelLarge.scaled(scale),
        labelMedium = base.labelMedium.scaled(scale),
        labelSmall = base.labelSmall.scaled(scale),
    )

// ── Chat background ───────────────────────────────────────────────────────────

object ChatBgPresets {
    /** null = use default theme background */
    val Default: Color? = null
    val WarmBeige = Color(0xFFEFE8DD)
    val SoftBlue = Color(0xFFDCEEFF)
    val SageGreen = Color(0xFFD3EDD4)
    val Lavender = Color(0xFFE8D8F5)
    val Sandy = Color(0xFFF5EDD4)
    val DarkSlate = Color(0xFF2B3547)

    val all: List<Color?> = listOf(Default, WarmBeige, SoftBlue, SageGreen, Lavender, Sandy, DarkSlate)
}

val LocalChatBgColor = staticCompositionLocalOf<Color?> { null }
val LocalOutgoingBubbleColor = staticCompositionLocalOf<Color?> { null }
val LocalIncomingBubbleColor = staticCompositionLocalOf<Color?> { null }

// ── Bubble corner radius ──────────────────────────────────────────────────────

enum class BubbleRadiusOption(val radius: Dp) {
    Sharp(6.dp),
    Standard(12.dp),
    Round(20.dp),
}

fun parseBubbleRadiusOption(name: String?): BubbleRadiusOption =
    BubbleRadiusOption.entries.firstOrNull { it.name == name } ?: BubbleRadiusOption.Standard

val LocalBubbleRadius = staticCompositionLocalOf { BubbleRadiusOption.Standard.radius }

// ── Parsing ───────────────────────────────────────────────────────────────────

/**
 * Parses a hex color string like "#RRGGBB" to a [Color]. Returns null if invalid.
 */
fun parseAccentColor(hex: String?): Color? {
    if (hex == null) return null
    return runCatching {
        val clean = hex.removePrefix("#")
        val rgb = clean.toLong(16)
        Color((rgb or 0xFF000000L).toInt())
    }.getOrNull()
}

/**
 * Encodes a [Color] to a "#RRGGBB" hex string.
 */
fun Color.toHexString(): String {
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}
