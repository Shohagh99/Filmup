package com.filmup.app

import java.util.UUID

/** Pure, testable editing operations on the non-destructive project model. */
object Editing {
    const val MIN = 200L

    fun total(p: Project): Long = p.clips.sumOf { it.lengthMs }

    fun split(p: Project, t: Long, newId: () -> String = { UUID.randomUUID().toString() }): Project? {
        var start = 0L
        val out = mutableListOf<Clip>()
        var did = false
        for (c in p.clips) {
            val end = start + c.lengthMs
            if (!did && t > start + MIN && t < end - MIN) {
                val src = c.inMs + (t - start)
                out += c.copy(outMs = src)
                out += c.copy(id = newId(), inMs = src, caption = "")
                did = true
            } else out += c
            start = end
        }
        return if (did) p.copy(clips = out) else null
    }

    fun trim(c: Clip, startDelta: Long, endDelta: Long): Clip {
        val ni = (c.inMs + startDelta).coerceIn(0L, c.outMs - MIN)
        val no = (c.outMs + endDelta).coerceIn(c.inMs + MIN, c.durationMs)
        return c.copy(inMs = ni, outMs = no)
    }

    fun move(p: Project, id: String, dir: Int): Project {
        val i = p.clips.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in p.clips.indices) return p
        val l = p.clips.toMutableList()
        val tmp = l[i]; l[i] = l[j]; l[j] = tmp
        return p.copy(clips = l)
    }

    fun duplicate(p: Project, id: String, newId: () -> String = { UUID.randomUUID().toString() }): Project {
        val i = p.clips.indexOfFirst { it.id == id }
        if (i < 0) return p
        val l = p.clips.toMutableList()
        l.add(i + 1, l[i].copy(id = newId()))
        return p.copy(clips = l)
    }
}
