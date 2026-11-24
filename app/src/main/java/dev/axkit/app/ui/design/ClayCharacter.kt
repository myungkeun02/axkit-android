package dev.axkit.app.ui.design

import android.graphics.BlurMaskFilter
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import dev.axkit.app.LocalModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** false: clay faces are drawn still (the room list hidden under the chat). */
val LocalAvatarMotion = androidx.compose.runtime.compositionLocalOf { true }

/** A bot's face: the Mac's character picture when it sends one, otherwise a clay figure drawn here. */
@Composable
fun BotAvatar(name: String, size: Dp = 40.dp, avatar: String? = null, status: String? = null, modifier: Modifier = Modifier) {
    val model = LocalModel.current
    var image by remember(avatar) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatar) { image = model.avatarImage(avatar)?.asImageBitmap() }
    Box(modifier.size(size).clearAndSetSemantics { }) {
        val img = image
        if (img != null) {
            Image(img, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            val ordinal = model.characterOrdinal(name)
            // Only a big face moves; the many small faces in lists stay still (status still shows in the eyes).
            // A working bot redrawn every frame kept the screen busy.
            ClayCharacter(seed = name, mood = Mood.from(status, name, ordinal), ordinal = ordinal, animated = size >= 72.dp, modifier = Modifier.size(size))
        }
    }
}

enum class Mood {
    Happy, Proud, Cheeky, Calm, Focused, Worried, Sleepy, Surprised;

    companion object {
        /** Status picks the face; idle bots get one of the cheerful faces by name. */
        fun from(status: String?, seed: String, ordinal: Int? = null): Mood = when (status) {
            "working" -> Focused
            "waiting" -> Worried
            "off" -> Sleepy
            else -> {
                val cheerful = listOf(Happy, Proud, Cheeky, Calm, Surprised)
                cheerful[((ordinal?.let { it * 2 }?.toLong() ?: ClayHash.hash(seed + "mood")) % cheerful.size).toInt()]
            }
        }
    }
}

object ClayHash {
    /** FNV-1a with a final mix (same numbers as iOS), so similar names still land far apart. */
    fun hash(text: String): Long {
        var x = -0x340d631b7bdddcdbL
        for (b in text.toByteArray(Charsets.UTF_8)) x = (x xor (b.toLong() and 0xff)) * 0x100000001b3L
        x = x xor (x ushr 33); x *= -0xae502812aa7333L; x = x xor (x ushr 33)
        return java.lang.Long.remainderUnsigned(x, Int.MAX_VALUE.toLong())
    }
}

/** A little clay figure: a smooth matte body and two eyes whose shape shows how the bot is doing. Port of ClayCharacter.swift. */
@Composable
fun ClayCharacter(seed: String, mood: Mood, ordinal: Int? = null, animated: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val h = remember(seed) { ClayHash.hash("$seed#shape") }
    val dark = Theme.ax.dark
    val secondary = Theme.ax.secondary
    val measurer = rememberTextMeasurer()
    var t by remember { mutableLongStateOf(0L) }
    val still = reduceMotion || !animated || !LocalAvatarMotion.current
    if (!still) {
        // At most 30 redraws a second, like iOS TimelineView(.animation(minimumInterval: 1/30)).
        LaunchedEffect(Unit) { while (true) withFrameMillis { if (it - t >= 33) t = it } }
    }
    val colors = remember(seed, ordinal) { palette(seed, ordinal) }
    Canvas(modifier) {
        // A still face uses t = 1: eyes open (t = 0 lands on a blink).
        val time = if (still) 1.0 else t / 1000.0 + (h % 997) / 37.0
        drawFigure(time, h, mood, colors, dark, secondary, measurer)
    }
}

private data class ClayColors(val body: Color, val light: Color, val dark: Color)

private fun hsb(h: Double, s: Double, b: Double): Color = Color.hsv(((h * 360) % 360).toFloat(), s.toFloat(), b.toFloat())

