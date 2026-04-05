/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.Composer
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.features.location.api.LocationService
import io.element.android.features.messages.impl.MessagesNavigator
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.attachments.Attachment
import io.element.android.features.messages.impl.attachments.Attachment.Media
import io.element.android.features.messages.impl.attachments.preview.error.sendAttachmentError
import io.element.android.features.messages.impl.draft.ComposerDraftService
import io.element.android.features.messages.impl.messagecomposer.suggestions.RoomAliasSuggestionsDataSource
import io.element.android.features.messages.impl.messagecomposer.suggestions.SuggestionsProcessor
import io.element.android.features.messages.impl.stickers.StickerPackService
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.features.messages.impl.utils.TextPillificationHelper
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.room.IntentionalMention
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.draft.ComposerDraft
import io.element.android.libraries.matrix.api.room.draft.ComposerDraftType
import io.element.android.libraries.matrix.api.room.getDirectRoomMember
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.stickers.StickerFormat
import io.element.android.libraries.matrix.api.stickers.StickerPackItem
import io.element.android.libraries.matrix.api.stickers.StickerPackManifest
import io.element.android.libraries.matrix.api.room.powerlevels.use
import io.element.android.libraries.matrix.api.timeline.TimelineException
import io.element.android.libraries.matrix.api.timeline.item.event.toEventOrTransactionId
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import io.element.android.libraries.matrix.ui.messages.reply.map
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaviewer.api.local.LocalMediaFactory
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.push.api.notifications.conversations.NotificationConversationService
import io.element.android.libraries.slashcommands.api.SlashCommand
import io.element.android.libraries.slashcommands.api.SlashCommandService
import io.element.android.libraries.slashcommands.api.message
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.textcomposer.mentions.MentionSpanProvider
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MarkdownTextEditorState
import io.element.android.libraries.textcomposer.model.Message
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.MessageComposerRecorderMode
import io.element.android.libraries.textcomposer.model.Suggestion
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.libraries.textcomposer.model.rememberMarkdownTextEditorState
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import io.element.android.wysiwyg.compose.RichTextEditorState
import io.element.android.wysiwyg.display.TextDisplay
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.io.File
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.element.android.libraries.core.mimetype.MimeTypes.Any as AnyMimeTypes

