package com.felipe.compiladores.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.ui.components.Chip
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.Palette
import com.felipe.compiladores.ui.theme.Palettes
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

@Composable
fun SettingsScreen(game: GameState, onBack: () -> Unit) {
    val settings = game.profile.settings
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Aparência", "Cores confortáveis para estudar por horas", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MascotSays(
                "Escolha um tema que *descanse a vista*. Nada de preto e branco puros: fundos quentes (sépia), verdes (Everforest, verde de leitura) ou o escuro atenuado do GitHub.",
                Mood.HAPPY,
            )
            Palettes.all.forEach { p ->
                ThemeCard(p, selected = p.id == settings.themeId) { game.updateSettings { it.copy(themeId = p.id) } }
            }
            Panel(title = "Tamanho do texto", accent = Primary) {
                ChipsRow {
                    listOf(0.9f to "Menor", 1f to "Normal", 1.15f to "Grande", 1.3f to "Enorme").forEach { (v, label) ->
                        Chip(label, settings.textScale == v, { game.updateSettings { it.copy(textScale = v) } })
                    }
                }
            }
            Panel(title = "Movimento", accent = Primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduzir animações", color = TextMain, style = MaterialTheme.typography.titleSmall)
                        Text("Mostra as ilustrações já prontas, sem movimento.", color = TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(settings.reduceMotion, { v -> game.updateSettings { it.copy(reduceMotion = v) } }, Modifier.testTag("motion"))
                }
            }
            Panel(title = "Dicas contra o cansaço visual", accent = Primary) {
                listOf(
                    "Regra 20-20-20: a cada 20 minutos, olhe por 20 segundos para algo a uns 6 metros.",
                    "À noite, prefira o Noturno âmbar: quase sem luz azul.",
                    "De dia, fundos sépia ou verde-claro brilham menos que o branco puro.",
                    "Pisque de propósito: na tela a gente pisca bem menos.",
                ).forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("• ", color = Primary)
                        Text(it, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Prévia do tema desenhada com as cores dele (independente do tema atual). */
@Composable
private fun ThemeCard(p: Palette, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .testTag("theme:${p.id}")
            .background(p.background, RoundedCornerShape(18.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) p.primary else p.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = p.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text("✓ em uso", color = p.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(p.description, color = p.textDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.background(p.surface, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = p.nonterminal, fontWeight = FontWeight.Bold)) { append("S") }
                        withStyle(SpanStyle(color = p.textDim)) { append(" → ") }
                        withStyle(SpanStyle(color = p.terminal)) { append("a ") }
                        withStyle(SpanStyle(color = p.nonterminal, fontWeight = FontWeight.Bold)) { append("S") }
                        withStyle(SpanStyle(color = p.terminal)) { append(" b") }
                        withStyle(SpanStyle(color = p.textDim)) { append(" | ") }
                        withStyle(SpanStyle(color = p.special)) { append("ε") }
                    },
                    fontFamily = Mono, fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            p.accents.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { Box(Modifier.size(16.dp).background(it, CircleShape)) }
                }
            }
        }
    }
}
