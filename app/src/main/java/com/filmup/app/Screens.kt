@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.filmup.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private fun mediaItems(clips: List<Clip>) = clips.map {
    MediaItem.Builder().setUri(it.uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(it.inMs).setEndPositionMs(it.outMs).build()
        ).build()
}

private fun seekGlobal(player: Player, clips: List<Clip>, ms: Long) {
    if (clips.isEmpty()) return
    var rem = ms.coerceAtLeast(0)
    for ((i, c) in clips.withIndex()) {
        if (rem < c.lengthMs || i == clips.lastIndex) {
            player.seekTo(i, rem.coerceAtMost(c.lengthMs)); return
        }
        rem -= c.lengthMs
    }
}

private fun globalPos(player: Player, clips: List<Clip>): Long {
    val i = player.currentMediaItemIndex
    if (i !in clips.indices) return 0
    return clips.take(i).sumOf { it.lengthMs } + player.currentPosition.coerceAtLeast(0)
}

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val pxPerMs = with(density) { 80.dp.toPx() } / 1000f
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    val scroll = rememberScrollState()
    val clips = vm.project.clips
    val total = Editing.total(vm.project)
    var dialog by remember { mutableStateOf<String?>(null) }

    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addVideos(it) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.setMusic(u) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    val sig = clips.map { Triple(it.uri, it.inMs, it.outMs) }
    LaunchedEffect(sig) {
        player.pause()
        player.setMediaItems(mediaItems(clips), false)
        player.prepare()
        val t = posMs.coerceAtMost(total)
        posMs = t
        seekGlobal(player, clips, t)
        scroll.scrollTo((t * pxPerMs).toInt())
    }
    LaunchedEffect(vm.project.filter) { player.setVideoEffects(FilterFx.effects(vm.project.filter)) }
    LaunchedEffect(vm.project.muteOriginal) { player.volume = if (vm.project.muteOriginal) 0f else 1f }
    LaunchedEffect(playing) {
        while (playing) {
            posMs = globalPos(player, vm.project.clips)
            scroll.scrollTo((posMs * pxPerMs).toInt())
            delay(33)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.value }.collect { v ->
            if (scroll.isScrollInProgress && !playing) {
                posMs = (v / pxPerMs).toLong()
                seekGlobal(player, vm.project.clips, posMs)
            }
        }
    }
    LaunchedEffect(vm.message) {
        if (vm.message != null) { delay(2500); vm.message = null }
    }

    fun togglePlay() {
        if (clips.isEmpty()) return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED || posMs >= total) seekGlobal(player, clips, 0)
            player.play()
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).statusBarsPadding().navigationBarsPadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Filmup", fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
            Button(onClick = { dialog = "export" }, enabled = clips.isNotEmpty()) { Text("Export") }
        }
        // Preview
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = player; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
            if (clips.isEmpty()) Text("Tap “Add” to import videos", color = Color.Gray)
            vm.message?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    .background(Color(0xCC000000)).padding(8.dp))
            }
        }
        // Playback row
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { togglePlay() }) { Text(if (playing) "⏸" else "▶", fontSize = 22.sp, color = Color.White) }
            Text("${fmt(posMs)} / ${fmt(total)}", color = Color.LightGray, fontSize = 13.sp)
        }
        Timeline(vm, scroll, pxPerMs, Modifier.fillMaxWidth().height(170.dp))
        // Toolbar
        val sel = vm.selected()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            Tool("＋", "Add") { pickVideos.launch(arrayOf("video/*")) }
            Tool("✂", "Split", clips.isNotEmpty()) { vm.split(posMs) }
            Tool("🗑", "Delete", sel != null) { vm.deleteSelected() }
            Tool("⧉", "Duplicate", sel != null) { vm.duplicateSelected() }
            Tool("◀", "Move L", sel != null) { vm.moveSelected(-1) }
            Tool("▶", "Move R", sel != null) { vm.moveSelected(1) }
            Tool("T", "Text", sel != null) { dialog = "text" }
            Tool("♪", "Music") { if (vm.project.musicUri == null) pickAudio.launch(arrayOf("audio/*")) else dialog = "music" }
            Tool("✨", "Filter", clips.isNotEmpty()) { dialog = "filter" }
            Tool(if (vm.project.muteOriginal) "🔇" else "🔊", "Orig. audio", clips.isNotEmpty()) { vm.toggleMute() }
            Tool("↶", "Undo", vm.undoCount > 0) { vm.undo() }
            Tool("↷", "Redo", vm.redoCount > 0) { vm.redo() }
        }
    }

    when (dialog) {
        "text" -> {
            var txt by remember { mutableStateOf(vm.selected()?.caption ?: "") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Caption (বাংলা / English)") },
                text = { OutlinedTextField(txt, { txt = it }) },
                confirmButton = { TextButton({ vm.setCaption(txt); dialog = null }) { Text("OK") } },
                dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } }
            )
        }
        "filter" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Filter") },
            text = {
                Column {
                    FilterFx.names.forEach { n ->
                        Row(Modifier.fillMaxWidth().clickable { vm.setFilter(n); dialog = null }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(vm.project.filter == n, { vm.setFilter(n); dialog = null })
                            Text(n)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("Close") } }
        )
        "music" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Music") },
            text = {
                Column {
                    TextButton({ pickAudio.launch(arrayOf("audio/*")); dialog = null }) { Text("Replace music") }
                    TextButton({ vm.removeMusic(); dialog = null }) { Text("Remove music") }
                }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("Close") } }
        )
        "export" -> ExportDialog(vm, total) { vm.closeExport(); dialog = null }
    }
}

@Composable
private fun Tool(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp).alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 20.sp, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.LightGray)
    }
}

@Composable
private fun ExportDialog(vm: EditorViewModel, totalMs: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var res by remember { mutableIntStateOf(720) }
    val mbps = when (res) { 480 -> 2.5f; 720 -> 5f; else -> 10f }
    val estMb = mbps * (totalMs / 1000f) / 8f
    val st = vm.exportState
    AlertDialog(
        onDismissRequest = { if (st !is ExportState.Running) onClose() },
        title = { Text("Export MP4") },
        text = {
            Column {
                when (st) {
                    is ExportState.Idle -> {
                        listOf(480, 720, 1080).forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { res = r }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(res == r, { res = r }); Text("${r}p")
                            }
                        }
                        Text("H.264 + AAC · ${mbps} Mbps · ≈ ${"%.1f".format(estMb)} MB", fontSize = 12.sp)
                    }
                    is ExportState.Running -> {
                        Text("Exporting… ${st.progress}%")
                        LinearProgressIndicator(progress = { st.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    is ExportState.Done -> Text("Saved to Movies/Filmup")
                    is ExportState.Failed -> Text("Export failed: ${st.msg}")
                }
            }
        },
        confirmButton = {
            when (st) {
                is ExportState.Idle -> TextButton({ vm.startExport(res, (mbps * 1_000_000).toInt()) }) { Text("Export") }
                is ExportState.Done -> TextButton({
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(st.uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }) { Text("Open") }
                else -> {}
            }
        },
        dismissButton = {
            when (st) {
                is ExportState.Running -> TextButton({ vm.cancelExport() }) { Text("Cancel export") }
                else -> TextButton(onClose) { Text("Close") }
            }
        }
    )
}