@Suppress("LargeClass")
@AssistedInject
class MessageComposerPresenter(
    @Assisted private val navigator: MessagesNavigator,
    @Assisted private val timelineController: TimelineController,
    @Assisted private val isInThread: Boolean,
    @ApplicationContext private val context: Context,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val room: JoinedRoom,
    private val mediaPickerProvider: PickerProvider,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val localMediaFactory: LocalMediaFactory,
    mediaSenderFactory: MediaSenderFactory,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val analyticsService: AnalyticsService,
    private val locationService: LocationService,
    private val messageComposerContext: DefaultMessageComposerContext,
    private val richTextEditorStateFactory: RichTextEditorStateFactory,
    private val roomAliasSuggestionsDataSource: RoomAliasSuggestionsDataSource,
    private val permalinkParser: PermalinkParser,
    private val permalinkBuilder: PermalinkBuilder,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
    private val draftService: ComposerDraftService,
    private val mentionSpanProvider: MentionSpanProvider,
    private val pillificationHelper: TextPillificationHelper,
    private val suggestionsProcessor: SuggestionsProcessor,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    private val notificationConversationService: NotificationConversationService,
    private val slashCommandService: SlashCommandService,
    private val stickerPackService: StickerPackService,
) : Presenter<MessageComposerState> {
    @AssistedFactory
    interface Factory {
        fun create(
            timelineController: TimelineController,
            navigator: MessagesNavigator,
            isInThread: Boolean,
        ): MessageComposerPresenter
    }

    private val mediaSender = mediaSenderFactory.create(timelineMode = timelineController.mainTimelineMode())

    private val cameraPermissionPresenter = permissionsPresenterFactory.create(Manifest.permission.CAMERA)
    private var pendingEvent: MessageComposerEvent? = null
    private val suggestionSearchTrigger = MutableStateFlow<Suggestion?>(null)

    // Used to disable some UI related elements in tests
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var isTesting: Boolean = false

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var showTextFormatting: Boolean by mutableStateOf(false)

    @SuppressLint("UnsafeOptInUsageError")
    @Composable
    override fun present(): MessageComposerState {
        val localCoroutineScope = rememberCoroutineScope()

        val roomInfo by room.roomInfoFlow.collectAsState()

        val richTextEditorState = richTextEditorStateFactory.remember()
        if (isTesting) {
            richTextEditorState.isReadyToProcessActions = true
        }
        val markdownTextEditorState = rememberMarkdownTextEditorState(initialText = null, initialFocus = false)

        val cameraPermissionState = cameraPermissionPresenter.present()

        val canShareLocation = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            canShareLocation.value = locationService.isServiceAvailable()
        }

        val galleryMediaPicker = mediaPickerProvider.registerGalleryPicker { uri, mimeType ->
            handlePickedMedia(uri, mimeType)
        }
        val filesPicker = mediaPickerProvider.registerFilePicker(AnyMimeTypes) { uri, mimeType ->
            handlePickedMedia(uri, mimeType ?: MimeTypes.OctetStream)
        }
        val cameraPhotoPicker = mediaPickerProvider.registerCameraPhotoPicker { uri ->
            handlePickedMedia(uri, MimeTypes.Jpeg)
        }
        val cameraVideoPicker = mediaPickerProvider.registerCameraVideoPicker { uri ->
            handlePickedMedia(uri, MimeTypes.Mp4)
        }
        val isFullScreen = rememberSaveable {
            mutableStateOf(false)
        }
        var showAttachmentSourcePicker: Boolean by remember { mutableStateOf(false) }
        var showStickerPicker by remember { mutableStateOf(false) }
        var isImportingStickerPack by remember { mutableStateOf(false) }
        var stickerPacks by remember { mutableStateOf<List<StickerPackManifest>>(emptyList()) }
        var recorderMode by rememberSaveable { mutableStateOf(MessageComposerRecorderMode.Audio) }
        var videoNoteState: VideoNoteState by remember { mutableStateOf(VideoNoteState.Hidden) }
        var videoNoteStartEpochMs by remember { mutableStateOf<Long?>(null) }
        var videoNoteTimerJob by remember { mutableStateOf<Job?>(null) }
        var nextVideoRecordingId by remember { mutableIntStateOf(0) }

        val sendTypingNotifications by remember {
            sessionPreferencesStore.isSendTypingNotificationsEnabled()
        }.collectAsState(initial = true)

        suspend fun refreshStickerPacks() {
            stickerPacks = stickerPackService.getPacks()
                .getOrDefault(emptyList())
                .filter { pack -> pack.items.isNotEmpty() }
        }

        LaunchedEffect(Unit) {
            refreshStickerPacks()
        }

        LaunchedEffect(cameraPermissionState.permissionGranted) {
            if (cameraPermissionState.permissionGranted) {
                when (pendingEvent) {
                    is MessageComposerEvent.PickAttachmentSource.PhotoFromCamera -> cameraPhotoPicker.launch()
                    is MessageComposerEvent.PickAttachmentSource.VideoFromCamera -> cameraVideoPicker.launch()
                    else -> Unit
                }
                pendingEvent = null
            }
        }

        val suggestions = remember { mutableStateListOf<ResolvedSuggestion>() }
        ResolveSuggestionsEffect(suggestions)

        DisposableEffect(Unit) {
            // Declare that the user is not typing anymore when the composer is disposed
            onDispose {
                sessionCoroutineScope.launch {
                    if (sendTypingNotifications) {
                        room.typingNotice(false)
                    }
                }
            }
        }

        val textEditorState by rememberUpdatedState(
            if (showTextFormatting) {
                TextEditorState.Rich(richTextEditorState, roomInfo.isEncrypted == true)
            } else {
                TextEditorState.Markdown(markdownTextEditorState, roomInfo.isEncrypted == true)
            }
        )

        val slashCommandAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        LaunchedEffect(Unit) {
            val draft = draftService.loadDraft(
                roomId = room.roomId,
                // TODO support threads in composer
                threadRoot = null,
                isVolatile = false
            )
            if (draft != null) {
                applyDraft(draft, markdownTextEditorState, richTextEditorState)
            }
        }

        fun stopVideoNoteTimer() {
            videoNoteTimerJob?.cancel()
            videoNoteTimerJob = null
            videoNoteStartEpochMs = null
        }

        fun startVideoNoteTimer(recordingId: Int) {
            stopVideoNoteTimer()
            val startedAt = System.currentTimeMillis()
            videoNoteStartEpochMs = startedAt
            videoNoteTimerJob = localCoroutineScope.launch {
                while (true) {
                    val currentState = videoNoteState as? VideoNoteState.Recording ?: break
                    if (currentState.recordingId != recordingId) break
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0).toInt().milliseconds
                    videoNoteState = currentState.copy(elapsedTime = elapsed)
                    delay(100)
                }
            }
        }

        fun beginVideoNoteRecording() {
            nextVideoRecordingId += 1
            val recordingId = nextVideoRecordingId
            videoNoteState = VideoNoteState.Recording(
                recordingId = recordingId,
                elapsedTime = ZERO,
                isLocked = false,
                action = VideoNoteState.RecordingAction.Active,
            )
            startVideoNoteTimer(recordingId)
        }

        fun resetVideoNoteState() {
            stopVideoNoteTimer()
            videoNoteState = VideoNoteState.Hidden
        }

        fun finishVideoNoteRecording(cancelled: Boolean) {
            if (videoNoteState is VideoNoteState.RequestingPermissions) {
                resetVideoNoteState()
                return
            }
            val currentState = videoNoteState as? VideoNoteState.Recording ?: return
            if (cancelled && currentState.action == VideoNoteState.RecordingAction.CancelRequested) {
                resetVideoNoteState()
                return
            }
            val nextAction = if (cancelled) {
                VideoNoteState.RecordingAction.CancelRequested
            } else {
                VideoNoteState.RecordingAction.StopRequested
            }
            videoNoteState = currentState.copy(action = nextAction)
        }

        fun handleEvent(event: MessageComposerEvent) {
            when (event) {
                MessageComposerEvent.ToggleFullScreenState -> isFullScreen.value = !isFullScreen.value
                MessageComposerEvent.CloseSpecialMode -> {
                    if (messageComposerContext.composerMode.isEditing) {
                        localCoroutineScope.launch {
                            resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = true)
                        }
                    } else {
                        messageComposerContext.composerMode = MessageComposerMode.Normal
                    }
                }
                is MessageComposerEvent.SendMessage -> {
                    sessionCoroutineScope.sendMessage(
                        markdownTextEditorState = markdownTextEditorState,
                        richTextEditorState = richTextEditorState,
                        slashCommandAction = slashCommandAction,
                    )
                }
                is MessageComposerEvent.SendUri -> {
                    val inReplyToEventId = (messageComposerContext.composerMode as? MessageComposerMode.Reply)?.eventId
                    sessionCoroutineScope.sendAttachment(
                        attachment = Media(
                            localMedia = localMediaFactory.createFromUri(
                                uri = event.uri,
                                mimeType = null,
                                name = null,
                                formattedFileSize = null
                            ),
                        ),
                        inReplyToEventId = inReplyToEventId,
                    )

                    // Reset composer since the attachment has been sent
                    messageComposerContext.composerMode = MessageComposerMode.Normal
                }
                is MessageComposerEvent.SendSticker -> {
                    showAttachmentSourcePicker = false
                    showStickerPicker = false
                    sessionCoroutineScope.launch {
                        sendSticker(event.pack, event.sticker)
                    }
                }
                is MessageComposerEvent.SetMode -> {
                    localCoroutineScope.setMode(event.composerMode, markdownTextEditorState, richTextEditorState)
                }
                MessageComposerEvent.AddAttachment -> localCoroutineScope.launch {
                    showStickerPicker = false
                    showAttachmentSourcePicker = true
                }
                MessageComposerEvent.ShowStickerPicker -> localCoroutineScope.launch {
                    showStickerPicker = true
                    showAttachmentSourcePicker = false
                    refreshStickerPacks()
                }
                MessageComposerEvent.DismissStickerPicker -> showStickerPicker = false
                is MessageComposerEvent.ImportStickerPackArchive -> localCoroutineScope.launch {
                    isImportingStickerPack = true
                    stickerPackService.importPackArchive(event.uri)
                        .onSuccess { pack ->
                            stickerPacks = (stickerPacks + pack)
                                .distinctBy { it.id }
                                .filter { it.items.isNotEmpty() }
                            snackbarDispatcher.post(SnackbarMessage(R.string.screen_message_sticker_pack_added))
                        }
                        .onFailure { cause ->
                            Timber.e(cause, "Failed to import sticker pack archive")
                            snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                        }
                    isImportingStickerPack = false
                }
                is MessageComposerEvent.RemoveStickerPack -> localCoroutineScope.launch {
                    isImportingStickerPack = true
                    stickerPackService.removePack(event.packId)
                        .onSuccess {
                            stickerPacks = stickerPacks.filterNot { it.id == event.packId }
                            snackbarDispatcher.post(SnackbarMessage(R.string.screen_message_sticker_pack_removed))
                        }
                        .onFailure { cause ->
                            Timber.e(cause, "Failed to remove sticker pack")
                            snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                        }
                    isImportingStickerPack = false
                }
                MessageComposerEvent.DismissAttachmentMenu -> showAttachmentSourcePicker = false
                MessageComposerEvent.PickAttachmentSource.FromGallery -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    galleryMediaPicker.launch()
                }
                MessageComposerEvent.PickAttachmentSource.FromFiles -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    filesPicker.launch()
                }
                MessageComposerEvent.PickAttachmentSource.PhotoFromCamera -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    if (cameraPermissionState.permissionGranted) {
                        cameraPhotoPicker.launch()
                    } else {
                        pendingEvent = event
                        cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                    }
                }
                MessageComposerEvent.PickAttachmentSource.VideoFromCamera -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    if (cameraPermissionState.permissionGranted) {
                        cameraVideoPicker.launch()
                    } else {
                        pendingEvent = event
                        cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                    }
                }
                MessageComposerEvent.PickAttachmentSource.Location -> {
                    showAttachmentSourcePicker = false
                    // Navigation to the location picker screen is done at the view layer
                }
                MessageComposerEvent.PickAttachmentSource.Poll -> {
                    showAttachmentSourcePicker = false
                    // Navigation to the create poll screen is done at the view layer
                }
                is MessageComposerEvent.ToggleTextFormatting -> {
                    showAttachmentSourcePicker = false
                    localCoroutineScope.toggleTextFormatting(event.enabled, markdownTextEditorState, richTextEditorState)
                }
                is MessageComposerEvent.Error -> {
                    analyticsService.trackError(event.error)
                }
                is MessageComposerEvent.TypingNotice -> {
                    if (sendTypingNotifications) {
                        localCoroutineScope.launch {
                            room.typingNotice(event.isTyping)
                        }
                    }
                }
                is MessageComposerEvent.SuggestionReceived -> {
                    suggestionSearchTrigger.value = event.suggestion
                }
                is MessageComposerEvent.InsertSuggestion -> {
                    localCoroutineScope.launch {
                        if (showTextFormatting) {
                            when (val suggestion = event.resolvedSuggestion) {
                                is ResolvedSuggestion.AtRoom -> {
                                    richTextEditorState.insertAtRoomMentionAtSuggestion()
                                }
                                is ResolvedSuggestion.Member -> {
                                    val text = suggestion.roomMember.userId.value
                                    val link = permalinkBuilder.permalinkForUser(suggestion.roomMember.userId).getOrNull() ?: return@launch
                                    richTextEditorState.insertMentionAtSuggestion(text = text, link = link)
                                }
                                is ResolvedSuggestion.Alias -> {
                                    val text = suggestion.roomAlias.value
                                    val link = permalinkBuilder.permalinkForRoomAlias(suggestion.roomAlias).getOrNull() ?: return@launch
                                    richTextEditorState.insertMentionAtSuggestion(text = text, link = link)
                                }
                                is ResolvedSuggestion.Command -> {
                                    richTextEditorState.replaceSuggestion(suggestion.command.command)
                                }
                            }
                        } else if (markdownTextEditorState.currentSuggestion != null) {
                            markdownTextEditorState.insertSuggestion(
                                resolvedSuggestion = event.resolvedSuggestion,
                                mentionSpanProvider = mentionSpanProvider,
                            )
                            suggestionSearchTrigger.value = null
                        }
                    }
                }
                MessageComposerEvent.SaveDraft -> {
                    val draft = createDraftFromState(markdownTextEditorState, richTextEditorState)
                    sessionCoroutineScope.updateDraft(draft, isVolatile = false)
                }
                MessageComposerEvent.ClearSlashError -> {
                    slashCommandAction.value = AsyncAction.Uninitialized
                }
                is MessageComposerEvent.SetRecorderMode -> {
                    recorderMode = event.mode
                }
                MessageComposerEvent.StartVideoNoteRecording -> {
                    showAttachmentSourcePicker = false
                    videoNoteState = VideoNoteState.RequestingPermissions
                }
                MessageComposerEvent.VideoNotePermissionsGranted -> {
                    beginVideoNoteRecording()
                }
                MessageComposerEvent.LockVideoNoteRecording -> {
                    val currentState = videoNoteState as? VideoNoteState.Recording
                    if (currentState != null) {
                        videoNoteState = currentState.copy(isLocked = true)
                    }
                }
                MessageComposerEvent.FinishVideoNoteRecording -> {
                    finishVideoNoteRecording(cancelled = false)
                }
                MessageComposerEvent.CancelVideoNoteRecording -> {
                    finishVideoNoteRecording(cancelled = true)
                }
                is MessageComposerEvent.VideoNoteRecordingCompleted -> {
                    videoNoteState = VideoNoteState.Processing(event.uri)
                    sessionCoroutineScope.launch {
                        try {
                            sendVideoNote(event.uri)
                        } finally {
                            resetVideoNoteState()
                        }
                    }
                }
                is MessageComposerEvent.VideoNoteRecordingFailed -> {
                    Timber.e(event.throwable, "Failed to record video note")
                    resetVideoNoteState()
                    snackbarDispatcher.post(SnackbarMessage(sendAttachmentError(event.throwable)))
                }
                is MessageComposerEvent.LifecycleEvent -> {
                    when (event.event) {
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                        androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                            if (videoNoteState is VideoNoteState.Recording || videoNoteState is VideoNoteState.RequestingPermissions) {
                                resetVideoNoteState()
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        val resolveMentionDisplay = remember {
            { text: String, url: String ->
                val mentionSpan = mentionSpanProvider.getMentionSpanFor(text, url)
                if (mentionSpan != null) {
                    TextDisplay.Custom(mentionSpan)
                } else {
                    TextDisplay.Plain
                }
            }
        }

        val resolveAtRoomMentionDisplay = remember {
            {
                val mentionSpan = mentionSpanProvider.createEveryoneMentionSpan()
                TextDisplay.Custom(mentionSpan)
            }
        }

        return MessageComposerState(
            textEditorState = textEditorState,
            isFullScreen = isFullScreen.value,
            mode = messageComposerContext.composerMode,
            showAttachmentSourcePicker = showAttachmentSourcePicker,
            showStickerPicker = showStickerPicker,
            isImportingStickerPack = isImportingStickerPack,
            showTextFormatting = showTextFormatting,
            stickerPacks = stickerPacks.toImmutableList(),
            canShareLocation = canShareLocation.value,
            recorderMode = recorderMode,
            videoNoteState = videoNoteState,
            suggestions = suggestions.toImmutableList(),
            resolveMentionDisplay = resolveMentionDisplay,
            resolveAtRoomMentionDisplay = resolveAtRoomMentionDisplay,
            slashCommandAction = slashCommandAction.value,
            eventSink = ::handleEvent,
        )
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun ResolveSuggestionsEffect(
        suggestions: SnapshotStateList<ResolvedSuggestion>,
    ) {
        LaunchedEffect(Unit) {
            val currentUserId = room.sessionId

            suspend fun canSendRoomMention(): Boolean {
                val userCanSendAtRoom = room.roomPermissions().use(false) { perms ->
                    perms.canOwnUserTriggerRoomNotification()
                }
                return !room.isDm() && userCanSendAtRoom
            }

            // This will trigger a search immediately when `@` is typed
            val mentionStartTrigger = suggestionSearchTrigger.filter { it?.text.isNullOrEmpty() }
            // This will start a search when the user changes the text after the `@` with a debounce to prevent too much wasted work
            val mentionCompletionTrigger = suggestionSearchTrigger.debounce(0.3.seconds).filter { !it?.text.isNullOrEmpty() }

            val mentionTriggerFlow = merge(mentionStartTrigger, mentionCompletionTrigger)

            val roomAliasSuggestionsFlow = roomAliasSuggestionsDataSource
                .getAllRoomAliasSuggestions()
                .stateIn(this, SharingStarted.Lazily, emptyList())

            combine(mentionTriggerFlow, room.membersStateFlow, roomAliasSuggestionsFlow) { suggestion, roomMembersState, roomAliasSuggestions ->
                val result = suggestionsProcessor.process(
                    suggestion = suggestion,
                    roomMembersState = roomMembersState,
                    roomAliasSuggestions = roomAliasSuggestions,
                    currentUserId = currentUserId,
                    canSendRoomMention = ::canSendRoomMention,
                    isInThread = isInThread,
                )
                suggestions.clear()
                suggestions.addAll(result)
            }
                .collect()
        }
    }

    private fun CoroutineScope.sendMessage(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        slashCommandAction: MutableState<AsyncAction<Unit>>,
    ) = launch {
        val message = currentComposerMessage(markdownTextEditorState, richTextEditorState, withMentions = true)
        val capturedMode = messageComposerContext.composerMode

        val slashCommand = if (capturedMode is MessageComposerMode.Normal) {
            slashCommandService.parse(
                textMessage = message.markdown,
                formattedMessage = message.html,
                isInThreadTimeline = isInThread,
            )
        } else {
            SlashCommand.NotACommand
        }

        when (slashCommand) {
            is SlashCommand.NotACommand -> Unit
            is SlashCommand.Error -> {
                slashCommandAction.value = AsyncAction.Failure(Exception(slashCommand.message()))
                return@launch
            }
            is SlashCommand.SlashCommandNavigation -> {
                when (slashCommand) {
                    is SlashCommand.ShowUser -> {
                        navigator.navigateToMember(slashCommand.userId)
                    }
                    SlashCommand.DevTools -> {
                        navigator.navigateToDeveloperSettings()
                    }
                }
                resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = capturedMode is MessageComposerMode.Edit)
                return@launch
            }
            is SlashCommand.SlashCommandSendMessage -> {
                timelineController.invokeOnCurrentTimeline {
                    slashCommandService.proceedSendMessage(slashCommand, this)
                        .onFailure { cause ->
                            Timber.e(cause, "Failed to proceed with admin slash command")
                            slashCommandAction.value = AsyncAction.Failure(cause)
                        }
                        .onSuccess {
                            // Reset composer
                            resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = capturedMode is MessageComposerMode.Edit)
                        }
                }
                return@launch
            }
            is SlashCommand.SlashCommandAdmin -> {
                slashCommandAction.value = AsyncAction.Loading
                slashCommandService.proceedAdmin(slashCommand)
                    .onFailure { cause ->
                        Timber.e(cause, "Failed to proceed with admin slash command")
                        slashCommandAction.value = AsyncAction.Failure(cause)
                    }
                    .onSuccess {
                        // Reset composer
                        resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = capturedMode is MessageComposerMode.Edit)
                        slashCommandAction.value = AsyncAction.Uninitialized
                    }
                return@launch
            }
        }

        // Reset composer right away
        resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = capturedMode is MessageComposerMode.Edit)
        when (capturedMode) {
            is MessageComposerMode.Attachment,
            is MessageComposerMode.Normal -> timelineController.invokeOnCurrentTimeline {
                sendMessage(
                    body = message.markdown,
                    htmlBody = message.html,
                    intentionalMentions = message.intentionalMentions
                )
            }
            is MessageComposerMode.Edit -> {
                timelineController.invokeOnCurrentTimeline {
                    // First try to edit the message in the current timeline
                    editMessage(capturedMode.eventOrTransactionId, message.markdown, message.html, message.intentionalMentions)
                        .onFailure { cause ->
                            val eventId = capturedMode.eventOrTransactionId.eventId
                            if (cause is TimelineException.EventNotFound && eventId != null) {
                                // if the event is not found in the timeline, try to edit the message directly
                                room.editMessage(eventId, message.markdown, message.html, message.intentionalMentions)
                            }
                        }
                }
            }
            is MessageComposerMode.EditCaption -> {
                timelineController.invokeOnCurrentTimeline {
                    editCaption(
                        capturedMode.eventOrTransactionId,
                        caption = message.markdown,
                        formattedCaption = message.html
                    )
                }
            }
            is MessageComposerMode.Reply -> {
                timelineController.invokeOnCurrentTimeline {
                    with(capturedMode) {
                        replyMessage(
                            body = message.markdown,
                            htmlBody = message.html,
                            intentionalMentions = message.intentionalMentions,
                            repliedToEventId = eventId,
                        )
                    }
                }
            }
        }

        val roomInfo = room.info()
        val roomMembers = room.membersStateFlow.value

        notificationConversationService.onSendMessage(
            sessionId = room.sessionId,
            roomId = roomInfo.id,
            roomName = roomInfo.name ?: roomInfo.id.value,
            roomIsDirect = roomInfo.isDm,
            roomAvatarUrl = roomInfo.avatarUrl ?: roomMembers.getDirectRoomMember(roomInfo = roomInfo, sessionId = room.sessionId)?.avatarUrl,
        )

        analyticsService.capture(
            Composer(
                inThread = capturedMode.inThread,
                isEditing = capturedMode.isEditing,
                isReply = capturedMode.isReply,
                // Set proper type when we'll be sending other types of messages.
                messageType = Composer.MessageType.Text,
            )
        )
    }

    private fun CoroutineScope.sendAttachment(
        attachment: Attachment,
        inReplyToEventId: EventId?,
    ) = when (attachment) {
        is Attachment.Media -> {
            launch {
                sendMedia(
                    uri = attachment.localMedia.uri,
                    mimeType = attachment.localMedia.info.mimeType,
                    inReplyToEventId = inReplyToEventId,
                )
            }
        }
    }

    private fun handlePickedMedia(
        uri: Uri?,
        mimeType: String? = null,
    ) {
        uri ?: return
        val localMedia = localMediaFactory.createFromUri(
            uri = uri,
            mimeType = mimeType,
            name = null,
            formattedFileSize = null
        )
        val mediaAttachment = Attachment.Media(localMedia)
        val inReplyToEventId = (messageComposerContext.composerMode as? MessageComposerMode.Reply)?.eventId
        navigator.navigateToPreviewAttachments(persistentListOf(mediaAttachment), inReplyToEventId)

        // Reset composer since the attachment will be sent in a separate flow
        messageComposerContext.composerMode = MessageComposerMode.Normal
    }

    private suspend fun sendVideoNote(uri: Uri?) {
        uri ?: return
        val inReplyToEventId = (messageComposerContext.composerMode as? MessageComposerMode.Reply)?.eventId

        // Video notes are sent immediately and do not go through the attachment preview/caption flow.
        messageComposerContext.composerMode = MessageComposerMode.Normal
        sendMedia(
            uri = uri,
            mimeType = MimeTypes.Mp4,
            inReplyToEventId = inReplyToEventId,
        )
    }

    private suspend fun sendSticker(
        pack: StickerPackManifest,
        sticker: StickerPackItem,
    ) = runCatchingExceptions {
        val body = buildStickerBody(pack, sticker)
        val thumbnail = sticker.thumbnail
        val content = buildJsonObject {
            put("body", body)
            put("url", sticker.source.url)
            putJsonObject("info") {
                put("mimetype", sticker.source.mimeType)
                sticker.source.width?.let { put("w", it) }
                sticker.source.height?.let { put("h", it) }
                sticker.source.sizeBytes?.let { put("size", it) }
                thumbnail?.url?.let { put("thumbnail_url", it) }
                if (thumbnail != null) {
                    putJsonObject("thumbnail_info") {
                        put("mimetype", thumbnail.mimeType)
                        thumbnail.width?.let { put("w", it) }
                        thumbnail.height?.let { put("h", it) }
                        thumbnail.sizeBytes?.let { put("size", it) }
                    }
                }
            }
        }.toString()
        room.sendRaw(eventType = "m.sticker", content = content).getOrThrow()
        messageComposerContext.composerMode = MessageComposerMode.Normal
        analyticsService.capture(
            Composer(
                inThread = timelineController.mainTimelineMode() is io.element.android.libraries.matrix.api.timeline.Timeline.Mode.Thread,
                isEditing = false,
                isReply = false,
                messageType = Composer.MessageType.Text,
            )
        )
    }.onFailure { cause ->
        Timber.e(cause, "Failed to send sticker")
        if (cause is CancellationException) {
            throw cause
        } else {
            snackbarDispatcher.post(SnackbarMessage(sendAttachmentError(cause)))
        }
    }

    private suspend fun sendMedia(
        uri: Uri,
        mimeType: String,
        inReplyToEventId: EventId?,
    ) = runCatchingExceptions {
        mediaSender.sendMedia(
            uri = uri,
            mimeType = mimeType,
            mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
            inReplyToEventId = inReplyToEventId,
        ).getOrThrow()
    }
        .onFailure { cause ->
            Timber.e(cause, "Failed to send attachment")
            if (cause is CancellationException) {
                throw cause
            } else {
                val snackbarMessage = SnackbarMessage(sendAttachmentError(cause))
                snackbarDispatcher.post(snackbarMessage)
            }
        }

    private fun CoroutineScope.updateDraft(
        draft: ComposerDraft?,
        isVolatile: Boolean,
    ) = launch {
        draftService.updateDraft(
            roomId = room.roomId,
            draft = draft,
            isVolatile = isVolatile,
            // TODO support threads in composer
            threadRoot = null,
        )
    }

    private fun buildStickerBody(
        pack: StickerPackManifest,
        sticker: StickerPackItem,
    ): String {
        return sticker.displayName
            .ifBlank { pack.displayName }
            .lineSequence()
            .firstOrNull()
            ?.trim()
            .orEmpty()
            .ifBlank { "sticker" }
    }

    private suspend fun applyDraft(
        draft: ComposerDraft,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ) {
        val htmlText = draft.htmlText
        val markdownText = draft.plainText
        if (htmlText != null) {
            showTextFormatting = true
            setText(htmlText, markdownTextEditorState, richTextEditorState, requestFocus = true)
        } else {
            showTextFormatting = false
            setText(markdownText, markdownTextEditorState, richTextEditorState, requestFocus = true)
        }
        when (val draftType = draft.draftType) {
            ComposerDraftType.NewMessage -> messageComposerContext.composerMode = MessageComposerMode.Normal
            is ComposerDraftType.Edit -> messageComposerContext.composerMode = MessageComposerMode.Edit(
                eventOrTransactionId = draftType.eventId.toEventOrTransactionId(),
                content = htmlText ?: markdownText
            )
            is ComposerDraftType.Reply -> {
                messageComposerContext.composerMode = MessageComposerMode.Reply(
                    replyToDetails = InReplyToDetails.Loading(draftType.eventId),
                    // I guess it's fine to always render the image when restoring a draft
                    hideImage = false
                )
                timelineController.invokeOnCurrentTimeline {
                    val replyToDetails = loadReplyDetails(draftType.eventId).map(permalinkParser)
                    messageComposerContext.composerMode = MessageComposerMode.Reply(
                        replyToDetails = replyToDetails,
                        // I guess it's fine to always render the image when restoring a draft
                        hideImage = false
                    )
                }
            }
        }
    }

    private fun createDraftFromState(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ): ComposerDraft? {
        val message = currentComposerMessage(markdownTextEditorState, richTextEditorState, withMentions = false)
        val draftType = when (val mode = messageComposerContext.composerMode) {
            is MessageComposerMode.Attachment,
            is MessageComposerMode.Normal -> ComposerDraftType.NewMessage
            is MessageComposerMode.Edit -> {
                mode.eventOrTransactionId.eventId?.let { eventId -> ComposerDraftType.Edit(eventId) }
            }
            is MessageComposerMode.Reply -> ComposerDraftType.Reply(mode.eventId)
            is MessageComposerMode.EditCaption -> {
                // TODO Need a new type to save caption in the SDK
                null
            }
        }
        return if (draftType == null || message.markdown.isBlank()) {
            null
        } else {
            ComposerDraft(
                draftType = draftType,
                htmlText = message.html,
                plainText = message.markdown,
            )
        }
    }

    private fun currentComposerMessage(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        withMentions: Boolean,
    ): Message {
        return if (showTextFormatting) {
            val html = richTextEditorState.messageHtml
            val markdown = richTextEditorState.messageMarkdown
            val mentions = richTextEditorState.mentionsState
                .takeIf { withMentions }
                ?.let { state ->
                    buildList {
                        if (state.hasAtRoomMention) {
                            add(IntentionalMention.Room)
                        }
                        for (userId in state.userIds) {
                            add(IntentionalMention.User(UserId(userId)))
                        }
                    }
                }
                .orEmpty()
            Message(html = html, markdown = markdown, intentionalMentions = mentions)
        } else {
            val markdown = markdownTextEditorState.getMessageMarkdown(permalinkBuilder)
            val mentions = if (withMentions) {
                markdownTextEditorState.getMentions()
            } else {
                emptyList()
            }
            Message(html = null, markdown = markdown, intentionalMentions = mentions)
        }
    }

    private fun CoroutineScope.toggleTextFormatting(
        enabled: Boolean,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState
    ) = launch {
        showTextFormatting = enabled
        if (showTextFormatting) {
            val markdown = markdownTextEditorState.getMessageMarkdown(permalinkBuilder)
            richTextEditorState.setMarkdown(markdown)
            richTextEditorState.requestFocus()
            analyticsService.captureInteraction(Interaction.Name.MobileRoomComposerFormattingEnabled)
        } else {
            val markdown = richTextEditorState.messageMarkdown
            val markdownWithMentions = pillificationHelper.pillify(markdown, false)
            markdownTextEditorState.text.update(markdownWithMentions, true)
            // Give some time for the focus of the previous editor to be cleared
            delay(100)
            markdownTextEditorState.requestFocusAction()
        }
    }

    private fun CoroutineScope.setMode(
        newComposerMode: MessageComposerMode,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ) = launch {
        val currentComposerMode = messageComposerContext.composerMode
        when (newComposerMode) {
            is MessageComposerMode.Edit -> {
                if (currentComposerMode.isEditing.not()) {
                    val draft = createDraftFromState(markdownTextEditorState, richTextEditorState)
                    updateDraft(draft, isVolatile = true).join()
                }
                setText(newComposerMode.content, markdownTextEditorState, richTextEditorState, requestFocus = true)
            }
            is MessageComposerMode.EditCaption -> {
                if (currentComposerMode.isEditing.not()) {
                    val draft = createDraftFromState(markdownTextEditorState, richTextEditorState)
                    updateDraft(draft, isVolatile = true).join()
                }
                setText(newComposerMode.content, markdownTextEditorState, richTextEditorState, requestFocus = true)
            }
            else -> {
                // When coming from edit, just clear the composer as it'd be weird to reset a volatile draft in this scenario.
                if (currentComposerMode.isEditing) {
                    setText("", markdownTextEditorState, richTextEditorState)
                }
            }
        }
        messageComposerContext.composerMode = newComposerMode
    }

    private suspend fun resetComposer(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        fromEdit: Boolean,
    ) {
        // Use the volatile draft only when coming from edit mode otherwise.
        val draft = draftService.loadDraft(
            roomId = room.roomId,
            // TODO support threads in composer
            threadRoot = null,
            isVolatile = true
        ).takeIf { fromEdit }
        if (draft != null) {
            applyDraft(draft, markdownTextEditorState, richTextEditorState)
        } else {
            setText("", markdownTextEditorState, richTextEditorState)
            messageComposerContext.composerMode = MessageComposerMode.Normal
        }
    }

    private suspend fun setText(
        content: String,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        requestFocus: Boolean = false,
    ) {
        if (showTextFormatting) {
            richTextEditorState.setHtml(content)
            if (requestFocus) {
                richTextEditorState.requestFocus()
            }
        } else {
            if (content.isEmpty()) {
                markdownTextEditorState.selection = IntRange.EMPTY
            }
            val pillifiedContent = pillificationHelper.pillify(content, false)
            markdownTextEditorState.text.update(pillifiedContent, true)
            if (requestFocus) {
                markdownTextEditorState.requestFocusAction()
            }
        }
    }
}
