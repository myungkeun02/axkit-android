package dev.axkit.app.ui.screens

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import dev.axkit.core.PairingLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** The scanner reads the camera's Y plane; render the Mac's pairing QR into such a plane (with row padding) and read it back. */
class QRDecodeTest {
    @Test
    fun readsPairingQrFromLuminancePlane() {
        val link = "axkit://pair?u=http%3A%2F%2F100.101.102.103%3A47617&n=AbC_-12&c=482913&ci=id.access&cs=s3cr%2Bt"
        val width = 640; val height = 480; val rowStride = 704
        val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 360, 360)
        val plane = ByteArray(rowStride * height) { 0xC8.toByte() } // light grey paper
        val ox = (width - matrix.width) / 2; val oy = (height - matrix.height) / 2
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) plane[(oy + y) * rowStride + ox + x] = 0x14
        }
        val text = decodeLuminance(QRCodeReader(), plane, rowStride, width, height)
        assertEquals(link, text)
        val parsed = PairingLink.parse(text!!)
        assertEquals("http://100.101.102.103:47617", parsed.baseUrl)
        assertEquals("482913", parsed.code)
        assertEquals("s3cr+t", parsed.accessClientSecret)
    }
}
