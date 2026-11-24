package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §31 작업 시트 (tree, order, folded line, where 열기 goes) and `@` bot names in the chat box. */
class WorkPanelMentionTest {
    private fun item(id: String, status: String, parent: String? = null, address: String? = null, title: String = id) =
        WorkView.Item(id = id, kind = "bot", title = title, status = status, parent = parent, origin = address?.let { WorkView.Origin("channel.app", it) })

    private val view = WorkView(
        active = listOf(
            item("a", "running", title = "재고 팀장 · 가격 비교"),
            item("a2", "running", parent = "a"),
            item("m2", "running", parent = "m"),
            item("orphan", "queued", parent = "gone"),
        ),
        recent = listOf(
            item("m", "done", title = "홍보 팀장 · 인스타 글"),
            item("a1", "done", parent = "a"),
            item("r1", "done"), item("r2", "failed"), item("r3", "done"), item("r4", "done"), item("r5", "done"), item("r6", "done"),
        ),
    )

    @Test fun treeAndOrder() {
        val l = WorkPanel.layout(view)
        // A parent that ended stays up top while its child runs; children right under it, running first.
        assertEquals(listOf("a" to 0, "a2" to 1, "a1" to 1, "m" to 0, "m2" to 1, "orphan" to 0), l.active.map { it.item.id to it.depth })
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), l.recent.map { it.item.id })
        assertEquals(4, l.runningCount)
    }

    @Test fun loopsDoNotHang() {
        val loop = WorkView(active = listOf(item("x", "running", parent = "y"), item("y", "running", parent = "x")))
        assertEquals(setOf("x", "y"), WorkPanel.layout(loop).active.map { it.item.id }.toSet())
    }

    @Test fun stripLine() {
        assertEquals("하는 중 4개 · 재고 팀장 · 가격 비교", WorkPanel.strip(view))
        assertNull(WorkPanel.strip(WorkView(recent = listOf(item("d", "done")))))
        assertNull(WorkPanel.strip(null))
    }

    @Test fun whereOpenGoes() {
        val bots = mapOf("stock" to "stock")
        assertEquals("", WorkPanel.room(item("o", "running", address = "owner")) { bots[it] })
        assertEquals("stock", WorkPanel.room(item("b", "running", address = "bot:stock")) { bots[it] })
        assertNull(WorkPanel.room(item("b", "running", address = "bot:nobody")) { bots[it] })
        assertNull(WorkPanel.room(item("n", "running")) { bots[it] })
    }

    @Test fun mentionWord() {
        assertEquals(Mention.Query(0, 2, "재"), Mention.query("@재", 2))
        assertEquals(Mention.Query(3, 4, ""), Mention.query("안녕 @", 4))
        assertNull(Mention.query("me@mail", 7)) // a mail address is not a mention
        assertNull(Mention.query("@재고 팀장 안녕", 9)) // a space ends the word
        assertNull(Mention.query("그냥 글", 4))
    }

    @Test fun mentionSuggestAndInsert() {
        val names = listOf("재고 팀장", "매장 팀장", "재고 직원 1", "개발 팀장")
        assertEquals(listOf("재고 팀장", "재고 직원 1"), Mention.suggest(names, "재고"))
        assertEquals(listOf("매장 팀장"), Mention.suggest(names, "ㅁㅈ"))
        assertEquals(4, Mention.suggest(names, "").size)
        val q = Mention.query("안녕 @재 오늘", 5)!!
        assertEquals(Mention.Inserted("안녕 @재고 팀장 오늘", 10), Mention.insert("안녕 @재 오늘", q, "재고 팀장"))
        val end = Mention.query("@개", 2)!!
        assertEquals(Mention.Inserted("@개발 팀장 ", 7), Mention.insert("@개", end, "개발 팀장"))
    }
}
