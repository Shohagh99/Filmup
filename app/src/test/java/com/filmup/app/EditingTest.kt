package com.filmup.app

import org.junit.Assert.*
import org.junit.Test

class EditingTest {
    private fun clip(id: String, len: Long = 10_000) = Clip(id, "content://x/$id", len, 0, len, 1920, 1080)

    @Test fun splitDividesClipAtPlayhead() {
        val p = Project(listOf(clip("a")))
        val r = Editing.split(p, 4_000) { "b" }!!
        assertEquals(2, r.clips.size)
        assertEquals(4_000L, r.clips[0].outMs)
        assertEquals(4_000L, r.clips[1].inMs)
        assertEquals(10_000L, Editing.total(r))
    }

    @Test fun splitNearEdgeRejected() {
        assertNull(Editing.split(Project(listOf(clip("a"))), 50))
    }

    @Test fun trimClampsToSource() {
        val c = Editing.trim(clip("a"), -500, 500)
        assertEquals(0L, c.inMs); assertEquals(10_000L, c.outMs)
        val d = Editing.trim(clip("a"), 3_000, -3_000)
        assertEquals(4_000L, d.lengthMs)
    }

    @Test fun reorderAndDuplicate() {
        val p = Project(listOf(clip("a"), clip("b")))
        assertEquals(listOf("b", "a"), Editing.move(p, "a", 1).clips.map { it.id })
        assertEquals(3, Editing.duplicate(p, "a") { "c" }.clips.size)
    }
}
