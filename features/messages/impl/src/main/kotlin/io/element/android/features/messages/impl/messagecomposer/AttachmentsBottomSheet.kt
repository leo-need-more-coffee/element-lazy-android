/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import io.element.android.features.messages.impl.stickers.AnimatedWebmStickerView
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.stickers.StickerFormat
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.androidutils.ui.hideKeyboard
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.stickers.StickerPackItem
import io.element.android.libraries.matrix.api.stickers.StickerPackManifest
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GALLERY_LOAD_LIMIT = 60
private const val GALLERY_GRID_COLUMNS = 3
private const val STICKER_GRID_COLUMNS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentsBottomSheet(
    state: MessageComposerState,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    enableTextFormatting: Boolean,
    modifier: Modifier = Modifier,
) {
    val localView = LocalView.current
    var isVisible by rememberSaveable { mutableStateOf(state.showAttachmentSourcePicker) }
    val stickerPackArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { state.eventSink(MessageComposerEvent.ImportStickerPackArchive(it)) }
    }

    BackHandler(enabled = isVisible) {
        isVisible = false
    }

    LaunchedEffect(state.showAttachmentSourcePicker, state.showStickerPicker) {
        isVisible = if (state.showAttachmentSourcePicker || state.showStickerPicker) {
            localView.hideKeyboard()
            true
        } else {
            false
        }
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            state.eventSink(MessageComposerEvent.DismissAttachmentMenu)
            state.eventSink(MessageComposerEvent.DismissStickerPicker)
        }
    }

    if (isVisible) {
        ModalBottomSheet(
            modifier = modifier,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            onDismissRequest = { isVisible = false },
        ) {
            if (state.showStickerPicker) {
                key(state.stickerPacks, state.isImportingStickerPack) {
                    StickerPickerPanel(
                        state = state,
                        onImportArchiveClick = {
                            stickerPackArchiveLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                )
                            )
                        },
                        onDismiss = { isVisible = false },
                    )
                }
            } else {
                AttachmentPanel(
                    state = state,
                    enableTextFormatting = enableTextFormatting,
                    onSendLocationClick = onSendLocationClick,
                    onCreatePollClick = onCreatePollClick,
                    onDismiss = { isVisible = false },
                )
            }
        }
    }
}

