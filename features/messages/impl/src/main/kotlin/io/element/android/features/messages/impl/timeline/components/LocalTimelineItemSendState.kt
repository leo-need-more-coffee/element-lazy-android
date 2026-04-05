/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.runtime.compositionLocalOf
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState

/**
 * Provides the [LocalEventSendState] of the currently rendered timeline item.
 * Set by [TimelineItemEventRow] and read by content views (e.g. video notes).
 *
 * Uses a CompositionLocal rather than parameter passing because the content
 * is wrapped in [androidx.compose.runtime.movableContentOf], which freezes
 * lambda closures but correctly reads CompositionLocals from the call site.
 */
val LocalTimelineItemSendState = compositionLocalOf<LocalEventSendState?> { null }
