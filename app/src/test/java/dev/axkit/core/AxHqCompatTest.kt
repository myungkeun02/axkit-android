package dev.axkit.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ax-hq compatibility (§7.2–§7.3): card ids with `:`, card form / select answers, channels. Same cases as iOS CoreTests. */
class AxHqCompatTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        client = ApiClient(ServerCredentials(server.url("/").toString().trimEnd('/'), "t", "d", "폰"))
    }

    @After fun tearDown() { server.shutdown() }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun cardIdWithColonsIsSentOnce() = runBlocking {
        server.enqueue(ok("""{"ok":true,"state":"open"}"""))
        client.answerCard("p0:b142-a8:0", CardAnswer.Option("x"))
        assertEquals("/m/v1/app/cards/p0:b142-a8:0", server.takeRequest().path)
    }

    @Test fun formAndSelectAnswers() = runBlocking {
        server.enqueue(ok("""{"ok":true,"state":"approved"}"""))
        server.enqueue(ok("""{"ok":true,"state":"approved"}"""))
        client.answerCard("p0:1", CardAnswer.Form("pin:p0:1", mapOf("pin" to "123456")))
        client.answerCard("c", CardAnswer.Select("s1", listOf("a", "b")))
        val form = (ProtocolJson.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject)["form"]!!.jsonObject
        assertEquals("pin:p0:1", form["id"]!!.jsonPrimitive.content)
        assertEquals("123456", form["fields"]!!.jsonObject["pin"]!!.jsonPrimitive.content)
        val select = (ProtocolJson.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject)["select"]!!.jsonObject
        assertEquals(listOf("a", "b"), select["values"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun cardWithFormAndSelectsDecodes() {
        val event = EventDecoding.event(
            """{"type":"card","seq":1,"at":"2026-11-18T06:19:46Z","cardId":"p0:1","summary":"x","effects":[],"effectLabels":[],""" +
                """"form":{"id":"pin:p0:1","title":"PIN","fields":[{"id":"pin","label":"PIN","secret":true}]},""" +
                """"selects":[{"id":"s1","max":2,"choices":[{"id":"a","label":"A"}]}]}""",
        )
        assertTrue(event is AppEvent.Card)
        val card = (event as AppEvent.Card).e
        assertEquals(true, card.form?.fields?.first()?.secret)
        assertEquals(2, card.selects?.first()?.max)
    }

    @Test fun channelsViewDecodes() = runBlocking {
        server.enqueue(ok("""{"ok":true,"view":"channels","data":{"channels":[{"id":"ch-approvals","label":"결재","kind":"channel","writable":false}]}}"""))
        val view = client.view(ChannelsView.serializer(), "channels")
        assertEquals(false, view.channels.single().writable)
    }

    @Test fun knowledgeHealthAndReactions() = runBlocking {
        server.enqueue(ok("""{"ok":true,"view":"knowledge","version":1,"data":{"areas":[{"id":"a","label":"회사","docs":[{"id":"d1","title":"규칙","updatedAt":"2026-11-10T00:00:00Z"},{"id":"d2","title":"예산","updatedAt":"2026-11-18T00:00:00Z"}]},{"id":"b","label":"기록","docs":[{"id":"d2","title":"예산","updatedAt":"2026-11-18T00:00:00Z"}]}]}}"""))
        server.enqueue(ok("""{"ok":true,"view":"health","version":1,"data":{"summary":"2가지","checks":[{"id":"db","label":"저장소","status":"ok"},{"id":"disk","label":"저장 공간","status":"warn","detail":"82%"}]}}"""))
        server.enqueue(ok("""{"ok":true}"""))
        val k = client.view(KnowledgeView.serializer(), "knowledge")
        assertEquals("newest first, each document once", listOf("d2", "d1"), k.recent(5).map { it.id })
        val h = client.view(HealthView.serializer(), "health")
        assertEquals("warn", h.checks.last().status)
        client.react(42, "👎")
        server.takeRequest(); server.takeRequest()
        val r = server.takeRequest()
        assertEquals("/m/v1/app/messages/42/react", r.path)
        assertEquals("👎", (ProtocolJson.parseToJsonElement(r.body.readUtf8()) as JsonObject)["emoji"]!!.jsonPrimitive.content)
    }
}