@Composable
private fun AttachmentPanel(
    state: MessageComposerState,
    enableTextFormatting: Boolean,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val mediaPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var loadGeneration by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) loadGeneration++
    }

    val recentMedia by produceState<ImmutableList<Uri>>(persistentListOf(), loadGeneration) {
        value = withContext(Dispatchers.IO) {
            loadRecentMedia(context).toImmutableList()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(mediaPermission)
        }
    }

    // Gallery height = 90% screen – action bar so the grid fills the expandable space
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val galleryHeight = screenHeight * 0.88f

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // ── Action bar ───────────────────────────────────────────────
        // Rendered FIRST → always visible in both partial and expanded sheet states.
        // Sits on top of the gallery visually via Surface elevation.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ElementTheme.colors.bgCanvasDefault,
            shadowElevation = 4.dp,
        ) {
            ActionBar(
                state = state,
                enableTextFormatting = enableTextFormatting,
                onSendLocationClick = onSendLocationClick,
                onCreatePollClick = onCreatePollClick,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }

        HorizontalDivider(color = ElementTheme.colors.borderInteractiveSecondary)

        // ── Gallery grid ─────────────────────────────────────────────
        // Fixed height so the LazyVerticalGrid has a bounded constraint.
        LazyVerticalGrid(
            columns = GridCells.Fixed(GALLERY_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .height(galleryHeight),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 2.dp),
        ) {
            // Camera tile — same size as photo tiles
            item {
                CameraCell(
                    onPhotoClick = { state.eventSink(MessageComposerEvent.PickAttachmentSource.PhotoFromCamera) },
                    onVideoLongPress = { state.eventSink(MessageComposerEvent.PickAttachmentSource.VideoFromCamera) },
                )
            }
            // Gallery thumbnails
            items(recentMedia) { uri ->
                MediaThumbnailCell(
                    uri = uri,
                    onClick = {
                        onDismiss()
                        state.eventSink(MessageComposerEvent.SendUri(uri))
                    },
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionBar(
    state: MessageComposerState,
    enableTextFormatting: Boolean,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = { Icon(imageVector = CompoundIcons.Sticker(), contentDescription = null, tint = ElementTheme.colors.iconPrimary) },
            label = stringResource(R.string.screen_message_sticker_picker_title),
            onClick = { state.eventSink(MessageComposerEvent.ShowStickerPicker) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            icon = { Icon(imageVector = CompoundIcons.Attachment(), contentDescription = null, tint = ElementTheme.colors.iconPrimary) },
            label = stringResource(R.string.screen_room_attachment_source_files),
            onClick = { state.eventSink(MessageComposerEvent.PickAttachmentSource.FromFiles) },
            modifier = Modifier.weight(1f),
        )
        if (state.canShareLocation) {
            ActionButton(
                icon = { Icon(imageVector = CompoundIcons.LocationPin(), contentDescription = null, tint = ElementTheme.colors.iconPrimary) },
                label = stringResource(R.string.screen_room_attachment_source_location),
                onClick = {
                    state.eventSink(MessageComposerEvent.PickAttachmentSource.Location)
                    onSendLocationClick()
                },
                modifier = Modifier.weight(1f),
            )
        }
        ActionButton(
            icon = { Icon(imageVector = CompoundIcons.Polls(), contentDescription = null, tint = ElementTheme.colors.iconPrimary) },
            label = stringResource(R.string.screen_room_attachment_source_poll),
            onClick = {
                state.eventSink(MessageComposerEvent.PickAttachmentSource.Poll)
                onCreatePollClick()
            },
            modifier = Modifier.weight(1f),
        )
        if (enableTextFormatting) {
            ActionButton(
                icon = { Icon(imageVector = CompoundIcons.TextFormatting(), contentDescription = null, tint = ElementTheme.colors.iconPrimary) },
                label = stringResource(R.string.screen_room_attachment_text_formatting),
                onClick = { state.eventSink(MessageComposerEvent.ToggleTextFormatting(enabled = true)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StickerPickerPanel(
    state: MessageComposerState,
    onImportArchiveClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val packs = state.stickerPacks.filter { pack -> pack.items.isNotEmpty() }
    var selectedPackId by remember(packs) { mutableStateOf(packs.firstOrNull()?.id) }
    LaunchedEffect(packs, selectedPackId) {
        if (packs.none { it.id == selectedPackId }) {
            selectedPackId = packs.firstOrNull()?.id
        }
    }
    val selectedPack = packs.firstOrNull { it.id == selectedPackId } ?: packs.firstOrNull()
    val stickers = selectedPack?.items.orEmpty()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val gridHeight = screenHeight * 0.72f

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ElementTheme.colors.bgCanvasDefault,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.screen_message_sticker_picker_title),
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                    IconButton(
                        onClick = {
                            state.eventSink(MessageComposerEvent.DismissStickerPicker)
                            onDismiss()
                        },
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = stringResource(CommonStrings.action_cancel),
                            tint = ElementTheme.colors.iconPrimary,
                        )
                    }
                }
                FilterChip(
                    onClick = onImportArchiveClick,
                    label = {
                        Text(
                            text = stringResource(
                                if (state.isImportingStickerPack) {
                                    R.string.screen_message_sticker_picker_importing
                                } else {
                                    R.string.action_import_sticker_pack_archive
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        if (state.isImportingStickerPack) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ElementTheme.colors.iconPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = CompoundIcons.Plus(),
                                contentDescription = null,
                                tint = ElementTheme.colors.iconPrimary,
                            )
                        }
                    },
                    selected = false,
                    enabled = !state.isImportingStickerPack,
                )
                if (packs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.screen_message_sticker_picker_empty),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = packs, key = { it.id }) { pack ->
                            PackPreviewChip(
                                pack = pack,
                                selected = pack.id == selectedPack?.id,
                                onClick = { selectedPackId = pack.id },
                            )
                        }
                    }
                    selectedPack?.let { pack ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = pack.displayName,
                                style = ElementTheme.typography.fontBodyMdMedium,
                                color = ElementTheme.colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { state.eventSink(MessageComposerEvent.RemoveStickerPack(pack.id)) },
                                enabled = !state.isImportingStickerPack,
                            ) {
                                Icon(
                                    imageVector = CompoundIcons.Delete(),
                                    contentDescription = stringResource(R.string.action_remove_sticker_pack),
                                    tint = if (state.isImportingStickerPack) {
                                        ElementTheme.colors.iconDisabled
                                    } else {
                                        ElementTheme.colors.iconPrimary
                                    },
                                )
                            }
                        }
                    }
                    if (state.isImportingStickerPack) {
                        Text(
                            text = stringResource(R.string.screen_message_sticker_picker_updating),
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ElementTheme.colors.borderInteractiveSecondary)

        LazyVerticalGrid(
            columns = GridCells.Fixed(STICKER_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 2.dp),
        ) {
            items(stickers, key = StickerPackItem::id) { sticker ->
                StickerThumbnailCell(
                    sticker = sticker,
                    onClick = {
                        selectedPack?.let {
                            onDismiss()
                            state.eventSink(MessageComposerEvent.SendSticker(it, sticker))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PackPreviewChip(
    pack: StickerPackManifest,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (selected) ElementTheme.colors.bgAccentRest else ElementTheme.colors.bgSubtlePrimary,
            )
            .border(
                width = 2.dp,
                color = if (selected) ElementTheme.colors.borderFocused else Color.Transparent,
                shape = CircleShape,
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        pack.items.firstOrNull()?.let { sticker ->
            StickerThumbnailCell(
                sticker = sticker,
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Icon(
            imageVector = CompoundIcons.Sticker(),
            contentDescription = pack.displayName,
            tint = if (selected) ElementTheme.colors.iconOnSolidPrimary else ElementTheme.colors.iconPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(ElementTheme.colors.bgSubtlePrimary),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyXsRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraCell(
    onPhotoClick: () -> Unit,
    onVideoLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(ElementTheme.colors.bgSubtlePrimary)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onPhotoClick() },
                    onLongPress = { onVideoLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgAccentRest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CompoundIcons.TakePhoto(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(R.string.screen_room_attachment_source_camera_video_hold),
                style = ElementTheme.typography.fontBodyXsRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MediaThumbnailCell(
    uri: Uri,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun StickerThumbnailCell(
    sticker: StickerPackItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (sticker.source.format == StickerFormat.WEBM && sticker.thumbnail == null) {
            AnimatedWebmStickerView(
                mediaSource = MediaSource(sticker.source.url),
                mimeType = sticker.source.mimeType,
                filename = sticker.displayName,
                playWhenReady = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val previewAsset = sticker.thumbnail ?: sticker.source
            AsyncImage(
                model = MediaRequestData(
                    source = MediaSource(previewAsset.url),
                    kind = MediaRequestData.Kind.File(
                        fileName = sticker.displayName,
                        mimeType = previewAsset.mimeType,
                    ),
                ),
                contentDescription = sticker.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────

private fun loadRecentMedia(context: android.content.Context): List<Uri> {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    return runCatching {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext() && uris.size < GALLERY_LOAD_LIMIT) {
                val id = cursor.getLong(idColumn)
                uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
            }
        }
        uris
    }.getOrElse { emptyList() }
}

// ────────────────────────────────────────────────────────────────────────────

@PreviewsDayNight
@Composable
internal fun AttachmentPanelPreview() = ElementPreview {
    AttachmentPanel(
        state = aMessageComposerState(canShareLocation = true),
        enableTextFormatting = true,
        onSendLocationClick = {},
        onCreatePollClick = {},
        onDismiss = {},
    )
}
