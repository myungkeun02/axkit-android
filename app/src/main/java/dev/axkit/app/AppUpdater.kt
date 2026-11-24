package dev.axkit.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.axkit.core.ApiClient
import dev.axkit.core.ApiError
import dev.axkit.core.AppUpdates
import dev.axkit.core.AppUpdatesView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * §32 앱 업데이트 without a store: the Mac builds this app with one key and a growing build number, and offers it in
 * `views/app-updates`. When its build is bigger than ours, "새 버전이 있어요 · 바꾸기"; a tap downloads the APK into the
 * cache, checks its sha256, and hands it to the system installer (the owner confirms; data and pairing stay).
 */
class AppUpdater(private val context: Context, private val scope: CoroutineScope) {
    sealed interface Phase {
        data object Idle : Phase
        /** 0..1, or -1 when the size is unknown. */
        data class Downloading(val progress: Float) : Phase
        data object Verifying : Phase
        /** "출처를 알 수 없는 앱" is off for this app: the owner turns it on once, then 바꾸기 again. */
        data object NeedsPermission : Phase
        data object Installing : Phase
        data class Failed(val why: String) : Phase
    }

    /** The newer build the Mac offers, or null. */
    var available by mutableStateOf<AppUpdatesView.Build?>(null); private set
    var phase by mutableStateOf<Phase>(Phase.Idle); private set
    /** "나중에" on the banner: hidden until the app starts again (the settings row stays). */
    var bannerHidden by mutableStateOf(false)

    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private var checkedThisRun = false
    private var job: Job? = null
    private val myBuild: Int get() = BuildConfig.VERSION_CODE

    init { current = this }

    /** On start and once a day (callers may call it often; it only reads when due). */
    suspend fun check(client: ApiClient, enabled: Boolean) {
        if (!enabled) { available = null; return }
        val now = System.currentTimeMillis()
        if (!AppUpdates.due(prefs.getLong("lastCheck", 0), now, startedNow = !checkedThisRun)) return
        checkedThisRun = true
        val view = runCatching { client.view(AppUpdatesView.serializer(), "app-updates") }.getOrNull() ?: return
        prefs.edit().putLong("lastCheck", now).apply()
        available = AppUpdates.offer(view, myBuild)
        // Old downloads (an older offer, or one already installed) go.
        folder().listFiles()?.filter { f -> available?.let { f.name != fileName(it) } ?: true }?.forEach { it.delete() }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system page where the owner allows this app to install updates. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 바꾸기: download (or reuse a verified download), check, install. */
    fun update(client: ApiClient) {
        val build = available ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            val file = File(folder(), fileName(build))
            try {
                if (!(file.isFile && withContext(Dispatchers.IO) { AppUpdates.matches(file, build.sha256) })) {
                    phase = Phase.Downloading(0f)
                    val part = File(folder(), fileName(build) + ".part")
                    client.download("app-updates/android.apk", part) { p -> scope.launch(Dispatchers.Main) { if (phase is Phase.Downloading) phase = Phase.Downloading(p) } }
                    phase = Phase.Verifying
                    val ok = withContext(Dispatchers.IO) { AppUpdates.matches(part, build.sha256) }
                    if (!ok) { part.delete(); phase = Phase.Failed(AppUpdates.BROKEN); return@launch }
                    part.renameTo(file)
                }
                if (!canInstall()) { phase = Phase.NeedsPermission; return@launch }
                phase = Phase.Installing
                withContext(Dispatchers.IO) { install(file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                phase = Phase.Failed(if (e is ApiError.Refused) e.text else AppUpdates.DOWNLOAD_FAILED)
            } catch (e: Exception) {
                phase = Phase.Failed(AppUpdates.INSTALL_FAILED)
            }
        }
    }

    fun dismissProblem() { if (phase is Phase.Failed || phase is Phase.NeedsPermission) phase = Phase.Idle }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("ax-kit.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName), flags)
            session.commit(pending.intentSender)
        }
    }

    /** What the installer said, when it did not ask the owner and did not replace this app. */
    internal fun installFinished(status: Int) {
        phase = when (status) {
            PackageInstaller.STATUS_SUCCESS -> Phase.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> Phase.Idle // the owner said 취소
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Phase.Failed(AppUpdates.SIGNED_DIFFERENTLY)
            else -> Phase.Failed(AppUpdates.INSTALL_FAILED)
        }
    }

    private fun folder() = File(context.cacheDir, "updates").apply { mkdirs() }
    private fun fileName(b: AppUpdatesView.Build) = "ax-kit-${b.build}.apk"

    companion object {
        /** The receiver below reaches the live updater through this (one per process). */
        @Volatile internal var current: AppUpdater? = null
    }
}

/** The system installer's answer: ask the owner to confirm, or report how it ended. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val updater = AppUpdater.current ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { updater.installFinished(status) }
    }
}
