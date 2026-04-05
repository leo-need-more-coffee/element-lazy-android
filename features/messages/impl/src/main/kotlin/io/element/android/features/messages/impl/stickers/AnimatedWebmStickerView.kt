/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.di.LocalMatrixMediaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.element.android.libraries.matrix.api.media.MediaSource
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap

/**
 * Displays an animated WebM sticker by downloading the file via [MatrixMediaLoader] and playing
 * it with ExoPlayer in a silent, looping loop.
 */
@Composable
fun AnimatedWebmStickerView(
    mediaSource: MediaSource,
    mimeType: String,
    filename: String,
    playWhenReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaLoader = LocalMatrixMediaLoader.current
    val sourceUrl = mediaSource.safeUrl
    var localFile by remember(sourceUrl) { mutableStateOf<File?>(null) }
    var posterBitmap by remember(sourceUrl) { mutableStateOf(memoryCachedPoster(sourceUrl)) }
    var playbackGranted by remember(sourceUrl, playWhenReady) { mutableStateOf(false) }

    LaunchedEffect(sourceUrl) {
        val cacheFile = File(context.cacheDir, "sticker_${sourceUrl.hashCode()}.webm")
        memoryCachedFile(sourceUrl)?.takeIf(File::exists)?.let {
            localFile = it
            return@LaunchedEffect
        }
        if (cacheFile.exists()) {
            rememberMemoryCachedFile(sourceUrl, cacheFile)
            localFile = cacheFile
            return@LaunchedEffect
        }
        mediaLoader.downloadMediaFile(mediaSource, mimeType, filename)
            .onSuccess { mediaFile ->
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    if (mediaFile.persist(cacheFile.path)) {
                        rememberMemoryCachedFile(sourceUrl, cacheFile)
                        localFile = cacheFile
                    } else {
                        mediaFile.close()
                    }
                }.onFailure { e ->
                    Timber.w(e, "Failed to cache WebM sticker")
                    mediaFile.close()
                }
            }
            .onFailure { Timber.w(it, "Failed to download WebM sticker") }
    }

    LaunchedEffect(sourceUrl, localFile?.path) {
        val file = localFile ?: return@LaunchedEffect
        if (posterBitmap != null) return@LaunchedEffect
        posterBitmap = withContext(Dispatchers.IO) {
            extractPosterBitmap(file)?.also { rememberMemoryCachedPoster(sourceUrl, it) }
        }
    }

    LaunchedEffect(sourceUrl, localFile?.path, playWhenReady) {
        playbackGranted = false
        if (!playWhenReady || localFile == null) {
            releasePlaybackSlot(sourceUrl)
            return@LaunchedEffect
        }
        while (!playbackGranted) {
            playbackGranted = tryAcquirePlaybackSlot(sourceUrl)
            if (!playbackGranted) {
                delay(WEBM_PLAYBACK_RETRY_MS)
            }
        }
    }

    DisposableEffect(sourceUrl) {
        onDispose {
            releasePlaybackSlot(sourceUrl)
        }
    }

    val file = localFile
    if (file != null && playWhenReady && playbackGranted) {
        Box(modifier = modifier) {
            WebmLoopingPlayer(file = file, modifier = Modifier.fillMaxSize())
        }
    } else if (posterBitmap != null) {
        Image(
            bitmap = posterBitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(ElementTheme.colors.bgSubtleSecondary))
    }
}

private const val WEBM_MEMORY_CACHE_LIMIT = 48
private const val WEBM_POSTER_MEMORY_CACHE_LIMIT = 64
private const val WEBM_MAX_CONCURRENT_PLAYBACK = 3
private const val WEBM_PLAYBACK_RETRY_MS = 250L

private val webmFileMemoryCache = object : LinkedHashMap<String, File>(WEBM_MEMORY_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, File>?): Boolean {
        return size > WEBM_MEMORY_CACHE_LIMIT
    }
}

private val webmPosterMemoryCache = object : LinkedHashMap<String, Bitmap>(WEBM_POSTER_MEMORY_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
        return size > WEBM_POSTER_MEMORY_CACHE_LIMIT
    }
}

private val activePlaybackSlots = LinkedHashSet<String>()

@Synchronized
private fun memoryCachedFile(sourceUrl: String): File? = webmFileMemoryCache[sourceUrl]

@Synchronized
private fun rememberMemoryCachedFile(sourceUrl: String, file: File) {
    webmFileMemoryCache[sourceUrl] = file
}

@Synchronized
private fun memoryCachedPoster(sourceUrl: String): Bitmap? = webmPosterMemoryCache[sourceUrl]

@Synchronized
private fun rememberMemoryCachedPoster(sourceUrl: String, bitmap: Bitmap) {
    webmPosterMemoryCache[sourceUrl] = bitmap
}

@Synchronized
private fun tryAcquirePlaybackSlot(sourceUrl: String): Boolean {
    if (activePlaybackSlots.contains(sourceUrl)) return true
    if (activePlaybackSlots.size >= WEBM_MAX_CONCURRENT_PLAYBACK) return false
    activePlaybackSlots.add(sourceUrl)
    return true
}

@Synchronized
private fun releasePlaybackSlot(sourceUrl: String) {
    activePlaybackSlots.remove(sourceUrl)
}

private fun extractPosterBitmap(file: File): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        retriever.release()
    }
}.getOrNull()

@Composable
private fun WebmLoopingPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                val textureView = TextureView(ctx)
                addView(textureView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                exoPlayer.setVideoTextureView(textureView)
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
