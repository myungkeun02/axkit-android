package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** §34: blocks rows may carry a `message` that makes the row tappable. */
class BlockRowMessageTest {
    private val raw = """
        {"title":"드라이브","blocks":[
          {"kind":"list","title":"오늘 · 2건","rows":[
            {"title":"11월 매출 정리","detail":"회의록 · owner@acme.example","trailing":"14:05","message":"드라이브의 \"11월 매출 정리\" 내용을 요약해 줘"},
            {"title":"근무표","detail":"모아카페"},
            {"title":"빈 문장","message":"   "}
          ]}
        ]}
    """.trimIndent()

    @Test fun rowsWithMessageAreTappable() {
        val rows = ProtocolJson.decodeFromString(BlocksView.serializer(), raw).blocks.single().rows!!
        assertEquals("드라이브의 \"11월 매출 정리\" 내용을 요약해 줘", rows[0].tapMessage)
        assertNull(rows[1].tapMessage)
        assertNull(rows[2].tapMessage)
    }
}
