package com.felipe.compiladores.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import com.felipe.compiladores.ui.theme.ThemeState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Entrada suave (cresce e aparece) na primeira vez que [key] é exibido. */
@Composable
fun Modifier.appear(key: Any? = Unit, delayMs: Int = 0): Modifier {
    if (ThemeState.reduceMotion) return this
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        anim.animateTo(1f, tween(durationMillis = 260, delayMillis = delayMs))
    }
    return this.graphicsLayer {
        val v = anim.value
        alpha = v
        scaleX = 0.7f + 0.3f * v
        scaleY = 0.7f + 0.3f * v
    }
}

/** Treme na horizontal toda vez que [trigger] muda (erro!). */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !ThemeState.reduceMotion) {
            offset.snapTo(0f)
            offset.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    -14f at 50
                    12f at 120
                    -9f at 190
                    7f at 260
                    -3f at 340
                },
            )
        }
    }
    return this.graphicsLayer { translationX = offset.value * density }
}

/** Pulsa suavemente (para chamar atenção para o próximo passo). */
@Composable
fun Modifier.pulse(enabled: Boolean = true): Modifier {
    if (!enabled || ThemeState.reduceMotion) return this
    val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)))
    return this.graphicsLayer {
        val s = 1f + 0.04f * sin(t * 2f * Math.PI.toFloat())
        scaleX = s
        scaleY = s
    }
}

/**
 * Passo atual de uma animação em laço com [count] quadros (e uma pausa no último).
 * Usa transição infinita, que os testes de interface sabem ignorar.
 */
@Composable
fun rememberLoopStep(count: Int, periodMs: Int = 1100, holdLast: Int = 2): Int {
    if (ThemeState.reduceMotion || count <= 1) return (count - 1).coerceAtLeast(0)
    val total = count + holdLast
    val t by rememberInfiniteTransition().animateFloat(
        0f, total.toFloat(), infiniteRepeatable(tween(total * periodMs, easing = LinearEasing)),
    )
    return t.toInt().coerceIn(0, count - 1)
}

/** Explosão de confete (uma vez por [trigger]). */
@Composable
fun Confetti(trigger: Any, modifier: Modifier = Modifier.fillMaxSize()) {
    if (ThemeState.reduceMotion) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val colors = ThemeState.palette.accents
    val parts = remember(trigger) {
        val r = Random(trigger.hashCode())
        List(70) {
            val angle = (-Math.PI / 2 + (r.nextDouble() - 0.5) * 2.2).toFloat()
            val speed = 0.55f + r.nextFloat() * 0.7f
            Triple(angle, speed, r.nextInt(colors.size)) to r.nextFloat() * 360f
        }
    }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height * 0.35f)
        for ((p, rot) in parts) {
            val (angle, speed, ci) = p
            val dx = cos(angle) * speed * size.width * 0.6f * t
            val dy = sin(angle) * speed * size.height * 0.55f * t + 0.9f * size.height * t * t
            val pos = origin + Offset(dx, dy)
            rotate(rot + 540f * t, pos) {
                drawRect(
                    colors[ci].copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    topLeft = pos - Offset(6f, 3f),
                    size = Size(12f, 6f),
                )
            }
        }
    }
}

/** Mola padrão para pequenas animações de UI. */
fun softSpring() = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)
