package com.felipe.compiladores.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.Production
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.DotColor
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.OnAccent
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Scrim
import com.felipe.compiladores.ui.theme.SpecialColor
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.TerminalColor
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.Warning

// ------------------------------------------------------------------ botão "voltar" do sistema

/** A plataforma fornece o tratamento do botão voltar (BackHandler no Android). */
val LocalBackHandler = staticCompositionLocalOf<@Composable (Boolean, () -> Unit) -> Unit> { { _, _ -> } }

@Composable
fun SystemBack(enabled: Boolean = true, onBack: () -> Unit) {
    LocalBackHandler.current(enabled, onBack)
}

// ------------------------------------------------------------------ símbolos coloridos

fun symbolColor(g: Grammar?, s: String): Color = when {
    s == EPS || s == END -> SpecialColor
    s == "•" -> DotColor
    g != null && g.isNonterminal(s) -> NonterminalColor
    g == null && s.first().isUpperCase() -> NonterminalColor
    else -> TerminalColor
}

fun symbolsText(symbols: List<String>, g: Grammar?, emptyAsEps: Boolean = true): AnnotatedString = buildAnnotatedString {
    if (symbols.isEmpty() && emptyAsEps) {
        withStyle(SpanStyle(color = SpecialColor)) { append(EPS) }
        return@buildAnnotatedString
    }
    symbols.forEachIndexed { i, s ->
        if (i > 0) append(" ")
        withStyle(SpanStyle(color = symbolColor(g, s), fontWeight = if (g?.isNonterminal(s) == true) FontWeight.Bold else FontWeight.Normal)) {
            append(s)
        }
    }
}

fun productionText(p: Production, g: Grammar, numbered: Boolean = false): AnnotatedString = buildAnnotatedString {
    if (numbered) withStyle(SpanStyle(color = TextDim)) { append("(${p.id}) ") }
    withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append(p.lhs) }
    withStyle(SpanStyle(color = TextDim)) { append(" → ") }
    append(symbolsText(p.rhs, g))
}

fun setAnnotated(set: Collection<String>, g: Grammar): AnnotatedString = buildAnnotatedString {
    if (set.isEmpty()) { withStyle(SpanStyle(color = TextDim)) { append("∅") }; return@buildAnnotatedString }
    withStyle(SpanStyle(color = TextDim)) { append("{ ") }
    g.sortTerminals(set).forEachIndexed { i, s ->
        if (i > 0) withStyle(SpanStyle(color = TextDim)) { append(", ") }
        withStyle(SpanStyle(color = symbolColor(g, s))) { append(s) }
    }
    withStyle(SpanStyle(color = TextDim)) { append(" }") }
}

@Composable
fun MonoText(text: AnnotatedString, modifier: Modifier = Modifier, size: Int = 15) {
    Text(text, modifier = modifier, fontFamily = Mono, fontSize = size.sp, lineHeight = (size + 7).sp)
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, size: Int = 15, color: Color = TextMain) {
    Text(text, modifier = modifier, fontFamily = Mono, fontSize = size.sp, lineHeight = (size + 7).sp, color = color)
}

// ------------------------------------------------------------------ painéis e cartões

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = Primary,
    collapsible: Boolean = false,
    initiallyOpen: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().then(if (collapsible) Modifier.clickable { open = !open } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(accent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, color = accent, modifier = Modifier.weight(1f))
                    if (collapsible) Text(if (open) "▲" else "▼", color = TextDim, fontSize = 12.sp)
                }
                if (open) Spacer(Modifier.height(8.dp))
            }
            if (open || title == null) content()
        }
    }
}

@Composable
fun GrammarCard(
    grammar: Grammar,
    modifier: Modifier = Modifier,
    title: String = "Gramática",
    numbered: Boolean = false,
    collapsible: Boolean = true,
    initiallyOpen: Boolean = true,
    highlight: Int? = null,
) {
    Panel(modifier, title, NonterminalColor, collapsible, initiallyOpen) {
        if (numbered) {
            grammar.productions.forEach { p ->
                val bg = if (p.id == highlight) Modifier.background(Primary.copy(alpha = 0.18f), RoundedCornerShape(6.dp)) else Modifier
                MonoText(productionText(p, grammar, numbered = true), bg.padding(horizontal = 4.dp, vertical = 1.dp))
            }
        } else {
            grammar.groupedLines().forEach { (nt, ps) ->
                MonoText(buildAnnotatedString {
                    withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append(nt) }
                    withStyle(SpanStyle(color = TextDim)) { append(" → ") }
                    ps.forEachIndexed { i, p ->
                        if (i > 0) withStyle(SpanStyle(color = TextDim)) { append("  |  ") }
                        append(symbolsText(p.rhs, grammar))
                    }
                })
            }
        }
    }
}

// ------------------------------------------------------------------ barra superior

@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(44.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", fontSize = 24.sp, color = TextMain) }
        } else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextMain, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TextDim, maxLines = 1)
        }
        actions()
    }
}

// ------------------------------------------------------------------ botões e chips

enum class ButtonStyle { FILLED, OUTLINED, GHOST }

