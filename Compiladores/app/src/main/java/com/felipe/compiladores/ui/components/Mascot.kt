package com.felipe.compiladores.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.ui.theme.Background
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.ThemeState
import kotlin.math.PI
import kotlin.math.sin

/**
 * Parsy, o mascote: um robozinho-parser com tela no rosto. Pisca, balança e muda
 * de expressão conforme o humor — emoção ajuda a memória (princípio do Head First).
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val still = ThemeState.reduceMotion
    var blink = 1f
    var bob = 0f
    if (!still) {
        val transition = rememberInfiniteTransition()
        blink = transition.animateFloat(
            1f, 1f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 3600
                    1f at 3200
                    0.1f at 3320
                    1f at 3440
                },
            ),
        ).value
        bob = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(if (mood == Mood.CELEBRATE) 700 else 2200, easing = LinearEasing))).value
    }
    val body = Primary
    val face = Background
    val bulb = Tertiary
    val cheek = Secondary
    Canvas(
        modifier.size(size).graphicsLayer {
            if (!still) {
                val amp = if (mood == Mood.CELEBRATE) 6f else 2f
                translationY = -amp * density * kotlin.math.abs(sin(bob * 2f * PI.toFloat()))
            }
        },
    ) {
        val s = this.size.minDimension
        val eyeOpen = if (still) 1f else blink
        // antena
        drawLine(body, Offset(s * 0.5f, s * 0.22f), Offset(s * 0.5f, s * 0.08f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
        drawCircle(bulb, radius = s * 0.055f, center = Offset(s * 0.5f, s * 0.07f))
        drawCircle(bulb.copy(alpha = 0.25f), radius = s * 0.1f, center = Offset(s * 0.5f, s * 0.07f))
        // cabeça
        drawRoundRect(SurfaceHigh, Offset(s * 0.1f, s * 0.22f), Size(s * 0.8f, s * 0.62f), CornerRadius(s * 0.18f))
        drawRoundRect(body, Offset(s * 0.1f, s * 0.22f), Size(s * 0.8f, s * 0.62f), CornerRadius(s * 0.18f), style = Stroke(s * 0.045f))
        // orelhas / parafusos
        drawCircle(body, radius = s * 0.045f, center = Offset(s * 0.08f, s * 0.53f))
        drawCircle(body, radius = s * 0.045f, center = Offset(s * 0.92f, s * 0.53f))
        // tela do rosto
        drawRoundRect(face, Offset(s * 0.2f, s * 0.32f), Size(s * 0.6f, s * 0.42f), CornerRadius(s * 0.1f))
        // olhos
        val lookUp = if (mood == Mood.THINK) -s * 0.03f else 0f
        val lookSide = if (mood == Mood.THINK) s * 0.03f else 0f
        val eyeW = s * 0.1f
        val eyeH = s * 0.13f * eyeOpen * (if (mood == Mood.SURPRISED) 1.25f else 1f)
        for (cx in listOf(s * 0.37f, s * 0.63f)) {
            drawOval(body, Offset(cx - eyeW / 2 + lookSide, s * 0.46f - eyeH / 2 + lookUp), Size(eyeW, eyeH.coerceAtLeast(s * 0.012f)))
        }
        // bochechas
        if (mood == Mood.HAPPY || mood == Mood.CELEBRATE) {
            drawCircle(cheek.copy(alpha = 0.35f), radius = s * 0.045f, center = Offset(s * 0.27f, s * 0.6f))
            drawCircle(cheek.copy(alpha = 0.35f), radius = s * 0.045f, center = Offset(s * 0.73f, s * 0.6f))
        }
        // boca
        val stroke = Stroke(s * 0.035f, cap = StrokeCap.Round)
        when (mood) {
            Mood.HAPPY -> drawArc(body, 20f, 140f, false, Offset(s * 0.4f, s * 0.5f), Size(s * 0.2f, s * 0.14f), style = stroke)
            Mood.CELEBRATE -> drawArc(body, 0f, 180f, true, Offset(s * 0.38f, s * 0.53f), Size(s * 0.24f, s * 0.16f))
            Mood.SAD -> drawArc(body, 200f, 140f, false, Offset(s * 0.4f, s * 0.6f), Size(s * 0.2f, s * 0.12f), style = stroke)
            Mood.SURPRISED -> drawCircle(body, radius = s * 0.045f, center = Offset(s * 0.5f, s * 0.64f), style = stroke)
            Mood.THINK -> drawLine(body, Offset(s * 0.43f, s * 0.64f), Offset(s * 0.58f, s * 0.62f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
        }
        // pezinhos
        drawRoundRect(body, Offset(s * 0.28f, s * 0.86f), Size(s * 0.14f, s * 0.07f), CornerRadius(s * 0.03f))
        drawRoundRect(body, Offset(s * 0.58f, s * 0.86f), Size(s * 0.14f, s * 0.07f), CornerRadius(s * 0.03f))
        // balão de pensamento
        if (mood == Mood.THINK) {
            drawCircle(bulb.copy(alpha = 0.8f), radius = s * 0.03f, center = Offset(s * 0.9f, s * 0.2f))
            drawCircle(bulb.copy(alpha = 0.6f), radius = s * 0.02f, center = Offset(s * 0.84f, s * 0.26f))
        }
        if (mood == Mood.SURPRISED) {
            val p = Path().apply { addRect(Rect(Offset(s * 0.9f, s * 0.08f), Size(s * 0.03f, s * 0.12f))) }
            drawPath(p, bulb)
            drawCircle(bulb, radius = s * 0.018f, center = Offset(s * 0.915f, s * 0.24f))
        }
    }
}

/**
 * Texto com marcação leve: *destaque* vira negrito colorido e `símbolos` viram
 * gramática colorida (não-terminal, terminal, ε).
 */
fun richText(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val close = if (c == '*' || c == '`') text.indexOf(c, i + 1) else -1
        if (close > i) {
            val inner = text.substring(i + 1, close)
            if (c == '*') {
                withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.Bold)) { append(inner) }
            } else {
                inner.split(" ").forEachIndexed { k, tok ->
                    if (k > 0) append(" ")
                    val color = when {
                        tok == "→" || tok == "|" || tok == "⇒" -> Outline
                        tok.isEmpty() -> TextMain
                        else -> symbolColor(null, tok)
                    }
                    withStyle(SpanStyle(color = color, fontFamily = Mono, fontWeight = FontWeight.SemiBold)) { append(tok) }
                }
            }
            i = close + 1
        } else {
            append(c)
            i++
        }
    }
}

/** O mascote falando, com balão de fala. */
@Composable
fun MascotSays(text: String, mood: Mood = Mood.HAPPY, modifier: Modifier = Modifier, mascotSize: Dp = 60.dp) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Mascot(mood, size = mascotSize)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f)
                .padding(top = 6.dp)
                .appear(text)
                .background(SurfaceHigh, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .border(1.dp, Outline, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(richText(text), style = MaterialTheme.typography.bodyLarge, color = TextMain)
        }
    }
}
