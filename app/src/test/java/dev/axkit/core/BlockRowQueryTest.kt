package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §38: rows[].query opens a "folder" of the same blocks screen; it wins over message. */
class BlockRowQueryTest {
    private fun rows(json: String) = ProtocolJson.decodeFromString(BlocksView.serializer(), json).blocks[0].rows!!

    @Test fun queryIsReadAsUrlParametersAndWinsOverMessage() {
        val r = rows("""{"title":"드라이브","blocks":[{"kind":"list","rows":[
            {"title":"📁 기획","query":{"mode":"tree","source":"acme","folder":"f1","path":"내 드라이브 › 기획","depth":2,"shared":false,"none":null},"message":"요약해 줘"},
            {"title":"파일","message":" 요약해 줘 "},
            {"title":"처음","query":{}},
            {"title":"그냥"}]}]}""")
        assertEquals(mapOf("mode" to "tree", "source" to "acme", "folder" to "f1", "path" to "내 드라이브 › 기획", "depth" to "2", "shared" to "false"), r[0].tapQuery)
        assertNull(r[0].tapMessage)
        assertNull(r[1].tapQuery)
        assertEquals("요약해 줘", r[1].tapMessage)
        assertEquals(emptyMap<String, String>(), r[2].tapQuery)
        assertNull(r[3].tapQuery)
        assertNull(r[3].tapMessage)
    }

    @Test fun backGoesUpOneFolderAndEmptyQueryIsTheFirstScreen() {
        var t = BlocksView.Trail()
        assertFalse(t.canGoBack)
        assertEquals(emptyMap<String, String>(), t.current)
        t = t.open(mapOf("mode" to "tree")).open(mapOf("mode" to "tree", "source" to "acme"))
        assertTrue(t.canGoBack)
        assertEquals("acme", t.current["source"])
        t = t.back()
        assertEquals(mapOf("mode" to "tree"), t.current)
        t = t.open(mapOf("mode" to "tree", "folder" to "x")).open(emptyMap())
        assertFalse(t.canGoBack)
        assertEquals(BlocksView.Trail(), t.back())
    }
}
