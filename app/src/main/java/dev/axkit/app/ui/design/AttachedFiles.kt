package dev.axkit.app.ui.design

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.axkit.app.LocalModel
import dev.axkit.core.ApiError
import dev.axkit.core.Attachment
import dev.axkit.core.friendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files that aren't pictures (PDF, 한글, 엑셀, zip …, §19): a row with name and size; tap to download and open it in
 * another app (the system chooser, from where it can also be shared or saved). Kept 24 hours on the Mac.
 */
@Composable
fun AttachedFiles(attachments: List<Attachment>?) {
    val files = attachments.orEmpty().filter { !it.contentType.startsWith("image/") }
    if (files.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { files.forEach { FileRow(it) } }
}

@Composable
private fun FileRow(file: Attachment) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val name = file.name.lowercase()
    val icon = when {
        name.endsWith(".pdf") -> Icons.Rounded.PictureAsPdf
        name.endsWith(".zip") -> Icons.Rounded.FolderZip
        name.endsWith(".xls") || name.endsWith(".xlsx") -> Icons.Rounded.TableChart
        name.endsWith(".ppt") || name.endsWith(".pptx") -> Icons.Rounded.Slideshow
        listOf(".doc", ".docx", ".hwp", ".hwpx").any { name.endsWith(it) } -> Icons.AutoMirrored.Rounded.Article
        else -> Icons.Rounded.Description
    }

    fun open() {
        val client = model.client ?: return
        if (loading) return
        loading = true
        scope.launch {
            try {
                val bytes = client.file(file.url)
                val target = withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, "ax-files").apply { mkdirs() }
                    File(folder, file.name.replace("/", "_")).also { it.writeBytes(bytes) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.contentType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(Intent.createChooser(view, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    model.toast = "이 파일을 열 앱이 없어요."
                }
            } catch (e: ApiError.NotFound) {
                model.toast = "파일이 지워졌어요. 맥에는 30일만 보관돼요. 비서에게 다시 보내 달라고 해 주세요."
            } catch (e: Exception) {
                model.toast = e.friendly
            } finally { loading = false }
        }
    }

    Row(
        Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(Theme.ax.card)
            .safeTap { open() }.semantics { contentDescription = "파일 ${file.name}, 눌러서 열기" }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.width(28.dp).size(24.dp), tint = Theme.ax.accent)
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, style = AxType.subheadlineBold, color = Theme.ax.label, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(file.size?.let { Formatter.formatShortFileSize(context, it.toLong()) } ?: "파일", style = AxType.caption, color = Theme.ax.secondary)
        }
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Theme.ax.secondary)
        else Icon(Icons.Rounded.ArrowCircleDown, null, Modifier.size(22.dp), tint = Theme.ax.accent)
    }
}
