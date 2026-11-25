package dev.axkit.core

/**
 * Buttons in the chat read like a conversation (desktop 56cc0a6): the assistant asks in one bubble with its buttons
 * inside, the owner's pick shows as their own line, and buttons that can no longer be used are dimmed (never hidden).
 */
object ChatChoices {
    /** A main-room notice that asks something (has buttons) or says more than a line is the assistant talking. */
    fun noticeIsBubble(text: String, choices: List<Choice>?): Boolean =
        !choices.isNullOrEmpty() || text.trim().lines().count { it.isNotBlank() } > 1

    /**
     * The seq of the one line in a room whose buttons still work: the latest line with buttons, unless the owner said
     * or picked something after it (then the conversation moved on). Every other line's buttons are dimmed.
     */
    fun liveSeq(items: List<Timeline.Item>): Int? {
        for (item in items.asReversed()) {
            when (item) {
                is Timeline.Item.Message -> {
                    if (item.e.isOwner) return null
                    if (!item.e.choices.isNullOrEmpty()) return item.seq
                }
                is Timeline.Item.Notice -> if (!item.e.choices.isNullOrEmpty()) return item.seq
                else -> {}
            }
        }
        return null
    }

    /** What the owner "said" by answering a card (their line after the question), or null while open / closed elsewhere. */
    fun cardEcho(card: CardEvent, closure: CardClosedEvent?): String? {
        if (closure == null) return null
        closure.answer?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val options = card.options?.takeIf { it.isNotEmpty() } ?: CardWords.defaultOptions(card)
        fun pick(id: String, tone: CardOption.Tone) = options.firstOrNull { it.id == id } ?: options.firstOrNull { it.toneKind == tone }
        return when (closure.state) {
            "approved" -> pick("decision:approve", CardOption.Tone.Primary)?.label ?: "네, 해 주세요"
            "denied" -> pick("decision:deny", CardOption.Tone.Danger)?.label ?: "아니요"
            "edit" -> options.firstOrNull { it.id == "decision:edit" }?.label ?: "고쳐서 다시"
            else -> null
        }
    }

    /** The option a closed card was answered with (marked among the dimmed buttons). */
    fun answeredOption(card: CardEvent, closure: CardClosedEvent?): String? {
        val echo = cardEcho(card, closure) ?: return null
        val options = card.options?.takeIf { it.isNotEmpty() } ?: CardWords.defaultOptions(card)
        return options.firstOrNull { it.label == echo }?.id
    }

    /** Cards answered with buttons right in the bubble; anything with a form or a menu opens 결재 instead. */
    fun answersInBubble(card: CardEvent): Boolean = card.form == null && card.selects.isNullOrEmpty()
}

/** §37 봇 화면에서 바로 고치기: renameTeam / editBot / moveBot, applied at once (no 결재 card). Limits as the Mac's. */
object BotEdit {
    const val NAME_MAX = 40
    const val PURPOSE_MAX = 200
    const val ROLE_MAX = 120
    const val ROLE_MIN = 2

    sealed interface Result {
        data class Body(val fields: Map<String, String>) : Result
        data class Problem(val text: String) : Result
    }

    /** Purpose is sent only when it changed. */
    fun renameTeam(team: BotsView.Team, name: String, purpose: String): Result {
        val n = name.trim(); val p = purpose.trim()
        if (n.isEmpty()) return Result.Problem("팀 이름을 적어 주세요.")
        if (n.length > NAME_MAX) return Result.Problem("팀 이름은 ${NAME_MAX}자까지예요.")
        if (p.length > PURPOSE_MAX) return Result.Problem("하는 일은 ${PURPOSE_MAX}자까지예요.")
        return Result.Body(buildMap { put("teamId", team.id); put("name", n); if (p != (team.purpose ?: "").trim()) put("purpose", p) })
    }

    /** Only what changed; null when nothing did. */
    fun editBot(bot: BotsView.Bot, name: String, role: String): Result? {
        val n = name.trim(); val r = role.trim()
        if (n.isEmpty()) return Result.Problem("이름을 적어 주세요.")
        if (n.length > NAME_MAX) return Result.Problem("이름은 ${NAME_MAX}자까지예요.")
        if (r.isNotEmpty() && r.length < ROLE_MIN) return Result.Problem("역할은 ${ROLE_MIN}자 이상 적어 주세요.")
        if (r.length > ROLE_MAX) return Result.Problem("역할은 ${ROLE_MAX}자까지예요.")
        val fields = buildMap { if (n != bot.name) put("name", n); if (r.isNotEmpty() && r != (bot.role ?: "").trim()) put("role", r) }
        return if (fields.isEmpty()) null else Result.Body(mapOf("botId" to bot.id) + fields)
    }

    data class Target(val id: String, val label: String)

    /** Teams a member can move to (every other team, with the company when there are several); none for a team lead. */
    fun moveTargets(bots: BotsView?, bot: BotsView.Bot, from: BotsView.Team): List<Target> {
        if (bot.rank == "lead" || bots == null) return emptyList()
        val withGroup = bots.groups.flatMap { g -> g.teams.orEmpty().map { g to it } }
        val companies = withGroup.map { it.first.id }.toSet().size
        return withGroup.filter { it.second.id != from.id }.map { (g, t) -> Target(t.id, if (companies > 1) "${g.label} · ${t.name}" else t.name) }
    }

    /** "등급 B · Claude Sonnet · 구독" — the tier and the model a bot runs on now (older Macs send no model). */
    /** The models a team runs on, fewest words: one name when they all share it, else each once. */
    fun teamModels(members: List<BotsView.Bot>): String {
        val names = members.mapNotNull { it.model?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        return if (names.size <= 2) names.joinToString(" / ") else "${names.take(2).joinToString(" / ")} 외 ${names.size - 2}"
    }

    fun modelLine(bot: BotsView.Bot): String = listOfNotNull(bot.tier?.let { "등급 $it" }, bot.model?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

/** §37 (추가) 설명으로 팀 만들기: `POST views/bots/createTeamFromText {text, group?}`. */
object TeamFromText {
    const val MIN = 5
    const val MAX = 6000

    val placeholder = listOf(
        "예) 유튜브 영상 제작팀이 필요해요.",
        "- 매주 화·금 영상 2개: 기획 → 대본 → 촬영 목록 → 편집 지시 → 썸네일·제목",
        "- 채널은 \"모아카페 TV\", 20~30대 직장인 대상, 말투는 친근하게",
        "- 편집은 외주 편집자에게 넘기니 편집 지시서를 꼭 써 줘요",
        "- 올리기 전엔 꼭 저한테 물어봐 주세요",
    ).joinToString("\n")

    fun problem(text: String): String? {
        val t = text.trim()
        if (t.length < MIN) return "어떤 팀인지 조금만 더 적어 주세요."
        if (t.length > MAX) return "${"%,d".format(MAX)}자까지 적을 수 있어요."
        return null
    }

    @kotlinx.serialization.Serializable
    data class Built(
        val teamId: String = "",
        val channel: String = "",
        val group: String? = null,
        val name: String = "",
        val purpose: String = "",
        val members: List<Member> = emptyList(),
        val briefKept: Boolean = false,
    ) {
        @kotlinx.serialization.Serializable data class Member(val id: String = "", val name: String = "", val role: String = "")
        val isValid get() = teamId.isNotBlank() && channel.isNotBlank() && name.isNotBlank()
    }
}
