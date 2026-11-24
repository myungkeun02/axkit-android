package dev.axkit.core

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §24 팀 구조 미리보기: decoding, edits within the limits, and the whole-list save body. */
class TeamPlanTest {
    private val raw = """
        {"plan":{"id":"p1","createdAt":"2026-11-23T03:00:00.000Z","status":"proposed","teams":[
          {"company":"스튜디오 오름","name":"브랜드A 리뉴얼","purpose":"로고·가이드 개편","kind":"general",
           "roles":[{"title":"레퍼런스 조사","does":"비슷한 사례를 모아요"}],"tasks":["주간 시안 진행 점검"],
           "routines":[{"title":"시안 진행 점검","instruction":"이번 주 시안 진행 상황을 정리해 알려 줘","schedule":{"kind":"weekly","day":"mon","at":"09:30"}}]},
          {"company":"스튜디오 오름","name":"웹사이트","purpose":"","kind":"dev","roles":[],"tasks":[],"routines":[
           {"title":"배포 확인","instruction":"배포 상태를 확인해 줘","schedule":{"kind":"every","hours":3}}]},
          {"company":"개인","name":"블로그","kind":"general","roles":[],"tasks":[],"routines":[]}]}}
    """.trimIndent()

    private val plan = ProtocolJson.decodeFromString(TeamPlanView.serializer(), raw).plan!!

    @Test fun decodesAndNoPlan() {
        assertTrue(plan.proposed)
        assertTrue(plan.teams[1].isDev)
        assertNull(ProtocolJson.decodeFromString(TeamPlanView.serializer(), """{"plan":null}""").plan)
        assertEquals(listOf("스튜디오 오름", "개인"), TeamPlanEdit.companies(plan.teams).map { it.first })
        assertEquals("회사 2곳 · 팀 3개 · 루틴 2개 · 만들기 전에 보고 고칠 수 있어요", TeamPlanEdit.cardLine(plan))
        assertEquals("만들었어요 · 회사 2곳 · 팀 3개 · 루틴 2개", TeamPlanEdit.cardLine(plan.copy(status = "applied")))
    }

    @Test fun scheduleWords() {
        assertEquals("매주 월 09:30", TeamPlanEdit.scheduleText(plan.teams[0].routines[0].schedule))
        assertEquals("3시간마다", TeamPlanEdit.scheduleText(plan.teams[1].routines[0].schedule))
        assertEquals("매일 18:00", TeamPlanEdit.scheduleText(TeamPlanView.Schedule("daily", at = "18:00")))
    }

    @Test fun editsAndBody() {
        var teams = TeamPlanEdit.removeTeam(plan.teams, 2)
        teams = TeamPlanEdit.update(teams, 0) { it.copy(name = " 브랜드 A ", company = "오름") }
        teams = TeamPlanEdit.update(teams, 0) { TeamPlanEdit.addRole(it) }
        teams = TeamPlanEdit.update(teams, 1) { it.copy(routines = emptyList()) }
        assertNull(TeamPlanEdit.problem(teams))
        val body = TeamPlanEdit.body(teams)["teams"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, body.size)
        assertEquals("브랜드 A", body[0]["name"]!!.jsonPrimitive.content)
        assertEquals("오름", body[0]["company"]!!.jsonPrimitive.content)
        assertEquals(2, body[0]["roles"]!!.jsonArray.size)
        assertEquals(listOf("주간 시안 진행 점검"), body[0]["tasks"]!!.jsonArray.map { it.jsonPrimitive.content })
        val schedule = body[0]["routines"]!!.jsonArray[0].jsonObject["schedule"]!!.jsonObject
        assertEquals(setOf("kind", "day", "at"), schedule.keys)
        assertEquals("dev", body[1]["kind"]!!.jsonPrimitive.content)
        assertEquals(0, body[1]["routines"]!!.jsonArray.size)
    }

    @Test fun everyHasOnlyHours() {
        val s = TeamPlanEdit.body(plan.teams)["teams"]!!.jsonArray[1].jsonObject["routines"]!!.jsonArray[0].jsonObject["schedule"]!!.jsonObject
        assertEquals(setOf("kind", "hours"), s.keys)
        assertEquals(3, s["hours"]!!.jsonPrimitive.int)
    }

    @Test fun limits() {
        var team = plan.teams[0]
        repeat(10) { team = TeamPlanEdit.addRole(team) }
        assertEquals(TeamPlanEdit.MAX_ROLES, team.roles.size)
        val longRole = plan.teams[0].copy(roles = listOf(TeamPlanView.Role("가".repeat(21))))
        assertNotNull(TeamPlanEdit.problem(listOf(longRole)))
        val emptyName = plan.teams[0].copy(name = "  ")
        assertNotNull(TeamPlanEdit.problem(listOf(emptyName)))
        val badTime = plan.teams[0].copy(routines = listOf(plan.teams[0].routines[0].copy(schedule = TeamPlanView.Schedule("daily", at = "9:30"))))
        assertNotNull(TeamPlanEdit.problem(listOf(badTime)))
        assertNotNull(TeamPlanEdit.problem(List(25) { plan.teams[2] }))
    }

    @Test fun planCardTopicAndAppliedWords() {
        assertTrue(TeamPlanEdit.isPlanCard("bots:team-plan:p1"))
        assertFalse(TeamPlanEdit.isPlanCard("tiers:review:2026-47"))
        assertTrue(TeamPlanEdit.opensScreen("bots:team-plan:p1", "fix-in-app"))
        assertTrue(TeamPlanEdit.opensScreen("bots:team-plan:p1", "edit")) // servers before 870e742
        assertFalse(TeamPlanEdit.opensScreen("bots:team-plan:p1", "build"))
        assertFalse(TeamPlanEdit.opensScreen("dev:suggest:x", "fix-in-app"))
        assertEquals("팀 3개와 루틴 2개를 만들었어요.", TeamPlanEdit.appliedWords(TeamPlanApplied(3, 2)))
        assertEquals("팀 1개를 만들었어요.", TeamPlanEdit.appliedWords(TeamPlanApplied(1, 0)))
    }
}
