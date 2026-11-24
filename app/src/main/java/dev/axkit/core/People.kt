package dev.axkit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * §29 `GET views/people`: the people the owner told the assistant about ("김민수 님은 우리 회사 대표야"), one card
 * each. Every room reads them. The owner can fix 직함·회사·관계·메모 here; deleting or merging is done by talking.
 */
@Serializable
data class PeopleView(val title: String = "사람", val people: List<Person> = emptyList()) {
    /** Cards without a key or a name can't be edited or shown: dropped. */
    val cards: List<Person> get() = people.filter { it.key.isNotBlank() && it.name.isNotBlank() }
}

@Serializable
data class Person(
    val key: String = "",
    val name: String = "",
    val names: List<String> = emptyList(),
    val title: String? = null,
    val company: String? = null,
    val relation: String? = null,
    val note: String? = null,
    /** How many mail addresses the Mac knows (the addresses themselves are never sent). */
    val emails: Int = 0,
    val lastSeen: IsoInstant? = null,
    val openCommitments: Int = 0,
    val updatedAt: IsoInstant? = null,
) {
    /** Other names the owner uses for this person ("김 대표님"). */
    val alsoCalled: List<String> get() = names.map { it.trim() }.filter { it.isNotEmpty() && it != name }.distinct()
    val noteLines: List<String> get() = note.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

@Serializable data class PersonEdited(val person: Person? = null)

object PeopleWords {
    const val EMPTY = "대화에서 사람을 소개하면 여기에 카드가 생겨요. 예: '김민수 님은 우리 회사 대표야'"
    const val DELETE_BY_TALKING = "카드를 지우거나 두 사람을 합치려면 대화로 말해 주세요. 예: \"이수진 카드 지워 줘\""
    val FIELDS = listOf("title", "company", "relation", "note")
    val LABELS = mapOf("title" to "직함·역할", "company" to "회사", "relation" to "나와의 관계", "note" to "메모")
    /** §29 limits, the same as the Mac's (it answers 400 with which box is too long). */
    val MAX = mapOf("title" to 40, "company" to 60, "relation" to 80, "note" to 400)
    val HINTS = mapOf("title" to "예: 대표", "company" to "예: 에이컴퍼니", "relation" to "예: 내가 일하는 회사 대표", "note" to "특징을 한 줄씩. 예: 결정이 빠름")

    /** "대표 · 에이컴퍼니" */
    fun roleLine(p: Person): String = listOfNotNull(p.title?.trim(), p.company?.trim()).filter { it.isNotEmpty() }.joinToString(" · ")

    /** Search by any name the owner uses, the company, the relation or the title (spaces and case ignored). */
    fun matches(p: Person, query: String): Boolean {
        fun flat(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
        val q = flat(query)
        if (q.isEmpty()) return true
        return (listOf(p.name) + p.names + listOfNotNull(p.company, p.relation, p.title, p.note)).any { flat(it).contains(q) }
    }

    fun form(p: Person): Map<String, String> =
        mapOf("title" to (p.title ?: ""), "company" to (p.company ?: ""), "relation" to (p.relation ?: ""), "note" to (p.note ?: ""))

    /** §29 edit body: the key, and only the boxes that changed; a box the owner emptied goes as "" (clears it). */
    fun editBody(p: Person, form: Map<String, String>): JsonObject {
        val before = form(p)
        return buildJsonObject {
            put("key", JsonPrimitive(p.key))
            for (f in FIELDS) {
                val v = (form[f] ?: "").trim()
                if (v != before[f]!!.trim()) put(f, JsonPrimitive(v))
            }
        }
    }

    /** The card as it is after a save when the Mac's answer has no `person` (older server). */
    fun applied(p: Person, body: JsonObject): Person {
        fun v(f: String, old: String?) = if (body.containsKey(f)) (body[f] as JsonPrimitive).content.ifEmpty { null } else old
        return p.copy(title = v("title", p.title), company = v("company", p.company), relation = v("relation", p.relation), note = v("note", p.note))
    }
}
