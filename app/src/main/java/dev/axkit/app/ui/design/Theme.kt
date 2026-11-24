package dev.axkit.app.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.axkit.app.R

/** The iOS system colours the app is drawn with (grouped page, white cards, the ax-kit blue accent). */
@Immutable
data class AxColors(
    val accent: Color,
    val page: Color,
    val card: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val fill: Color,
    val dark: Boolean,
)

val LightAx = AxColors(
    accent = Color(0xFF2F6BFF), page = Color(0xFFF2F2F7), card = Color(0xFFFFFFFF), label = Color(0xFF000000),
    secondary = Color(0x993C3C43), tertiary = Color(0x4D3C3C43), separator = Color(0x4A3C3C43), fill = Color(0x14767680), dark = false,
)
val DarkAx = AxColors(
    accent = Color(0xFF7AA2FF), page = Color(0xFF000000), card = Color(0xFF1C1C1E), label = Color(0xFFFFFFFF),
    secondary = Color(0x99EBEBF5), tertiary = Color(0x4DEBEBF5), separator = Color(0x99545458), fill = Color(0x3D767680), dark = true,
)

val LocalAx = staticCompositionLocalOf { LightAx }

/** iOS system tints. */
object SystemColor {
    val blue = Color(0xFF007AFF); val green = Color(0xFF34C759); val orange = Color(0xFFFF9500); val red = Color(0xFFFF3B30)
    val pink = Color(0xFFFF2D55); val purple = Color(0xFFAF52DE); val teal = Color(0xFF30B0C7); val indigo = Color(0xFF5856D6)
    val brown = Color(0xFFA2845E); val yellow = Color(0xFFFFCC00); val mint = Color(0xFF00C7BE); val cyan = Color(0xFF32ADE6); val gray = Color(0xFF8E8E93)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** iOS Dynamic Type sizes (large): largeTitle 34, title2 22, title3 20, headline 17 semibold, body 17, subheadline 15, footnote 13, caption 12. */
object AxType {
    private fun s(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.3).toInt()) =
        TextStyle(fontFamily = Pretendard, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)
    val largeTitle = s(34, FontWeight.Bold, 41)
    val title = s(28, FontWeight.Bold)
    val title2 = s(22, FontWeight.Bold)
    val title3 = s(20, FontWeight.SemiBold)
    val headline = s(17, FontWeight.SemiBold, 22)
    val body = s(17, line = 23)
    val bodyBold = s(17, FontWeight.SemiBold, 23)
    val callout = s(16)
    val subheadline = s(15, line = 20)
    val subheadlineBold = s(15, FontWeight.SemiBold, 20)
    val footnote = s(13, line = 18)
    val footnoteBold = s(13, FontWeight.SemiBold, 18)
    val caption = s(12, line = 16)
    val captionBold = s(12, FontWeight.SemiBold, 16)
    val caption2 = s(11, line = 13)
}

object Theme {
    val corner = 20.dp
    val ax: AxColors @Composable get() = LocalAx.current
    /** The owner's own lines: a light tint, so the bots' words stay in front. */
    val ownerBubble: Color @Composable get() = LocalAx.current.accent.copy(alpha = 0.14f)
    /** A team's work log line: quieter than a spoken line (iOS tertiarySystemFill). */
    val statusBubble: Color @Composable get() = if (LocalAx.current.dark) Color(0x3D767680) else Color(0x1F767680)
    val assistantBubble: Color @Composable get() = LocalAx.current.card

    /** Used by the old 문서 universe screens only (iOS dropped it with the 3D view). */
    val layerPalette = listOf(SystemColor.blue, SystemColor.orange, SystemColor.pink, SystemColor.green, SystemColor.purple, SystemColor.teal, SystemColor.indigo, SystemColor.brown)
    fun layerColor(index: Int) = layerPalette[((index % layerPalette.size) + layerPalette.size) % layerPalette.size]

    fun effectColor(effects: List<String>): Color = when {
        effects.contains("pay") -> SystemColor.orange
        effects.contains("delete") -> SystemColor.red
        effects.contains("send") -> SystemColor.blue
        else -> SystemColor.green
    }
}

@Composable
fun AxTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ax = if (dark) DarkAx else LightAx
    val scheme = if (dark) darkColorScheme(primary = ax.accent, background = ax.page, surface = ax.card, onSurface = ax.label, onBackground = ax.label, surfaceContainer = ax.card)
    else lightColorScheme(primary = ax.accent, background = ax.page, surface = ax.card, onSurface = ax.label, onBackground = ax.label, surfaceContainer = ax.card)
    val t = Typography(
        bodyLarge = AxType.body, bodyMedium = AxType.subheadline, bodySmall = AxType.footnote,
        titleLarge = AxType.title3, titleMedium = AxType.headline, titleSmall = AxType.subheadlineBold,
        labelLarge = AxType.subheadlineBold, labelMedium = AxType.caption, labelSmall = AxType.caption2,
        headlineLarge = AxType.largeTitle, headlineMedium = AxType.title, headlineSmall = AxType.title2,
    )
    CompositionLocalProvider(LocalAx provides ax) {
        MaterialTheme(colorScheme = scheme, typography = t, content = content)
    }
}
