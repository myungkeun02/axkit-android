package dev.axkit.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.axkit.app.ui.design.ActionButton
import dev.axkit.app.ui.design.ActionKind
import dev.axkit.app.ui.design.AxSheet
import dev.axkit.app.ui.design.BarButton
import dev.axkit.app.ui.design.EmptyStateView
import dev.axkit.app.ui.design.SfIcons
import dev.axkit.app.ui.design.SheetBar
import dev.axkit.app.ui.design.Theme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The camera sheet: point it at the Mac's QR. Only `axkit://` codes count; the first one found is handed back once. */
@Composable
fun QRScannerSheet(onDismiss: () -> Unit, onCode: (String) -> Unit) {
    AxSheet(onDismiss = onDismiss) {
        SheetBar("맥의 QR을 비춰 주세요", leading = { BarButton("닫기") { onDismiss() } })
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        var refused by remember { mutableStateOf(false) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            refused = !ok
        }
        LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                granted -> QRCameraView(onCode)
                refused -> Column(
                    Modifier.fillMaxSize().background(Theme.ax.page),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    EmptyStateView(
                        SfIcons.of("camera.fill"), "카메라를 쓸 수 없어요",
                        "설정에서 카메라를 허용하거나, '연결 링크 붙여넣기'를 써 주세요.",
                    )
                    ActionButton("설정 열기", kind = ActionKind.Primary, fullWidth = false) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                else -> Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}

@Composable
private fun QRCameraView(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(onCode)
    val done = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val holder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.unbindAll()
            executor.shutdown()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                        holder[0] = provider
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        val reader = QRCodeReader()
                        analysis.setAnalyzer(executor) { image ->
                            val text = try { decodeQR(reader, image) } finally { image.close() }
                            if (text != null && text.startsWith("axkit://") && done.compareAndSet(false, true)) {
                                view.post {
                                    holder[0]?.unbindAll()
                                    current(text)
                                }
                            }
                        }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
        )
        // A soft frame to aim at, like the iOS scanner's highlight.
        Box(Modifier.size(240.dp).border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp)))
    }
}

private val qrHints = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
)

/** Reads a QR from the camera frame's Y (brightness) plane. */
private fun decodeQR(reader: QRCodeReader, image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    val bytes = ByteArray(rowStride * height)
    buffer.rewind()
    buffer.get(bytes, 0, minOf(bytes.size, buffer.remaining()))
    return decodeLuminance(reader, bytes, rowStride, width, height)
}

/** The camera-independent part, so a unit test can feed it a rendered QR. */
internal fun decodeLuminance(reader: QRCodeReader, bytes: ByteArray, rowStride: Int, width: Int, height: Int): String? = try {
    val source = PlanarYUVLuminanceSource(bytes, rowStride, height, 0, 0, width, height, false)
    reader.decode(BinaryBitmap(HybridBinarizer(source)), qrHints).text
} catch (e: Exception) {
    null
} finally {
    reader.reset()
}
