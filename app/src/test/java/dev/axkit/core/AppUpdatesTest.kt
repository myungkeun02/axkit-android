package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** §32 앱 업데이트: what counts as newer, when to ask the Mac, the sha256 check. */
class AppUpdatesTest {
    private val sha = "a".repeat(64)
    private val raw = """{"android":{"build":52,"versionName":"1.0.52","builtAt":"2026-11-24T03:00:00.000Z","sha256":"$sha","size":23400000},
        "desktop":{"build":9,"sha256":"$sha"},"future":1}"""
    private val view = ProtocolJson.decodeFromString(AppUpdatesView.serializer(), raw)

    @Test fun newerOnly() {
        assertEquals(52, AppUpdates.offer(view, 51)?.build)
        assertNull(AppUpdates.offer(view, 52))
        assertNull(AppUpdates.offer(view, 60))
        assertNull(AppUpdates.offer(AppUpdatesView(), 1)) // this Mac has no Android build
        assertNull(AppUpdates.offer(null, 1))
        assertNull(AppUpdates.offer(AppUpdatesView(android = AppUpdatesView.Build(99, sha256 = "nope")), 1)) // can't be checked: not offered
    }

    @Test fun whenToAsk() {
        val now = 10 * AppUpdates.DAY_MS
        assertTrue(AppUpdates.due(now - 1000, now, startedNow = true))
        assertFalse(AppUpdates.due(now - 1000, now, startedNow = false))
        assertTrue(AppUpdates.due(now - AppUpdates.DAY_MS, now, startedNow = false))
        assertTrue(AppUpdates.due(now + 5000, now, startedNow = false)) // the clock went back
    }

    @Test fun sha256OfAFile() {
        val f = File.createTempFile("apk", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdates.sha256(f))
        assertTrue(AppUpdates.matches(f, "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD "))
        assertFalse(AppUpdates.matches(f, sha))
    }

    @Test fun words() {
        assertEquals("새 버전이 있어요 · 1.0.52 (52)", AppUpdates.title(view.android!!))
        assertEquals("23MB", AppUpdates.size(23_400_000))
        assertEquals("900KB", AppUpdates.size(899_600))
        assertNull(AppUpdates.size(0))
    }
}
