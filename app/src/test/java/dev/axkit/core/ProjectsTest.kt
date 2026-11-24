package dev.axkit.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** §23 프로젝트 가져오기: decoding, the draft edits, and the whole-list save body. */
class ProjectsTest {
    private val raw = """
        {"companies":[
          {"name":"ACME","projects":[
            {"path":"/u/acme/web","display":"~/acme/web","name":"acme-web","company":"ACME","remote":"github.com/acme/acme-web","lastActive":"2026-11-20T03:00:00.000Z","claude":12,"codex":3,"picked":true},
            {"path":"/u/acme/api","display":"~/acme/api","name":"acme-api","company":"ACME","lastActive":"2026-11-19T03:00:00.000Z","claude":5,"codex":0,"picked":false}]},
          {"name":"개인","projects":[
            {"path":"/u/old","display":"/u/old","name":"old","company":"개인","claude":0,"codex":0,"picked":true}]}],
         "picked":2,
         "scan":{"status":"running","phase":"projects","counts":{"files":1203,"repos":4,"docs":0,"sessions":38},"future":1}}
    """.trimIndent()

    private val view = ProtocolJson.decodeFromString(ProjectsView.serializer(), raw)

    @Test fun decodes() {
        assertEquals(2, view.companies.size)
        val gone = view.companies[1].projects[0]
        assertNull(gone.lastActive) // picked before, not found now: can only be unpicked
        val now = java.time.Instant.parse("2026-11-20T05:00:00Z")
        assertEquals("2시간 전 작업 · Claude 12 · Codex 3", ProjectsWords.detail(view.companies[0].projects[0], now))
        assertEquals("읽는 중 · 프로젝트 읽는 중", ProjectsWords.status(view.scan))
        assertEquals("저장소 4 · 파일 1203 · 대화 38", ProjectsWords.counts(view.scan?.counts))
        assertEquals("지금은 찾지 못한 폴더예요 · 빼기만 할 수 있어요", ProjectsWords.detail(gone))
    }

    @Test fun saveSendsTheWholePickedListWithEdits() {
        var d = ProjectsDraft.from(view)
        assertEquals(2, d.pickedCount)
        d = d.toggle("/u/acme/api").toggle("/u/old")
        d = d.rename("/u/acme/web", "  ACME 웹 ")
        d = d.renameCompany("ACME", "에이크미")
        d = d.move("/u/acme/api", "새 회사")
        assertEquals(listOf("에이크미", "새 회사", "개인"), d.companyNames)
        val body = d.body(rescan = true)
        val projects = body["projects"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("/u/acme/web", "/u/acme/api"), projects.map { it["path"]!!.jsonPrimitive.content })
        assertEquals("ACME 웹", projects[0]["name"]!!.jsonPrimitive.content)
        assertEquals("에이크미", projects[0]["company"]!!.jsonPrimitive.content)
        assertEquals("새 회사", projects[1]["company"]!!.jsonPrimitive.content)
        assertEquals(true, body["rescan"]!!.jsonPrimitive.boolean)
    }

    @Test fun pickAllAndEmptyList() {
        val d = ProjectsDraft.from(view).pickAll("ACME", false).pickAll("개인", false)
        assertEquals(0, d.pickedCount)
        assertEquals(JsonArray(emptyList()), d.body(rescan = false)["projects"])
        assertNull(d.problem) // an empty pick is allowed: it stops reading everything
    }

    @Test fun emptyNamesAreIgnoredAndLongOnesCut() {
        val d = ProjectsDraft.from(view)
        assertEquals("acme-web", d.rename("/u/acme/web", "  ").rows[0].name)
        assertEquals(80, d.rename("/u/acme/web", "가".repeat(90)).rows[0].name.length)
        assertEquals("ACME", d.move("/u/acme/web", " ").rows[0].company)
        assertNull(d.problem)
    }

    @Test fun savedWords() {
        assertEquals("3개를 저장하고 읽기 시작했어요.", ProjectsWords.saved(ProjectsSaveResult(3, "started"), 3))
        assertEquals("3개를 저장했어요. 지금 읽는 중이라 끝나면 다시 볼게요.", ProjectsWords.saved(ProjectsSaveResult(3, "running"), 3))
        assertEquals("2개를 저장했어요. 다음 날 새벽에 읽어요.", ProjectsWords.saved(ProjectsSaveResult(), 2))
    }
}
