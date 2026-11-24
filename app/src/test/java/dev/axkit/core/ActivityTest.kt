package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §4 `activity`: outside the log, never an AppEvent, words for the chat line. */
class ActivityTest {
    @Test fun parsesActivityAndKeepsItOutOfTheLog() {
        val raw = """{"type":"activity","channel":"개발","state":"working","step":"메일 보는 중","at":"2026-11-20T03:10:00Z"}"""
        val a = Activity.parse(raw)!!
        assertEquals("개발", a.room)
        assertEquals("메일 보는 중…", a.words)
        // No seq: it never becomes a timeline event, so the stream cursor (Last-Event-ID) does not move.
        assertNull(EventDecoding.event(raw))
    }

    @Test fun mainChatThinkingAndIdle() {
        val thinking = Activity.parse("""{"type":"activity","state":"thinking","at":"2026-11-20T03:10:00Z"}""")!!
        assertEquals("", thinking.room)
        assertEquals("생각하는 중…", thinking.words)
        assertTrue(Activity.parse("""{"type":"activity","state":"idle"}""")!!.isIdle)
        assertNull(Activity.parse("""{"type":"message","seq":3,"at":"2026-11-20T03:10:00Z","text":"x"}"""))
    }
}
