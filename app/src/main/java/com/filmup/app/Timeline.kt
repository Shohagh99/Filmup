package com.filmup.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
}

@Composable
fun Timeline(vm: EditorViewModel, scroll: ScrollState, pxPerMs: Float, modifier: Modifier) {
    val density = LocalDensity.current
    val p = vm.project
    fun w(ms: Long): Dp = with(density) { (ms * pxPerMs).toDp() }

    BoxWithConstraints(modifier.background(Color(0xFF1B1B1B))) {
        val half = maxWidth / 2
        Column(Modifier.horizontalScroll(scroll).padding(vertical = 8.dp)) {
            // Text (caption) track
            Row {
                Spacer(Modifier.width(half))
                p.clips.forEach { c ->
                    Box(Modifier.width(w(c.lengthMs)).height(24.dp).padding(horizontal = 1.dp)) {
                        if (c.caption.isNotBlank()) {
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color(0xFFD9822B))) {
                                Text(c.caption, fontSize = 11.sp, maxLines = 1, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp).align(Alignment.CenterStart))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(half))
            }
            // Main video track
            Row(Modifier.padding(vertical = 4.dp)) {
                Spacer(Modifier.width(half))
                p.clips.forEach { c ->
                    ClipView(
                        clip = c, selected = c.id == vm.selectedId, width = w(c.lengthMs),
                        onSelect = { vm.select(c.id) },
                        onTrimBegin = { vm.beginTrim() },
                        onTrimStart = { dx -> vm.trim(c.id, (dx / pxPerMs).toLong(), 0) },
                        onTrimEnd = { dx -> vm.trim(c.id, 0, (dx / pxPerMs).toLong()) }
                    )
                }
                Spacer(Modifier.width(half))
            }
            // Audio track
            Row {
                Spacer(Modifier.width(half))
                if (p.musicUri != null) {
                    Box(Modifier.width(w(Editing.total(p))).height(28.dp).padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color(0xFF1F8A8A))) {
                        Text("♪ Music", fontSize = 11.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp).align(Alignment.CenterStart))
                    }
                }
                Spacer(Modifier.width(half))
            }
        }
        // Fixed playhead
        Box(Modifier.align(Alignment.Center).fillMaxHeight().width(2.dp).background(Color.White))
    }
}

@Composable
private fun ClipView(
    clip: Clip, selected: Boolean, width: Dp,
    onSelect: () -> Unit, onTrimBegin: () -> Unit,
    onTrimStart: (Float) -> Unit, onTrimEnd: (Float) -> Unit
) {
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(null, clip.uri) {
        value = withContext(Dispatchers.IO) { Media.thumb(ctx, Uri.parse(clip.uri)) }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.width(width).height(64.dp).clip(shape)
            .background(Color(0xFF333333))
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable { onSelect() }
    ) {
        thumb?.let { bmp ->
            val n = (width.value / 64f).toInt() + 1
            Row(Modifier.fillMaxSize()) {
                repeat(n) {
                    Image(bmp.asImageBitmap(), null, Modifier.width(64.dp).fillMaxHeight(), contentScale = ContentScale.Crop)
                }
            }
        }
        Text(fmt(clip.lengthMs), fontSize = 10.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                .background(Color(0x88000000), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp))
        if (selected) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(16.dp).background(Color.White)
                .pointerInput(clip.id) {
                    detectHorizontalDragGestures(onDragStart = { onTrimBegin() }) { change, dx -> change.consume(); onTrimStart(dx) }
                })
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(16.dp).background(Color.White)
                .pointerInput(clip.id) {
                    detectHorizontalDragGestures(onDragStart = { onTrimBegin() }) { change, dx -> change.consume(); onTrimEnd(dx) }
                })
        }
    }
}
