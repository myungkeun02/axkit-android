package dev.axkit.core

import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/** §32 `GET views/app-updates`: the builds this Mac made itself, for paired apps (no store). */
@Serializable
data class AppUpdatesView(val android: Build? = null, val desktop: Build? = null) {
    @Serializable
    data class Build(
        /** The build number (the app repo's commit count); newer when bigger than this app's versionCode. */
        val build: Int = 0,
        val versionName: String? = null,
        val builtAt: IsoInstant? = null,
        /** Hex sha256 of the file; a download that doesn't match is thrown away. */
        val sha256: String = "",
        val size: Long? = null,
    )
}

object AppUpdates {
    const val DAY_MS = 24 * 3600 * 1000L

    /** The Android build to offer, or null when it isn't newer than `myBuild` or can't be checked. */
    fun offer(view: AppUpdatesView?, myBuild: Int): AppUpdatesView.Build? =
        view?.android?.takeIf { it.build > myBuild && isSha256(it.sha256) }

    /** On every app start, then once a day. */
    fun due(lastCheckMs: Long, nowMs: Long, startedNow: Boolean): Boolean = startedNow || nowMs - lastCheckMs >= DAY_MS || nowMs < lastCheckMs

    fun isSha256(hex: String) = hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expected: String) = sha256(file).equals(expected.trim(), ignoreCase = true)

    /** "새 버전이 있어요 · 1.4.0 (52)" */
    fun title(b: AppUpdatesView.Build): String = "새 버전이 있어요" + (b.versionName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + " (${b.build})"

    /** "23MB" */
    fun size(bytes: Long?): String? = bytes?.takeIf { it > 0 }?.let { if (it >= 1_000_000) "${(it + 500_000) / 1_000_000}MB" else "${(it + 500) / 1000}KB" }

    const val BROKEN = "받은 파일이 맥의 파일과 달라요. 지웠어요. 잠시 뒤 다시 해 주세요."
    const val DOWNLOAD_FAILED = "새 버전을 받지 못했어요. 맥과 연결돼 있는지 보고 다시 해 주세요."
    const val INSTALL_FAILED = "새 버전을 깔지 못했어요. 다시 해 주세요."
    const val NEEDS_PERMISSION = "처음 한 번만, 이 앱이 새 버전을 깔 수 있게 허용해 주세요. 설정에서 '이 출처 허용'을 켜고 돌아오세요."
    const val SIGNED_DIFFERENTLY = "지금 앱과 새 버전의 서명이 달라서 위에 깔 수 없어요. 지금 앱을 지우고 새로 깔아야 해요(연결은 다시 해야 해요)."
}
