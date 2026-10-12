@file:OptIn(UnstableApi::class)

package com.filmup.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FilterFx {
    val names = listOf("None", "B&W", "Sepia", "Contrast", "Bright", "Vivid")

    fun effects(name: String): List<Effect> = when (name) {
        "B&W" -> listOf<Effect>(RgbMatrix { _, _ ->
            floatArrayOf(
                .299f, .299f, .299f, 0f,
                .587f, .587f, .587f, 0f,
                .114f, .114f, .114f, 0f,
                0f, 0f, 0f, 1f
            )
        })
        "Sepia" -> listOf<Effect>(RgbMatrix { _, _ ->
            floatArrayOf(
                .393f, .349f, .272f, 0f,
                .769f, .686f, .534f, 0f,
                .189f, .168f, .131f, 0f,
                0f, 0f, 0f, 1f
            )
        })
        "Contrast" -> listOf<Effect>(Contrast(0.3f))
        "Bright" -> listOf<Effect>(Brightness(0.15f))
        "Vivid" -> listOf<Effect>(HslAdjustment.Builder().adjustSaturation(40f).build())
        else -> emptyList()
    }
}

object Exporter {
    private fun captionOverlay(text: String): Effect {
        val s = SpannableString(text)
        s.setSpan(ForegroundColorSpan(Color.WHITE), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(BackgroundColorSpan(0x99000000.toInt()), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(AbsoluteSizeSpan(64), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.8f).build()
        return OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(s, settings)))
    }

    /** Renders the project to MP4 and saves it in Movies/Filmup. Throws on failure. */
    suspend fun export(ctx: Context, p: Project, height: Int, bitrate: Int, onProgress: (Int) -> Unit): Uri {
        require(p.clips.isNotEmpty()) { "No clips to export" }
        val cache = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        withContext(Dispatchers.Main) {
            val first = p.clips.first()
            val long = (height * 16 / 9) / 2 * 2
            val (w, h) = if (first.height > first.width) height to long else long to height
            val items = p.clips.map { c ->
                val fx = mutableListOf<Effect>(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
                fx += FilterFx.effects(p.filter)
                if (c.caption.isNotBlank()) fx += captionOverlay(c.caption)
                val mi = MediaItem.Builder().setUri(c.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(c.inMs).setEndPositionMs(c.outMs).build()
                    ).build()
                EditedMediaItem.Builder(mi)
                    .setRemoveAudio(p.muteOriginal)
                    .setEffects(Effects(emptyList(), fx))
                    .build()
            }
            val sequences = mutableListOf(EditedMediaItemSequence(items))
            p.musicUri?.let { m ->
                val total = Editing.total(p)
                val mi = MediaItem.Builder().setUri(m)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(total).build())
                    .build()
                sequences += EditedMediaItemSequence(listOf(EditedMediaItem.Builder(mi).setRemoveVideo(true).build()))
            }
            val composition = Composition.Builder(*sequences.toTypedArray()).build()
            val encoder = DefaultEncoderFactory.Builder(ctx)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                .build()

            coroutineScope {
                suspendCancellableCoroutine<Unit> { cont ->
                    var poll: Job? = null
                    val t = Transformer.Builder(ctx)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoder)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                poll?.cancel(); if (cont.isActive) cont.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                poll?.cancel(); if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        }).build()
                    val holder = ProgressHolder()
                    poll = launch {
                        while (true) {
                            if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                            delay(250)
                        }
                    }
                    cont.invokeOnCancellation { poll?.cancel(); t.cancel() }
                    t.start(composition, cache.absolutePath)
                }
            }
        }
        return withContext(Dispatchers.IO) {
            if (!cache.exists() || cache.length() == 0L) throw IllegalStateException("Export produced no file")
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "Filmup_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Filmup")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create output file")
            resolver.openOutputStream(uri)!!.use { o -> cache.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            cache.delete()
            uri
        }
    }
}
