/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader

/**
 * Provides a [MatrixMediaLoader] to the composition for sticker and media rendering.
 */
val LocalMatrixMediaLoader = staticCompositionLocalOf<MatrixMediaLoader> {
    error("No MatrixMediaLoader provided via LocalMatrixMediaLoader")
}
