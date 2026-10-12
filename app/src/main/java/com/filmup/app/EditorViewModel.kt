package com.filmup.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Int) : ExportState
    data class Done(val uri: Uri) : ExportState
    data class Failed(val msg: String) : ExportState
}

class EditorViewModel(private val app: Application) : AndroidViewModel(app) {
    var project by mutableStateOf(Project()); private set
    var selectedId by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    var exportState by mutableStateOf<ExportState>(ExportState.Idle); private set
    var undoCount by mutableIntStateOf(0); private set
    var redoCount by mutableIntStateOf(0); private set

    private val undoStack = ArrayList<Project>()
    private val redoStack = ArrayList<Project>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(app.filesDir, "project.json")
    private var saveJob: Job? = null
    private var exportJob: Job? = null

    init {
        // Autosave recovery: reopen the last project.
        try {
            if (file.exists()) project = json.decodeFromString<Project>(file.readText())
        } catch (_: Exception) {}
    }

    fun selected(): Clip? = project.clips.firstOrNull { it.id == selectedId }

    private fun sync() { undoCount = undoStack.size; redoCount = redoStack.size }

    private fun save() {
        val snap = project
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            try { file.writeText(json.encodeToString(snap)) } catch (_: Exception) {}
        }
    }

    private fun push() {
        undoStack.add(project)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear(); sync()
    }

    private fun change(p: Project) { push(); project = p; save() }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(project); project = undoStack.removeAt(undoStack.lastIndex)
        fixSelection(); sync(); save()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(project); project = redoStack.removeAt(redoStack.lastIndex)
        fixSelection(); sync(); save()
    }

    private fun fixSelection() { if (project.clips.none { it.id == selectedId }) selectedId = null }

    fun select(id: String) { selectedId = if (selectedId == id) null else id }

    fun addVideos(uris: List<Uri>) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    try { app.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    Media.probe(app, u)
                }
            }
            if (added.size < uris.size) message = "Some files could not be read as video"
            if (added.isNotEmpty()) change(project.copy(clips = project.clips + added))
        }
    }

    fun setMusic(uri: Uri) {
        try { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        change(project.copy(musicUri = uri.toString()))
    }

    fun removeMusic() = change(project.copy(musicUri = null))

    fun split(t: Long) {
        val p = Editing.split(project, t)
        if (p == null) message = "Move the playhead inside a clip to split"
        else change(p)
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        change(project.copy(clips = project.clips.filter { it.id != id })); selectedId = null
    }

    fun duplicateSelected() { selectedId?.let { change(Editing.duplicate(project, it)) } }
    fun moveSelected(dir: Int) { selectedId?.let { change(Editing.move(project, it, dir)) } }

    fun setCaption(text: String) {
        val id = selectedId ?: return
        change(project.copy(clips = project.clips.map { if (it.id == id) it.copy(caption = text.trim()) else it }))
    }

    fun setFilter(name: String) = change(project.copy(filter = name))
    fun toggleMute() = change(project.copy(muteOriginal = !project.muteOriginal))

    fun beginTrim() = push()

    fun trim(id: String, startDelta: Long, endDelta: Long) {
        project = project.copy(clips = project.clips.map { if (it.id == id) Editing.trim(it, startDelta, endDelta) else it })
        save()
    }

    fun startExport(height: Int, bitrate: Int) {
        if (project.clips.isEmpty()) { exportState = ExportState.Failed("Nothing to export"); return }
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            exportState = ExportState.Running(0)
            try {
                val uri = Exporter.export(app, project, height, bitrate) { exportState = ExportState.Running(it) }
                exportState = ExportState.Done(uri)
            } catch (e: CancellationException) {
                exportState = ExportState.Idle; throw e
            } catch (e: Exception) {
                exportState = ExportState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun cancelExport() { exportJob?.cancel(); exportState = ExportState.Idle }
    fun closeExport() { exportState = ExportState.Idle }
}