/** Clear pastel toy colours; no browns or muddy yellows. */
private fun palette(seed: String, ordinal: Int?): ClayColors {
    val hues = listOf(0.97, 0.01, 0.14, 0.27, 0.42, 0.48, 0.55, 0.60, 0.70, 0.78, 0.86)
    val pick = ordinal?.let { (it * 4).toLong() } ?: ClayHash.hash("$seed#hue")
    val hue = hues[(pick % hues.size).toInt()]
    val sat = if (hue == 0.14) 0.55 else 0.48
    return ClayColors(hsb(hue, sat, 0.97), hsb(hue, 0.18, 1.0), hsb(hue, 0.62, 0.72))
}

/** A lumpy, bottom-heavy body: radius wobbles a little with angle, like hand-pressed clay. */
private fun blob(seed: Long, left: Float, top: Float, w: Float, hgt: Float): Path {
    val cx = left + w / 2; val cy = top + hgt / 2
    val rx = w / 2; val ry = hgt / 2
    val p1 = (seed % 628) / 100.0; val p2 = ((seed / 628) % 628) / 100.0
    val path = Path()
    val steps = 72
    for (i in 0..steps) {
        val a = i.toDouble() / steps * 2 * PI
        var r = 1 + 0.01 * sin(3 * a + p1) + 0.006 * sin(5 * a + p2)
        if (sin(a) > 0) r += 0.05 * sin(a) * abs(cos(a))
        val x = cx + (cos(a) * r).toFloat() * rx * 0.96f
        val y = cy + (sin(a) * r).toFloat() * ry * 0.96f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.blurredOval(color: Color, center: Offset, size: Size, blur: Float, rotation: Float = 0f) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply { this.color = color }
        if (blur > 0.5f) paint.asFrameworkPaint().maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        canvas.save()
        canvas.translate(center.x, center.y)
        canvas.rotate(rotation)
        canvas.drawOval(-size.width / 2, -size.height / 2, size.width / 2, size.height / 2, paint)
        canvas.restore()
    }
}

private fun DrawScope.drawFigure(t: Double, h: Long, mood: Mood, colors: ClayColors, dark: Boolean, secondary: Color, measurer: TextMeasurer) {
    val s = minOf(size.width, size.height)
    val ox = (size.width - s) / 2; val oy = (size.height - s) / 2
    translate(ox, oy) {
        // Motion: working hops, everyone else breathes slowly; sleepy sways.
        val hop = if (mood == Mood.Focused) abs(sin(t * 4.2)) else 0.0
        val breathe = when (mood) { Mood.Focused -> 0.0; Mood.Sleepy -> sin(t * 0.9) * 0.03; else -> sin(t * 1.6) * 0.015 }
        val sway = if (mood == Mood.Sleepy) sin(t * 0.9) * 4 else 0.0
        val squash = if (mood == Mood.Focused) (1 - hop) * 0.06 else 0.0

        // Ground shadow so it sits on the page like a real object.
        blurredOval(
            Color.Black.copy(alpha = if (dark) 0.35f else 0.12f), Offset(s / 2, s / 2 + s * 0.43f),
            Size(s * (0.62f - hop.toFloat() * 0.12f), s * 0.08f), s * 0.02f,
        )

        val pivot = Offset(s / 2, s)
        translate(0f, -hop.toFloat() * s * 0.1f) {
            rotate(sway.toFloat(), pivot) {
                scale((1 + squash + breathe).toFloat(), (1 - squash + breathe).toFloat(), pivot) {
                    val bw = s * 0.8f; val bh = s * 0.72f
                    val left = (s - bw) / 2; val top = (s - bh) / 2 + s * 0.04f
                    val body = blob(h, left, top, bw, bh)
                    drawPath(body, colors.body)
                    clipPath(body) {
                        // Matte shading: light from the top left, deeper clay underneath.
                        drawRect(Brush.radialGradient(listOf(colors.light.copy(alpha = 0.9f), Color.Transparent), Offset(left + bw * 0.32f, top + bh * 0.25f), s * 0.5f))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.dark.copy(alpha = 0.55f)), startY = top + bh / 2, endY = top + bh))
                    }
                    drawPath(body, colors.dark.copy(alpha = 0.3f), style = Stroke(s * 0.02f))
                    // Soft clay sheen: one wide highlight.
                    blurredOval(Color.White.copy(alpha = 0.55f), Offset(s / 2 - s * 0.18f, top + bh / 2 - s * 0.2f), Size(s * 0.22f, s * 0.1f), s * 0.02f, -28f)
                    eyes(s, t, mood, s / 2 + s * 0.02f)
                }
            }
        }
        if (mood == Mood.Sleepy) sleepZs(s, t, secondary, measurer)
    }
}

