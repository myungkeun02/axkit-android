package dev.axkit.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Port of RoomFeedTests in RoomFeedTests.swift. */
class RoomFeedTest {
    private val zone = Format.seoul
    private val now: Instant = Instant.parse("2026-11-20T03:00:00Z")
    private val leo = RoomFeed.Speaker("engineering", "Leo")

    private fun at(iso: String): Instant = Instant.parse(iso)
    private fun notice(seq: Int, text: String, iso: String = "2026-11-20T01:00:00Z"): Timeline.Item =
        Timeline.Item.Notice(NoticeEvent(seq = seq, at = at(iso), text = text, channel = "개발"))

    private fun message(seq: Int, text: String, author: String? = null, to: String? = null, owner: Boolean = false, iso: String = "2026-11-20T01:00:00Z"): Timeline.Item =
        Timeline.Item.Message(MessageEvent(
            seq = seq, at = at(iso), role = if (owner) "owner" else "assistant", text = text, channel = "개발",
            author = author?.let { Author(it.lowercase(), it) }, to = to?.let { Author(it.lowercase(), it) },
        ))

    private fun build(items: List<Timeline.Item>, room: String = "개발", lead: RoomFeed.Speaker? = leo) =
        RoomFeed.build(items, room = room, lead = lead, assistant = "비서", now = now, zone = zone)

    private fun entries(rows: List<RoomFeed.Row>) = rows.filterIsInstance<RoomFeed.Row.EntryRow>().map { it.entry }

    @Test fun repliesFoldUnderTheQuestion() {
        val rows = build(listOf(
            notice(1, "💬 Leo → Mia · 검토 요청: 바꿔도 될까요?"),
            notice(2, "↩️ Mia → Leo · 답 (승인): 좋아요."),
        ))
        val list = entries(rows)
        assertEquals(1, list.size)
        assertEquals("Leo", list[0].speaker.name)
        assertEquals("Mia", list[0].to)
        assertEquals(listOf("Mia"), list[0].replies.map { it.speaker.name })
        assertEquals(RoomFeed.Row.Day("오늘"), rows.first())
    }

    @Test fun authorlessNoticesSpeakForTheLeadAndGroup() {
        val rows = build(listOf(
            notice(1, "🛠️ Engineer 시작: 하나"),
            notice(2, "✅ 끝: 하나"),
            message(3, "제가 볼게요", author = "Nora"),
            message(4, "확인 부탁해요", owner = true),
        ))
        val list = entries(rows)
        assertEquals(listOf("Leo", "Leo", "Nora", "나"), list.map { it.speaker.name })
        assertEquals(listOf(true, false, true, true), list.map { it.showHeader })
        assertTrue(list[0].isStatus)
        assertEquals("🛠️ 개발 봇 시작: 하나", list[0].text)
    }

    @Test fun longRunsFoldKeepingTheNewestTwo() {
        val rows = build((1..6).map { notice(it, "작업 $it") })
        val f = (rows[1] as? RoomFeed.Row.FoldedRow)?.folded ?: fail("$rows").let { return }
        assertEquals(4, f.entries.size)
        assertEquals("이전 작업 기록 4개", f.title)
        assertEquals(listOf("작업 5", "작업 6"), entries(rows).map { it.text })

        val talk = (1..5).map { notice(it, "💬 Leo → ${if (it % 2 == 0) "Mia" else "Victor"} · 질문: 질문 $it") }
        val talkRows = build(talk)
        val g = (talkRows[1] as? RoomFeed.Row.FoldedRow)?.folded ?: fail("$talkRows").let { return }
        assertEquals("Leo·Victor·Mia의 대화 3개", g.title)
    }

    @Test fun aLongRunFromOneBotFolds() {
        val rows = build((1..9).map { message(it, "✅ ${it}단계", author = "Leo") })
        val f = (rows[1] as? RoomFeed.Row.FoldedRow)?.folded ?: fail("$rows").let { return }
        assertEquals("Leo의 이전 글 7개", f.title)
        assertEquals(2, entries(rows).size)
        // A short run stays as it is.
        assertEquals(5, entries(build((1..5).map { message(it, "글 $it", author = "Leo") })).size)
    }

    @Test fun repeatsAndDays() {
        val rows = build(listOf(
            notice(1, "선택지를 기록했어요", "2026-11-18T01:00:00Z"),
            notice(2, "선택지를 기록했어요", "2026-11-18T01:00:05Z"),
            message(3, "안녕", author = "Leo", iso = "2026-11-19T01:00:00Z"),
            message(4, "다시", author = "Leo", iso = "2026-11-20T01:00:00Z"),
        ))
        assertEquals(listOf("11월 18일 수요일", "어제", "오늘"), rows.filterIsInstance<RoomFeed.Row.Day>().map { it.label })
        assertEquals(2, entries(rows).first().repeats)
    }

    @Test fun threadLinesFoldUnderTheFirst() {
        fun line(seq: Int, text: String, start: Boolean = false): Timeline.Item =
            Timeline.Item.Message(MessageEvent(
                seq = seq, at = at("2026-11-20T01:00:00Z"), role = "assistant", text = text, channel = "개발",
                author = Author("leo", "Leo"), thread = EventThread("app-th-1", "로그인 다시 확인", start),
            ))
        val rows = build(listOf(line(1, "맡았어요.", start = true), message(2, "다른 말", author = "Nora"), line(3, "📋 계획"), line(4, "🏁 결과")))
        val list = entries(rows)
        assertEquals(listOf("맡았어요.", "다른 말"), list.map { it.text })
        assertEquals(listOf("📋 계획", "🏁 결과"), list[0].replies.map { it.text })
        assertEquals("로그인 다시 확인", list[0].threadTitle)
    }

    @Test fun reactionAndReadEvents() {
        val t = Timeline()
        for (json in listOf(
            """{"type":"message","seq":1,"at":"2026-11-20T01:00:00Z","role":"assistant","text":"hi","channel":"개발"}""",
            """{"type":"reaction","seq":2,"at":"2026-11-20T01:00:01Z","target":1,"emoji":"👍","by":{"id":"leo","name":"Leo"},"channel":"개발"}""",
            """{"type":"read","seq":3,"channel":"개발","upTo":1}""",
        )) t.apply(EventDecoding.event(json)!!)
        assertEquals(mapOf("👍" to 1), t.reactions(1))
        assertEquals(0, t.unreadCount("개발"))
        assertEquals(1, t.items("개발").size)
    }

    @Test fun mainRoomNoticesStaySystemLines() {
        val rows = RoomFeed.build(listOf(Timeline.Item.Notice(NoticeEvent(seq = 1, at = now, text = "알림"))), room = "", lead = null, assistant = "비서", now = now, zone = zone)
        assertEquals(2, rows.size)
        assertTrue(rows[1] is RoomFeed.Row.System)
    }
}
