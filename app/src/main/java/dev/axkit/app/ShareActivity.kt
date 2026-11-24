package dev.axkit.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The system share sheet's "ax-kit" (iOS AxKitShare): text and links go to the assistant as a message (through the
 * outbox, so they wait if the Mac is out of reach); a picture or file goes through `POST share`.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = (application as AxApp).model
        if (!model.isPaired) { done("앱에서 먼저 연결해 주세요"); return }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        lifecycleScope.launch {
            if (stream != null) {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val name = contentResolver.query(stream, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "shared"
                    runCatching { contentResolver.openInputStream(stream)?.use { it.readBytes() } }.getOrNull() to name
                }
                if (bytes == null || bytes.size > 12 * 1024 * 1024) { done("파일이 너무 커요. 12MB까지 보낼 수 있어요."); return@launch }
                val ok = model.share(null, bytes, name, contentResolver.getType(stream))
                done(if (ok) "비서에게 보냈어요" else model.toast ?: "보내지 못했어요")
            } else if (!text.isNullOrBlank()) {
                model.send(text, "", shared = true)
                done("비서에게 보낼게요")
            } else done("보낼 내용이 없어요")
        }
    }

    private fun done(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
