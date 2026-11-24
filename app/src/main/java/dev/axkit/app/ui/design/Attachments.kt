package dev.axkit.app.ui.design

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.axkit.app.LocalModel
import dev.axkit.core.Attachment

/** Pictures that come with a message or card: `files/<id>` (needs the device token) or one https `imageUrl`. */
@Composable
fun AttachedImages(attachments: List<Attachment>?, imageUrl: String?, height: Dp = 220.dp) {
    val refs = attachments.orEmpty().filter { it.contentType.startsWith("image/") }.map { it.url } + listOfNotNull(imageUrl)
    if (refs.isEmpty()) return
    Row(
        Modifier.then(if (refs.size > 1) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        refs.forEach { RemoteImage(it, height) }
    }
}

@Composable
fun RemoteImage(ref: String, height: Dp) {
    val model = LocalModel.current
    var image by remember(ref) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(ref) { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(ref) {
        image = model.avatarImage(ref)?.asImageBitmap()
        failed = image == null
    }
    val img = image
    val width = img?.let { minOf(height * (it.width.toFloat() / maxOf(it.height, 1)), 300.dp) } ?: if (failed) minOf(height, 180.dp) else height * 0.8f
    Box(
        Modifier.width(width).height(if (failed) minOf(height, 110.dp) else height).clip(RoundedCornerShape(14.dp)).background(Theme.ax.fill)
            .clickable(enabled = img != null) { full = true }
            .semantics { contentDescription = "첨부 사진" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            img != null -> Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            failed -> androidx.compose.foundation.layout.Column(
                Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Image, null, Modifier.size(22.dp), tint = Theme.ax.secondary)
                Text(
                    "사진을 불러오지 못했어요", style = AxType.caption, color = Theme.ax.secondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            else -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Theme.ax.secondary)
        }
    }
    if (full && img != null) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                        .clickable { full = false }.padding(12.dp),
                ) { Icon(Icons.Rounded.Close, "닫기", tint = Color.White) }
            }
        }
    }
}
