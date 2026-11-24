package dev.axkit.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The second review: outbox off the lock, card answers kept, timeline caps and extras, read line, work origin. */
class SecondReviewTest {
    private val at = Instant.parse("2026-11-23T03:00:00Z")
    private fun msg(seq: Int, room: String? = null, role: String = "assistant") =
        AppEvent.Message(MessageEvent(seq = seq, at = at, role = role, text = "m$seq", channel = room))

    @Test fun aNewBubbleDoesNotWaitForAHangingSend() = runBlocking {
        val box = Outbox(null)
        box.enqueue("first", "")
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val flushing = async { box.flush { started.complete(Unit); release.await(); SendResult(5) } }
        started.await()
        // While the first send hangs, typing another message and reading the list still answer at once.
        withTimeout(1000) { box.enqueue("second", "") }
        assertEquals(listOf("first", "second"), withTimeout(1000) { box.pending().map { it.text } })
        release.complete(Unit)
        val r = flushing.await()
        assertEquals(2, r.sent.size) // the second one went in the same run
    }

    @Test fun cardAnswersAndQuickAnswersCarryTheirLinks() = runBlocking {
        val box = Outbox(null)
        val body = JsonObject(mapOf("decision" to JsonPrimitive("approve")))
        box.enqueueCard("c1", body, "dev")
        box.enqueue("네", "", choice = "yes", forSeq = 42)
        val seen = mutableListOf<PendingMessage>()
        val r = box.flush { seen.add(it); if (it.isCardAnswer) throw ApiError.BadRequest("option") else SendResult(9) }
        assertEquals("c1", seen[0].cardId)
        assertEquals(body, seen[0].cardBody)
        assertEquals(1, r.failed.size)
        assertEquals(42, box.pending().first { it.choice == "yes" }.forSeq)
        box.dropSent()
        assertEquals(listOf("c1"), box.pending().map { it.cardId })
    }

    @Test fun queuedCardAnswerShowsThenIsReplacedOrReopened() {
        val t = Timeline()
        t.apply(AppEvent.Card(CardEvent(seq = 1, at = at, cardId = "c1", summary = "q")))
        t.closeLocally("c1", "queued")
        assertFalse(t.isOpen("c1"))
        assertEquals("맥에 닿으면 답을 보낼게요", CardWords.closedLabel(t.closure("c1"), false))
        t.closeLocally("c1", "approved")
        assertEquals("approved", t.closure("c1")?.state)
        t.closeLocally("c1", "denied") // a real answer is not replaced
        assertEquals("approved", t.closure("c1")?.state)
        val u = Timeline()
        u.apply(AppEvent.Card(CardEvent(seq = 1, at = at, cardId = "c2", summary = "q")))
        u.closeLocally("c2", "queued"); u.reopenLocally("c2")
        assertTrue(u.isOpen("c2"))
    }

    @Test fun snapshotSkipsHiddenRoomsKeepsExtrasAndCapsSeen() {
        val t = Timeline()
        t.apply(listOf(msg(1), msg(2, "로그"), msg(3, "dev")))
        val snap = t.snapshot(skipRoom = { it == "로그" }).copy(chosen = mapOf("1" to "yes"), myReactions = mapOf("3" to "👍"))
        val file = java.io.File.createTempFile("timeline", ".json")
        try {
            TimelineStore(file).save(listOf(snap))
            val back = TimelineStore(file).loadSnapshot()!!
            assertEquals(mapOf("1" to "yes"), back.chosen)
            assertEquals(mapOf("3" to "👍"), back.myReactions)
            val restored = Timeline.restore(back)
            assertTrue(restored.items("로그").isEmpty())
            assertEquals(1, restored.items("dev").size)
            assertEquals(3, restored.lastSeq)
        } finally { file.delete() }
        // Too big for the limit: the smaller copy is written.
        val big = Timeline()
        big.apply((1..400).map { AppEvent.Message(MessageEvent(seq = it, at = at, text = "가".repeat(6000))) })
        val f2 = java.io.File.createTempFile("timeline", ".json")
        try {
            TimelineStore(f2).save(listOf(150, 60, 20).map { big.snapshot(it) })
            assertEquals(60, Timeline.restore(TimelineStore(f2).loadSnapshot()!!).items("").size)
        } finally { f2.delete() }
        // `seen` does not grow forever, and the cursor still refuses old seqs near it.
        val long = Timeline()
        long.apply((1..7000).map { msg(it) })
        assertFalse(long.apply(msg(6999)))
    }

    @Test fun readLineGoesBeforeTheFirstNewLineFromSomeoneElse() {
        val speaker = RoomFeed.Speaker("b", "Leo", null)
        fun entry(seq: Int, owner: Boolean = false) = RoomFeed.Row.EntryRow(
            RoomFeed.Entry(seq = seq, at = at, speaker = if (owner) RoomFeed.Speaker.owner else speaker, text = "t$seq", source = Timeline.Item.Message(msg(seq).e)),
        )
        val rows = listOf(entry(1), entry(2), entry(3, owner = true), entry(4), entry(5))
        assertEquals(3, RoomFeed.readLineIndex(rows, 2))
        assertNull(RoomFeed.readLineIndex(rows, 5)) // nothing new
        assertNull(RoomFeed.readLineIndex(rows, 0)) // never read: no line above to mark
    }

    @Test fun workOriginAndOvernightNotice() {
        val v = ProtocolJson.decodeFromString(WorkView.serializer(), """{"active":[{"id":"a","status":"running","origin":{"channelId":"channel.app","address":"owner"}}]}""")
        assertEquals("이 앱 대화에서 시킨 일 ›", WorkWords.origin(v.active[0]))
        assertNull(WorkWords.origin(WorkView.Item(id = "b")))
        val n = (EventDecoding.event("""{"type":"notice","seq":1,"at":"2026-11-23T03:00:00Z","text":"밤사이 3건","kind":"overnight"}""") as AppEvent.Notice).e
        assertFalse(n.notifies) // plain channel, still notified as a notice
        assertFalse(n.isUrgent)
    }
}
