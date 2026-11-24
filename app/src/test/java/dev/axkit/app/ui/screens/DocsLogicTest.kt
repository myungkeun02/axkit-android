package dev.axkit.app.ui.screens

import dev.axkit.core.DocGraphView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DocsLogicTest {
    private fun doc(id: String, title: String, summary: String? = null, at: Long = 0, source: String = "knowledge", section: String = "회사") =
        DocShelf.Doc(id, title, summary, Instant.ofEpochSecond(at), source, section)

    @Test fun searchRanksTitleThenSummaryThenSectionThenBody() {
        val docs = listOf(
            doc("a", "회의록", "x"), doc("b", "투자 계획"), doc("c", "메모", "투자 목표"), doc("d", "기록", section = "투자"),
        )
        val hits = DocSearch.findLocal("투자", docs, mapOf("knowledge:a" to "# 회의\n- 투자는 50억"), emptyList())
        assertEquals(listOf("b", "c", "d", "a"), hits.map { it.doc.id })
        assertEquals("투자는 50억", hits.last().snippet)
    }

    @Test fun macHitsComeFirstAndAreNotRepeated() {
        val mac = listOf(DocHit(doc("b", "투자 계획"), "줄", 0))
        val hits = DocSearch.findLocal("ㅌㅈ", listOf(doc("b", "투자 계획"), doc("e", "투자 일지", at = 5)), emptyMap(), mac)
        assertEquals(listOf("b", "e"), hits.map { it.doc.id })
    }

    @Test fun treeCarriesRelatedTitles() {
        val g = DocGraphView(tree = listOf(DocGraphView.TreeLayer("l", "결정", items = listOf(
            DocGraphView.TreeLayer.Item("n1", "A", related = listOf(DocGraphView.TreeLayer.Related("n2", "B"))),
        ))))
        val s = DocShelf(DocShelf.from(g))
        assertEquals(listOf("B"), s.all.single().related)
        assertEquals("docgraph:n1", s.all.single().key)
    }

    @Test fun documentTextCleanup() {
        assertEquals("본문", DocText.withoutTitle("\n# 제목\n본문", "제목"))
        assertEquals("대화 기록 · 메모", DocText.readable("transcript:35e5-ab.jsonl:667520 · memory/x_y.md"))
    }

    @Test fun dueWords() {
        assertEquals("32일 지남" to true, todayDueWords("2026-10-19", "2026-11-20"))
        assertEquals("오늘까지" to false, todayDueWords("2026-11-20T09:00", "2026-11-20"))
        assertEquals("내일까지" to false, todayDueWords("2026-11-21", "2026-11-20"))
        assertEquals("12월 3일까지" to false, todayDueWords("2026-12-03", "2026-11-20"))
        assertNull(todayDueWords("someday", "2026-11-20"))
        assertEquals("9시", usageBucketLabel("09:00", true))
        assertEquals("20일", usageBucketLabel("20", false))
    }
}
