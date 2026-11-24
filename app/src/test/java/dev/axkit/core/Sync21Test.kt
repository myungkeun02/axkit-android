package dev.axkit.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Port of the iOS CoreTests additions since 2a9922b (SSH tests are ported elsewhere) and KoreanSearchTests. */
class Sync21Test {
    private val at: Instant = Instant.ofEpochSecond(1_800_000_000)
    private fun message(seq: Int, text: String) = AppEvent.Message(MessageEvent(seq = seq, at = at, text = text))

    @Test fun lateEventsSlotInAndRoomsKeepTheNewest() {
        val t = Timeline()
        val max = Timeline.MAX_PER_ROOM
        for (seq in 1..(max + 5)) if (seq != 1003) t.apply(message(seq, "m$seq"))
        t.apply(message(1003, "late"))
        val seqs = t.items("").map { it.seq }
        assertEquals(max, seqs.size)
        assertEquals(seqs.sorted(), seqs)
        assertEquals(max + 5, seqs.last())
        assertTrue(seqs.contains(1003))
        assertEquals(max, t.unreadCount(""))
    }

    @Test fun cleanedTextIsTheSameFromTheCache() {
        val raw = "<@123> 배포 끝 :white_check_mark: · v2-ax-qa-web-4ac6f8339bea2b7d\n\n\n\n## 다음\n- 확인"
        val first = ChatText.clean(raw)
        assertEquals(first, ChatText.cleanUncached(raw, Format.seoul))
        assertEquals(first, ChatText.clean(raw))
        assertEquals(ChatText.preview(raw, limit = 10), ChatText.preview(raw, limit = 10))
        assertNotEquals(ChatText.preview(raw, limit = 10), ChatText.preview(raw, limit = 100))
        val date = Instant.ofEpochSecond(1_800_000_000)
        assertEquals(Format.clock(date), Format.clock(date))
        assertEquals("2027-01-15", Format.ymd(date, ZoneId.of("UTC")))
    }

    // KoreanSearchTests (RoomFeedTests.swift)
    @Test fun koreanSpacesCaseAndPartialWords() {
        assertTrue(KoreanSearch.matches("결제/PG 정리", "결제pg"))
        assertTrue(KoreanSearch.matches("앱 후원 멤버십", "후원멤버"))
        assertFalse(KoreanSearch.matches("법무·정책", "결제"))
    }

    @Test fun koreanInitialSounds() {
        assertTrue(KoreanSearch.matches("결제 화면", "ㄱㅈ"))
        assertTrue(KoreanSearch.matches("결제 화면", "ㄱㅈㅎ"))
        assertFalse(KoreanSearch.matches("법무", "ㄱㅈ"))
    }

    @Test fun koreanRangeAndSnippet() {
        val text = "토스 본인 확인 계약"
        val r = KoreanSearch.range(text, "본인확인")
        assertEquals("본인 확인", r?.let { text.substring(it.first, it.last + 1) })
        val t2 = "결제 화면"
        assertEquals("화면", KoreanSearch.range(t2, "ㅎㅁ")?.let { t2.substring(it.first, it.last + 1) })
        assertEquals("토스 본인확인: 아직 안 했어요", KoreanSearch.snippet("# 제목\n- **토스 본인확인**: 아직 안 했어요", "본인확인"))
        assertNull(KoreanSearch.snippet("없음", "결제"))
    }
}
