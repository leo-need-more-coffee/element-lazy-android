/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.utils.time.formatShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/** Output size in pixels for the square video note (applied by Transformer). */
private const val VIDEO_NOTE_SIZE = 1080

@Composable
internal fun VideoNoteRecorderView(
    state: VideoNoteState.Recording,
    onRecordingCompleted: (Uri) -> Unit,
    onRecordingCancelled: () -> Unit,
    onRecordingFailed: (Throwable) -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val previewUseCase = remember { Preview.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD))
            )
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var currentZoom by remember { mutableFloatStateOf(0f) }
    var isPinching by remember { mutableStateOf(false) }
    val cameraRef by rememberUpdatedState(camera)

    // Плавный возврат к нулевому зуму при отпускании пальцев
    LaunchedEffect(isPinching) {
        if (!isPinching && currentZoom > 0.01f) {
            val startZoom = currentZoom
            val startNs = System.nanoTime()
            val durationNs = 650_000_000L // 650 мс
            while (true) {
                val elapsed = System.nanoTime() - startNs
                if (elapsed >= durationNs) {
                    currentZoom = 0f
                    cameraRef?.cameraControl?.setLinearZoom(0f)
                    break
                }
                val t = elapsed.toFloat() / durationNs
                // cubic ease-out: быстро в начале, плавно в конце
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                currentZoom = startZoom * (1f - eased)
                cameraRef?.cameraControl?.setLinearZoom(currentZoom)
                delay(8) // ~120 fps
            }
        }
    }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var activeRecordingId by remember { mutableIntStateOf(-1) }
    var preparedRecordingId by remember { mutableIntStateOf(-1) }
    var nextSegmentIndex by remember { mutableIntStateOf(0) }
    var cancelledOutput by remember { mutableStateOf(false) }
    var appliedLensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var pendingLensFacing by remember { mutableStateOf<Int?>(null) }
    val recordedSegments = remember { mutableStateListOf<File>() }
    val isSwitchingCamera = pendingLensFacing != null

    LaunchedEffect(Unit) {
        cameraProvider = ProcessCameraProvider.getInstance(context).await()
    }

    LaunchedEffect(cameraProvider, previewView, state.recordingId, appliedLensFacing) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val surfaceView = previewView ?: return@LaunchedEffect
        if (activeRecordingId == state.recordingId) return@LaunchedEffect
        if (preparedRecordingId != state.recordingId) {
            cleanupVideoNoteSegments(recordedSegments)
            recordedSegments.clear()
            nextSegmentIndex = 0
            preparedRecordingId = state.recordingId
        }
        provider.unbindAll()
        previewUseCase.surfaceProvider = surfaceView.surfaceProvider
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.Builder().requireLensFacing(appliedLensFacing).build(),
            previewUseCase,
            videoCapture,
        )
        currentZoom = 0f
        camera?.cameraControl?.setLinearZoom(0f)

        val outputFile = createVideoNoteSegmentFile(context, state.recordingId, nextSegmentIndex)
        nextSegmentIndex += 1
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        val pendingRecording: PendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
        cancelledOutput = false
        activeRecordingId = state.recordingId
        activeRecording = pendingRecording.withAudioEnabled().start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    activeRecordingId = -1
                    provider.unbindAll()
                    when {
                        pendingLensFacing != null -> {
                            if (outputFile.exists()) {
                                recordedSegments += outputFile
                            }
                            appliedLensFacing = pendingLensFacing ?: appliedLensFacing
                            pendingLensFacing = null
                        }
                        event.hasError() -> {
                            outputFile.delete()
                            cleanupVideoNoteSegments(recordedSegments)
                            recordedSegments.clear()
                            nextSegmentIndex = 0
                            preparedRecordingId = -1
                            onRecordingFailed(IllegalStateException("Video note recording failed with error code ${event.error}"))
                        }
                        cancelledOutput -> {
                            outputFile.delete()
                            cleanupVideoNoteSegments(recordedSegments)
                            recordedSegments.clear()
                            nextSegmentIndex = 0
                            preparedRecordingId = -1
                            onRecordingCancelled()
                        }
                        else -> {
                            val completedSegments = buildList {
                                addAll(recordedSegments)
                                if (outputFile.exists()) {
                                    add(outputFile)
                                }
                            }
                            recordedSegments.clear()
                            nextSegmentIndex = 0
                            preparedRecordingId = -1
                            coroutineScope.launch {
                                runCatching {
                                    finalizeVideoNoteRecording(context, state.recordingId, completedSegments)
                                }.onSuccess(onRecordingCompleted)
                                    .onFailure { throwable ->
                                        cleanupVideoNoteSegments(completedSegments)
                                        onRecordingFailed(throwable)
                                    }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(state.action) {
        when (state.action) {
            VideoNoteState.RecordingAction.Active -> Unit
            VideoNoteState.RecordingAction.StopRequested -> {
                activeRecording?.stop()
            }
            VideoNoteState.RecordingAction.CancelRequested -> {
                cancelledOutput = true
                activeRecording?.stop()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            cameraProvider?.unbindAll()
            cleanupVideoNoteSegments(recordedSegments)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.96f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewContext ->
                    PreviewView(previewContext).also { previewView = it }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(280.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var prevDist = 0f
                            var pinchStarted = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size < 2) break

                                val a = pressed[0].position
                                val b = pressed[1].position
                                val dist = Offset(a.x - b.x, a.y - b.y).getDistance()

                                if (!pinchStarted) {
                                    prevDist = dist
                                    pinchStarted = true
                                    isPinching = true
                                } else if (prevDist > 0f) {
                                    val scale = dist / prevDist
                                    // Прямое отображение: коэффициент 2f даёт отзывчивый отклик
                                    val newZoom = (currentZoom + (scale - 1f) * 2f).coerceIn(0f, 1f)
                                    currentZoom = newZoom
                                    cameraRef?.cameraControl?.setLinearZoom(newZoom)
                                }
                                prevDist = dist
                            }

                            if (pinchStarted) isPinching = false
                        }
                    },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.elapsedTime.formatShort(),
                    color = ElementTheme.colors.textPrimary,
                    style = ElementTheme.typography.fontHeadingMdBold,
                )
                Text(
                    text = if (state.isLocked) {
                        stringResource(R.string.screen_message_composer_video_note_locked)
                    } else {
                        stringResource(R.string.screen_message_composer_video_note_gesture_hint)
                    },
                    color = ElementTheme.colors.textSecondary,
                    style = ElementTheme.typography.fontBodyMdRegular,
                )
            }

            IconButton(
                onClick = {
                    val nextLensFacing = when (pendingLensFacing ?: appliedLensFacing) {
                        CameraSelector.LENS_FACING_FRONT -> CameraSelector.LENS_FACING_BACK
                        else -> CameraSelector.LENS_FACING_FRONT
                    }
                    if (activeRecording != null) {
                        pendingLensFacing = nextLensFacing
                        activeRecording?.stop()
                    } else {
                        appliedLensFacing = nextLensFacing
                    }
                },
                modifier = Modifier
                    .align(if (state.isLocked) Alignment.CenterStart else Alignment.BottomStart)
                    .then(
                        if (state.isLocked) {
                            Modifier.padding(start = 24.dp)
                        } else {
                            Modifier
                                .navigationBarsPadding()
                                .padding(start = 24.dp, bottom = 24.dp)
                        }
                    )
                    .size(48.dp)
                    .background(ElementTheme.colors.bgSubtlePrimary, CircleShape),
            ) {
                Icon(
                    imageVector = CompoundIcons.SwitchCameraSolid(),
                    contentDescription = stringResource(id = R.string.screen_message_composer_switch_camera),
                    tint = if (isSwitchingCamera) ElementTheme.colors.iconDisabled else ElementTheme.colors.iconPrimary,
                )
            }

            if (state.isLocked) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onCancelRecording,
                        modifier = Modifier
                            .size(56.dp)
                            .background(ElementTheme.colors.bgSubtlePrimary, CircleShape),
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Delete(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconPrimary,
                        )
                    }
                    IconButton(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .size(72.dp)
                            .background(ElementTheme.colors.bgAccentRest, CircleShape),
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Send(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconOnSolidPrimary,
                        )
                    }
                }
            }
        }
    }
}

