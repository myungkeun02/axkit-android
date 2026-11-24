package dev.axkit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.lifecycleScope
import dev.axkit.app.ui.design.AxTheme
import dev.axkit.app.ui.screens.RootView
import dev.axkit.core.friendly
import kotlinx.coroutines.launch

val LocalModel = staticCompositionLocalOf<AppModel> { error("no model") }

class MainActivity : ComponentActivity() {
    private val model get() = (application as AxApp).model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("resetPairing", false) == true) lifecycleScope.launch { model.unpair() }
        handle(intent)
        setContent {
            CompositionLocalProvider(LocalModel provides model) {
                AxTheme { RootView() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** `axkit://pair?…` links (from the Mac's QR shown as a link) and `axkit://open/approvals|home` from notifications. */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "axkit") return
        if (uri.host == "open") { model.open(uri); return }
        lifecycleScope.launch {
            try { model.pair(uri.toString()); model.toast = "맥과 연결됐어요" }
            catch (e: Exception) { model.toast = e.friendly }
        }
    }
}
