package dev.axkit.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §29 사람 and §30 받아쓰기: decoding, search, the edit body, where dictated words go. */
class PeopleDictationTest {
    private val raw = """
        {"title":"사람","people":[
          {"key":"p1","name":"김민수","names":["김민수","김 대표님"],"title":"대표","company":"에이컴퍼니","relation":"내가 일하는 회사 대표",
           "note":"결정이 빠름\n\n월요일 회의","emails":2,"openCommitments":2,"updatedAt":"2026-11-24T01:00:00Z","future":1},
          {"key":"p2","name":"이수진","relation":"지인"},
          {"key":"","name":"빈 키"}]}
    """.trimIndent()
    private val people = ProtocolJson.decodeFromString(PeopleView.serializer(), raw).cards

    @Test fun decodesAndSkipsBrokenCards() {
        assertEquals(listOf("p1", "p2"), people.map { it.key })
        assertEquals(listOf("김 대표님"), people[0].alsoCalled)
        assertEquals(listOf("결정이 빠름", "월요일 회의"), people[0].noteLines)
        assertEquals(0, people[1].emails)
        assertEquals("대표 · 에이컴퍼니", PeopleWords.roleLine(people[0]))
        assertEquals("", PeopleWords.roleLine(people[1]))
    }

    @Test fun searchByAnyName() {
        val (roh, lee) = people
        assertTrue(PeopleWords.matches(roh, "김대표"))
        assertTrue(PeopleWords.matches(roh, "에이컴"))
        assertTrue(PeopleWords.matches(lee, "지인"))
        assertFalse(PeopleWords.matches(lee, "에이컴"))
        assertTrue(PeopleWords.matches(lee, "  "))
    }

    @Test fun editSendsOnlyChangesAndEmptiedAsBlank() {
        val roh = people[0]
        assertEquals(setOf("key"), PeopleWords.editBody(roh, PeopleWords.form(roh)).keys)
        val body = PeopleWords.editBody(roh, PeopleWords.form(roh) + mapOf("title" to " 공동대표 ", "company" to ""))
        assertEquals(setOf("key", "title", "company"), body.keys)
        assertEquals("공동대표", body["title"]!!.jsonPrimitive.content)
        assertEquals("", body["company"]!!.jsonPrimitive.content)
        assertEquals(setOf("key"), PeopleWords.editBody(roh, PeopleWords.form(roh) + ("note" to roh.note + "\n")).keys)
        val after = PeopleWords.applied(roh, body)
        assertEquals("공동대표", after.title)
        assertNull(after.company)
        assertEquals(roh.relation, after.relation)
        assertEquals(mapOf("title" to 40, "company" to 60, "relation" to 80, "note" to 400), PeopleWords.MAX)
    }

    @Test fun dictationAnswer() {
        assertEquals(MacDictation.Answer("안녕하세요", null), MacDictation.decode(JsonObject(mapOf("text" to JsonPrimitive(" 안녕하세요 ")))))
        val none = MacDictation.decode(JsonObject(mapOf("text" to JsonPrimitive(""), "message" to JsonPrimitive("말소리를 알아듣지 못했어요."))))
        assertEquals("", none.text)
        assertEquals("말소리를 알아듣지 못했어요.", none.message)
        assertEquals(MacDictation.Answer("", null), MacDictation.decode(null))
    }

    @Test fun wordsGoInAtTheCaret() {
        assertEquals(MacDictation.Inserted("안녕", 2), MacDictation.insert("", 0, 0, " 안녕 "))
        assertEquals(MacDictation.Inserted("내일 회의 잡아 줘", 5), MacDictation.insert("내일 잡아 줘", 3, 3, "회의"))
        assertEquals(MacDictation.Inserted("내일 회의", 5), MacDictation.insert("내일", 2, 2, "회의"))
        assertEquals(MacDictation.Inserted("A 새말 C", 4), MacDictation.insert("A B C", 2, 3, "새말"))
        assertEquals(MacDictation.Inserted("abc", 3), MacDictation.insert("abc", 9, 9, ""))
    }

    @Test fun meterAndSilenceAndClock() {
        assertEquals(0f, MacDictation.meterOf(0))
        assertEquals(1f, MacDictation.meterOf(32767), 0.001f)
        assertTrue(MacDictation.meterOf(3000) in 0.4f..0.8f)
        assertTrue(MacDictation.isSilent(10))
        assertFalse(MacDictation.isSilent(2000))
        assertEquals("0:07", MacDictation.clock(7.9))
        assertEquals("3:00", MacDictation.clock(180.0))
    }
}