private fun createVideoNoteFile(context: android.content.Context, recordingId: Int): File {
    val directory = File(context.cacheDir, "video-notes").apply { mkdirs() }
    return File(directory, "video_note_$recordingId.mp4")
}

private fun createVideoNoteSegmentFile(context: android.content.Context, recordingId: Int, segmentIndex: Int): File {
    val directory = File(context.cacheDir, "video-notes").apply { mkdirs() }
    return File(directory, "video_note_${recordingId}_segment_$segmentIndex.mp4")
}

private fun cleanupVideoNoteSegments(segmentFiles: Iterable<File>) {
    segmentFiles.forEach(File::delete)
}

private suspend fun finalizeVideoNoteRecording(
    context: android.content.Context,
    recordingId: Int,
    segmentFiles: List<File>,
): Uri {
    require(segmentFiles.isNotEmpty()) { "Missing video note segments" }
    val outputFile = createVideoNoteFile(context, recordingId).apply { delete() }
    processVideoNoteWithTransformer(context, segmentFiles, outputFile)
    cleanupVideoNoteSegments(segmentFiles)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile).normalizeScheme()
}

private suspend fun processVideoNoteWithTransformer(
    context: android.content.Context,
    segmentFiles: List<File>,
    outputFile: File,
) {
    val squarePresentation = Presentation.createForWidthAndHeight(
        VIDEO_NOTE_SIZE, VIDEO_NOTE_SIZE, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
    )
    val videoEffects = Effects(emptyList(), listOf(squarePresentation))

    val editedMediaItems = segmentFiles.map { segmentFile ->
        EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(segmentFile)))
            .setEffects(videoEffects)
            .build()
    }

    val composition = Composition.Builder(
        EditedMediaItemSequence.withAudioAndVideoFrom(editedMediaItems)
    ).build()

    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        outputFile.delete()
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()
            continuation.invokeOnCancellation {
                transformer.cancel()
                outputFile.delete()
            }
            transformer.start(composition, outputFile.path)
        }
    }
}

@PreviewsDayNight
@Composable
internal fun VideoNoteRecorderViewPreview() = ElementPreview {
    VideoNoteRecorderView(
        state = VideoNoteState.Recording(
            recordingId = 1,
            elapsedTime = 12.seconds,
            isLocked = true,
            action = VideoNoteState.RecordingAction.Active,
        ),
        onRecordingCompleted = {},
        onRecordingCancelled = {},
        onRecordingFailed = {},
        onStopRecording = {},
        onCancelRecording = {},
    )
}
