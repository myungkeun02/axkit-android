package dev.axkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/** §22 browser-live: frames on the stream, JPEG size (coordinates go back in these pixels), state words. */
class BrowserLiveTest {
    private fun jpeg(w: Int, h: Int): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    @Test fun readsJpegSize() {
        assertEquals(824 to 1720, BrowserLiveStream.jpegSize(jpeg(824, 1720)))
    }

    @Test fun decodesFrameEvent() {
        val bytes = jpeg(824, 1720)
        val f = BrowserLiveStream.decodeFrame("""{"seq":3,"url":"https://shop.example.com/login","title":"로그인","width":824,"height":1720,"jpeg":"${Base64.getEncoder().encodeToString(bytes)}"}""")!!
        assertEquals(3, f.seq)
        assertEquals(824, f.width)
        assertTrue(f.jpeg.contentEquals(bytes))
    }

    @Test fun frameWithoutSizeUsesTheJpeg() {
        val f = BrowserLiveStream.decodeFrame("""{"seq":1,"jpeg":"${Base64.getEncoder().encodeToString(jpeg(412, 860))}"}""")!!
        assertEquals(412 to 860, f.width to f.height)
    }

    @Test fun stateDecodesPendingAndClosed() {
        val s = ProtocolJson.decodeFromString(BrowserLiveState.serializer(),
            """{"live":false,"pending":{"url":"https://shop.example.com/login","reason":"login","at":"2026-11-20T03:10:00.000Z"},"closed":"idle","frameUrl":"browser-live/frame"}""")
        assertEquals("login", s.pending?.reason)
        assertEquals("한동안 아무것도 하지 않아서 브라우저 화면을 닫았어요.", BrowserLiveWords.closed(s.closed))
        assertEquals("로그인이 필요해요", BrowserLiveWords.reason("login"))
    }

    @Test fun handoffSentences() {
        assertTrue(BrowserLiveWords.handoff("로그인 화면에서 멈췄어요. 앱의 브라우저 화면에서 직접 하실 수 있어요."))
        assertTrue(BrowserLiveWords.handoff("앱의 브라우저 화면에 준비해 뒀어요. 그 화면에서 직접 하시고 '다 했어요'를 눌러 주세요."))
        assertEquals(false, BrowserLiveWords.handoff("(앱의 브라우저 화면에서 직접 마쳤어요: shop.example.com) 계속해 주세요."))
    }

    @Test fun tapMapsToJpegPixelsInsideAspectFit() {
        // 824×1720 shown in a 412×1000 box: scale = min(0.5, 0.581) = 0.5, picture 412×860, 70 pt bands above and below.
        assertEquals(412 to 465, BrowserLiveWords.pixel(206f, 70f + 232.5f, 412f, 1000f, 824, 1720))
        assertEquals(null, BrowserLiveWords.pixel(206f, 20f, 412f, 1000f, 824, 1720))
    }

    @Test fun buttonComesFromLiveFieldFirst() {
        val withLive = (EventDecoding.event("""{"type":"message","seq":5,"at":"2026-11-20T03:10:00Z","role":"assistant","text":"로그인에서 멈췄어요.","live":{"view":"browser-live","reason":"login","url":"https://shop.example.com/login"}}""") as AppEvent.Message).e
        assertEquals("login", withLive.live?.reason)
        assertTrue(BrowserLiveWords.opensLive(withLive))
        // A newer server that sends no `live` on a line with the sentence still gets the old check (older servers).
        val old = (EventDecoding.event("""{"type":"message","seq":6,"at":"2026-11-20T03:10:00Z","role":"assistant","text":"앱의 브라우저 화면에서 직접 하실 수 있어요."}""") as AppEvent.Message).e
        assertTrue(BrowserLiveWords.opensLive(old))
        val other = (EventDecoding.event("""{"type":"message","seq":7,"at":"2026-11-20T03:10:00Z","role":"assistant","text":"x","live":{"view":"something-else"}}""") as AppEvent.Message).e
        assertEquals(false, BrowserLiveWords.opensLive(other))
    }

    @Test fun doneSaysWhereItCarriesOn() {
        val s = ProtocolJson.decodeFromString(BrowserLiveState.serializer(), """{"live":false,"resumed":true,"continuedIn":"app","channel":"개발"}""")
        assertEquals("app", s.continuedIn); assertEquals("개발", s.channel)
    }
}