/** Just two eyes; the mood lives in their shape. */
private fun DrawScope.eyes(s: Float, t: Double, mood: Mood, cy: Float) {
    val ink = Color(0xFF262626)
    val blinking = (t % 3.7) < 0.13
    val look = if (mood == Mood.Focused) sin(t * 1.3).toFloat() * s * 0.025f else 0f
    val y = cy + if (mood == Mood.Worried) s * 0.02f else 0f
    val gap = s * 0.17f
    val eyeW = when (mood) { Mood.Happy, Mood.Proud, Mood.Sleepy, Mood.Calm -> s * 0.1f; Mood.Focused, Mood.Worried -> s * 0.07f; Mood.Surprised -> s * 0.09f; else -> s * 0.075f }
    val lx = s / 2 + look - gap / 2 - eyeW / 2
    val rx = s / 2 + look + gap / 2 + eyeW / 2
    eye(Offset(lx, y), s, ink, blinking, true, mood)
    eye(Offset(rx, y), s, ink, blinking, false, mood)
}

private fun DrawScope.eye(c: Offset, s: Float, ink: Color, blinking: Boolean, left: Boolean, mood: Mood) {
    val line = Stroke(width = s * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mood) {
        Mood.Happy, Mood.Proud -> {
            // ∩ smiling eyes
            val w = s * 0.1f; val hgt = s * 0.045f
            val p = Path().apply {
                moveTo(c.x - w / 2, c.y + hgt / 2)
                quadraticTo(c.x, c.y - hgt / 2 - hgt, c.x + w / 2, c.y + hgt / 2)
            }
            drawPath(p, ink, style = line)
        }
        Mood.Sleepy, Mood.Calm -> {
            val w = s * 0.1f; val hgt = s * 0.03f
            drawRoundRect(ink, Offset(c.x - w / 2, c.y - hgt / 2), Size(w, hgt), androidx.compose.ui.geometry.CornerRadius(hgt / 2))
        }
        Mood.Focused -> {
            // > <
            val w = s * 0.07f; val hgt = s * 0.08f
            val p = Path()
            if (left) { p.moveTo(c.x - w / 2, c.y - hgt / 2); p.lineTo(c.x + w / 2, c.y); p.lineTo(c.x - w / 2, c.y + hgt / 2) }
            else { p.moveTo(c.x + w / 2, c.y - hgt / 2); p.lineTo(c.x - w / 2, c.y); p.lineTo(c.x + w / 2, c.y + hgt / 2) }
            drawPath(p, ink, style = line)
        }
        Mood.Worried -> {
            val w = s * 0.07f; val hgt = s * 0.09f * if (blinking) 0.12f else 1f
            rotate(if (left) -12f else 12f, c) { drawOval(ink, Offset(c.x - w / 2, c.y - hgt / 2), Size(w, hgt)) }
        }
        else -> {
            val w = s * if (mood == Mood.Surprised) 0.09f else 0.075f
            val hgt = s * (if (mood == Mood.Surprised) 0.11f else 0.095f) * if (blinking) 0.12f else 1f
            drawOval(ink, Offset(c.x - w / 2, c.y - hgt / 2), Size(w, hgt))
        }
    }
}

/** Three small z's drift up from the head one after another and fade out. */
private fun DrawScope.sleepZs(s: Float, t: Double, color: Color, measurer: TextMeasurer) {
    for (i in 0 until 3) {
        val phase = ((t * 0.45 + i / 3.0) % 1.0).toFloat()
        val alpha = if (phase < 0.15f) phase / 0.15f else 1 - (phase - 0.15f) / 0.85f
        val fontPx = s * (0.16f + 0.08f * phase)
        val style = TextStyle(fontSize = (fontPx / density).sp, fontWeight = FontWeight.Black, color = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)))
        val x = s / 2 + s * (0.22f + 0.14f * phase) + sin(phase * 6.0).toFloat() * s * 0.02f
        val y = s / 2 - s * (0.2f + 0.32f * phase)
        val layout = measurer.measure("z", style)
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
    }
}
