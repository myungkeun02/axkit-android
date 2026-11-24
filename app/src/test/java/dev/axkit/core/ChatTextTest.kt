package dev.axkit.core

import org.junit.Assert.*
import org.junit.Test

/** Port of ChatTextTests in RoomFeedTests.swift. */
class ChatTextTest {
    @Test fun discordLeftoversGo() {
        assertEquals("확인했어요 ✅", ChatText.clean("<@123456789> 확인했어요 :white_check_mark:"))
        assertEquals("방에 올렸어요", ChatText.clean("<#998877> 방에 올렸어요 <:blob:1234>"))
        assertEquals("이상한 코드", ChatText.clean("이상한 :not_a_real_code: 코드"))
        assertEquals("작은 글", ChatText.clean("-# 작은 글"))
        assertEquals("비밀", ChatText.clean("||비밀||"))
        assertFalse(ChatText.clean("시간 <t:1763600000:R>").contains("<t:"))
        // URLs and times keep their colons.
        assertEquals("https://example.com/a:b 오후 3:20", ChatText.clean("https://example.com/a:b 오후 3:20"))
    }

    @Test fun blockMarkdownBecomesPlainLines() {
        assertEquals("**결론**\n인용\n• 하나\ncode", ChatText.clean("## 결론\n> 인용\n- 하나\n```\ncode\n```"))
    }

    @Test fun metadataAndPromptBlocksAreCut() {
        val note = "슬랙에서 빼자고 했어요.\n\neventId: 8648\nsource: slack / account: x\nfrom: joy\n\n원문\n<knowledge_x>\n- 비밀 맥락\n</knowledge_x>"
        assertEquals("슬랙에서 빼자고 했어요.", ChatText.clean(note))
        assertEquals("앞 글", ChatText.clean("앞 글\n<knowledge_abc>\n안 닫힘"))
        assertEquals("슬랙에서 빼자고 했어요.", ChatText.cardTitle("eventId: 8648", note))
        assertEquals("노트북 살까요?", ChatText.cardTitle("노트북 살까요?", null))
        // One ordinary "Note: …" line in the middle stays.
        assertEquals("Note: 그대로\n다음 줄", ChatText.clean("Note: 그대로\n다음 줄"))
    }

    @Test fun internalIdsAndEnglishRoleWords() {
        assertEquals("🛠️ 개발 봇 시작: 로그인 고치기",
            ChatText.clean("🛠️ Engineer 시작: 로그인 고치기 · v2-ax-qa-web-4ac6f8339bea2b7d · ax/qa-web-4ac6f8339bea2b7d-2026-11-18"))
        assertEquals("승인 카드를 올렸어요", ChatText.clean("승인 카드를 올렸어요 (head a614e74)"))
        assertEquals("개발팀에 넘겼어요: 홈 피드", ChatText.clean("개발팀에 넘겼어요 (작업 37ac4816): 홈 피드"))
        assertEquals("→ 결재 탭", ChatText.clean("→ #결재"))
        assertEquals("보고 — 일상 방에 알림", ChatText.clean("보고 — #일상에 알림"))
        assertEquals("PR #712 반영", ChatText.clean("PR #712 반영"))
        assertEquals("[https://a.com/__없는] 화면", ChatText.clean("[https://a.com/__%EC%97%86%EB%8A%94] 화면"))
        assertEquals("⚠️ 출시 없이 정리됨", ChatText.clean("⚠️ ⚠️ 출시 없이 정리됨"))
    }

    @Test fun previewIsOneLine() {
        assertEquals("굵게 · 하나 · 둘", ChatText.preview("**굵게**\n- 하나\n- 둘"))
        // Swift counts characters (graphemes); these are single BMP chars, so length matches.
        assertEquals(10, ChatText.preview("가".repeat(200), limit = 10).length)
    }

    @Test fun botTalkParses() {
        val ask = BotTalk.parse("💬 Leo → Mia · 검토 요청: Leo입니다. 이렇게 바꾸자는 제안이 나왔어요.")
        assertEquals("Leo", ask?.from); assertEquals("Mia", ask?.to); assertEquals("검토 요청", ask?.label); assertEquals(false, ask?.isReply)
        val reply = BotTalk.parse("↩️ Mia → Leo · 답 (승인): Leo, 방향은 승인해요.\n둘째 줄")
        assertEquals("Mia", reply?.from); assertEquals(true, reply?.isReply); assertEquals("Leo, 방향은 승인해요.\n둘째 줄", reply?.text)
        assertNull(BotTalk.parse("🛠️ Engineer 시작: 로그인 고치기"))
    }
}