@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Primary,
    style: ButtonStyle = ButtonStyle.FILLED,
    mono: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when (style) {
        ButtonStyle.FILLED -> color
        ButtonStyle.OUTLINED -> color.copy(alpha = 0.10f)
        ButtonStyle.GHOST -> Color.Transparent
    }
    val fg = if (style == ButtonStyle.FILLED) OnAccent else color
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = 44.dp)
            .background(bg, shape)
            .then(if (style == ButtonStyle.OUTLINED) Modifier.border(1.dp, color.copy(alpha = 0.7f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            fontFamily = if (mono) Mono else null, fontSize = 15.sp,
        )
    }
}

@Composable
fun GameButton(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Primary,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = 44.dp)
            .background(color.copy(alpha = 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) { MonoText(text) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Primary, enabled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 38.dp)
            .background(if (selected) color.copy(alpha = 0.22f) else SurfaceHigh, shape)
            .border(1.dp, if (selected) color else Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) color else TextMain, style = MaterialTheme.typography.labelLarge)
    }
}

enum class CellStatus { NEUTRAL, OK, WRONG, CONFLICT, HINT }

/** Célula quadrada de tabela, clicável. */
@Composable
fun GridCell(
    text: AnnotatedString,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 40.dp,
    status: CellStatus = CellStatus.NEUTRAL,
    tag: String? = null,
) {
    val border = when (status) {
        CellStatus.OK -> Success
        CellStatus.WRONG -> Danger
        CellStatus.CONFLICT -> Warning
        CellStatus.HINT -> Tertiary
        CellStatus.NEUTRAL -> if (selected) Primary else Outline
    }
    val bg by animateColorAsState(
        when {
            status == CellStatus.CONFLICT -> Warning.copy(alpha = 0.18f)
            selected -> Primary.copy(alpha = 0.22f)
            else -> SurfaceHigh
        },
    )
    Box(
        modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(2.dp)
            .size(width, height)
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (status == CellStatus.NEUTRAL && !selected) 1.dp else 2.dp, border, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Mono, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun HeaderCell(text: AnnotatedString, width: Dp = 44.dp, height: Dp = 32.dp) {
    Box(Modifier.padding(2.dp).size(width, height), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Mono, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

// ------------------------------------------------------------------ metacognição: confiança

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfidencePicker(selected: Confidence?, onSelect: (Confidence) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Quão confiante você está?", style = MaterialTheme.typography.labelMedium, color = TextDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Confidence.entries.forEach { c ->
                Chip("${c.emoji} ${c.label}", selected == c, { onSelect(c) }, color = Tertiary)
            }
        }
    }
}

/** Confiança + botão de verificar: você precisa apostar antes de ver a resposta. */
@Composable
fun CheckBar(
    confidence: Confidence?,
    onConfidence: (Confidence) -> Unit,
    onCheck: () -> Unit,
    enabled: Boolean = true,
    label: String = "Verificar",
) {
    Column(Modifier.fillMaxWidth()) {
        ConfidencePicker(confidence, onConfidence)
        Spacer(Modifier.height(10.dp))
        GameButton(
            if (confidence == null) "Aposte sua confiança para verificar" else label,
            onCheck, Modifier.fillMaxWidth(), enabled = enabled && confidence != null,
        )
    }
}

// ------------------------------------------------------------------ feedback

enum class FeedbackKind(val icon: String) {
    GOOD("✓"), BAD("✗"), INFO("ℹ"), WARN("!");

    val color: Color
        get() = when (this) {
            GOOD -> Success
            BAD -> Danger
            INFO -> Primary
            WARN -> Tertiary
        }
}

@Composable
fun Feedback(kind: FeedbackKind, text: String, modifier: Modifier = Modifier, title: String? = null) {
    Row(
        modifier.fillMaxWidth()
            .appear(text)
            .background(kind.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .border(1.dp, kind.color.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Mascot(
            when (kind) {
                FeedbackKind.GOOD -> Mood.HAPPY
                FeedbackKind.BAD -> Mood.SAD
                FeedbackKind.INFO -> Mood.THINK
                FeedbackKind.WARN -> Mood.SURPRISED
            },
            size = 38.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, color = kind.color, style = MaterialTheme.typography.titleSmall)
            Text(text, color = TextMain, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ------------------------------------------------------------------ diversos

@Composable
fun HLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Outline))
}

@Composable
fun Stars(count: Int, max: Int = 3, size: Int = 18) {
    Row {
        repeat(max) { i ->
            Text(if (i < count) "★" else "☆", color = if (i < count) Tertiary else Outline, fontSize = size.sp)
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = Primary, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).background(SurfaceHigh, RoundedCornerShape(50))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(color, RoundedCornerShape(50)))
    }
}

@Composable
fun Tag(text: String, color: Color = Primary) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Diálogo modal desenhado sobre a tela (funciona igual em qualquer plataforma). */
@Composable
fun BoxScope.Overlay(visible: Boolean, onDismiss: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    if (onDismiss != null) SystemBack(enabled = true) { onDismiss() }
    Box(
        Modifier.fillMaxSize()
            .background(Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.padding(20.dp).widthIn(max = 520.dp).fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Outline),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
