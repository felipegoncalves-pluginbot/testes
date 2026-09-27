package com.felipe.compiladores.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.felipe.compiladores.game.Settings

/**
 * Uma paleta completa do jogo. As cores dos símbolos (não-terminal, terminal, ε/$)
 * têm o mesmo papel em todas as paletas, para o cérebro associar cor a significado.
 */
@Immutable
data class Palette(
    val id: String,
    val name: String,
    val description: String,
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val nonterminal: Color,
    val terminal: Color,
    val special: Color,
    val dot: Color,
    val onAccent: Color,
    val scrim: Color,
    val accents: List<Color>,
)

/**
 * Paletas de baixo cansaço visual: fundos sem preto/branco puros, contraste moderado
 * e tons quentes ou verdes (Everforest, GitHub Dimmed, Gruvbox, sépia, verde de leitura...).
 */
object Palettes {
    val EVERFOREST = Palette(
        "everforest", "Floresta (Everforest)", "Verde e quente, suave para longas sessões.", false,
        background = Color(0xFF2D353B), surface = Color(0xFF343F44), surfaceHigh = Color(0xFF3D484D),
        outline = Color(0xFF4F585E), text = Color(0xFFD3C6AA), textDim = Color(0xFF9DA9A0),
        primary = Color(0xFFA7C080), secondary = Color(0xFFD699B6), tertiary = Color(0xFFDBBC7F),
        success = Color(0xFF83C092), danger = Color(0xFFE67E80), warning = Color(0xFFE69875),
        nonterminal = Color(0xFF7FBBB3), terminal = Color(0xFFDBBC7F), special = Color(0xFFD699B6), dot = Color(0xFFA7C080),
        onAccent = Color(0xFF2D353B), scrim = Color(0xCC1E2326),
        accents = listOf(0xFFA7C080, 0xFF83C092, 0xFFD699B6, 0xFFDBBC7F, 0xFF7FBBB3, 0xFFE69875, 0xFFE67E80).map { Color(it) },
    )

    val GITHUB_DIMMED = Palette(
        "github", "GitHub Dimmed", "O escuro atenuado do GitHub: cinza-azulado, sem preto puro.", false,
        background = Color(0xFF22272E), surface = Color(0xFF2D333B), surfaceHigh = Color(0xFF373E47),
        outline = Color(0xFF444C56), text = Color(0xFFADBAC7), textDim = Color(0xFF768390),
        primary = Color(0xFF539BF5), secondary = Color(0xFFC96198), tertiary = Color(0xFFC69026),
        success = Color(0xFF57AB5A), danger = Color(0xFFE5534B), warning = Color(0xFFCC6B2C),
        nonterminal = Color(0xFF96D0FF), terminal = Color(0xFFDAAA3F), special = Color(0xFFDCBDFB), dot = Color(0xFF8DDB8C),
        onAccent = Color(0xFF1C2128), scrim = Color(0xCC1C2128),
        accents = listOf(0xFF539BF5, 0xFF57AB5A, 0xFF986EE2, 0xFFC69026, 0xFFC96198, 0xFFCC6B2C, 0xFF39C5CF).map { Color(it) },
    )

    val GRUVBOX = Palette(
        "gruvbox", "Gruvbox", "Retrô, terroso e quente.", false,
        background = Color(0xFF282828), surface = Color(0xFF32302F), surfaceHigh = Color(0xFF3C3836),
        outline = Color(0xFF504945), text = Color(0xFFEBDBB2), textDim = Color(0xFFA89984),
        primary = Color(0xFF8EC07C), secondary = Color(0xFFD3869B), tertiary = Color(0xFFFABD2F),
        success = Color(0xFFB8BB26), danger = Color(0xFFFB4934), warning = Color(0xFFFE8019),
        nonterminal = Color(0xFF83A598), terminal = Color(0xFFFABD2F), special = Color(0xFFD3869B), dot = Color(0xFFB8BB26),
        onAccent = Color(0xFF282828), scrim = Color(0xCC1D2021),
        accents = listOf(0xFF8EC07C, 0xFFFABD2F, 0xFFD3869B, 0xFF83A598, 0xFFFE8019, 0xFFB8BB26, 0xFFFB4934).map { Color(it) },
    )

    val AMBER_NIGHT = Palette(
        "ambar", "Noturno âmbar", "Sem luz azul: tons de âmbar para estudar à noite.", false,
        background = Color(0xFF1B1612), surface = Color(0xFF241D17), surfaceHigh = Color(0xFF30271F),
        outline = Color(0xFF4A3C2E), text = Color(0xFFE6CFAE), textDim = Color(0xFFA58E70),
        primary = Color(0xFFE0A458), secondary = Color(0xFFD9826B), tertiary = Color(0xFFE8C170),
        success = Color(0xFFB5B35C), danger = Color(0xFFE0735C), warning = Color(0xFFE39A4D),
        nonterminal = Color(0xFFF2C28B), terminal = Color(0xFFC9D17A), special = Color(0xFFE58E8E), dot = Color(0xFF9BCB8A),
        onAccent = Color(0xFF1B1612), scrim = Color(0xCC120E0B),
        accents = listOf(0xFFE0A458, 0xFFC9D17A, 0xFFD9826B, 0xFFE8C170, 0xFF9BCB8A, 0xFFE58E8E, 0xFFF2C28B).map { Color(it) },
    )

    val SEPIA = Palette(
        "sepia", "Sépia (papel)", "Papel envelhecido: cerca de 25% menos brilho que o branco.", true,
        background = Color(0xFFF4ECD8), surface = Color(0xFFFBF5E6), surfaceHigh = Color(0xFFEADFC5),
        outline = Color(0xFFD2C2A0), text = Color(0xFF4B3B2A), textDim = Color(0xFF7A6A55),
        primary = Color(0xFF8A5A2B), secondary = Color(0xFFA23E48), tertiary = Color(0xFFA0700A),
        success = Color(0xFF4F7A28), danger = Color(0xFFB3432B), warning = Color(0xFFC0661A),
        nonterminal = Color(0xFF2F5D7C), terminal = Color(0xFF94580A), special = Color(0xFF9E3D6F), dot = Color(0xFF4F7A28),
        onAccent = Color(0xFFFBF5E6), scrim = Color(0x994B3B2A),
        accents = listOf(0xFF8A5A2B, 0xFF4F7A28, 0xFFA23E48, 0xFFA0700A, 0xFF2F5D7C, 0xFFC0661A, 0xFF6B5B95).map { Color(it) },
    )

    val GREEN_READING = Palette(
        "verde", "Verde descanso", "Fundo verde-claro de leitura, associado a menos fadiga visual.", true,
        background = Color(0xFFCCE8CF), surface = Color(0xFFDDF0DE), surfaceHigh = Color(0xFFBFDDC3),
        outline = Color(0xFF9CC4A2), text = Color(0xFF22352A), textDim = Color(0xFF4D6655),
        primary = Color(0xFF2E7D4F), secondary = Color(0xFF8E3B6E), tertiary = Color(0xFF8A6D00),
        success = Color(0xFF2E7D4F), danger = Color(0xFFB23A3A), warning = Color(0xFFB35C00),
        nonterminal = Color(0xFF1F5F8B), terminal = Color(0xFF7A5200), special = Color(0xFF8E3B6E), dot = Color(0xFF0F7C7C),
        onAccent = Color(0xFFF1FAF1), scrim = Color(0x9922352A),
        accents = listOf(0xFF2E7D4F, 0xFF0F7C7C, 0xFF8E3B6E, 0xFF8A6D00, 0xFF1F5F8B, 0xFFB35C00, 0xFF5B5EA6).map { Color(it) },
    )

    val SOLARIZED_LIGHT = Palette(
        "solarized", "Solarized claro", "Creme com cores de contraste calibrado.", true,
        background = Color(0xFFFDF6E3), surface = Color(0xFFFFFBEF), surfaceHigh = Color(0xFFEEE8D5),
        outline = Color(0xFFD3CBB7), text = Color(0xFF586E75), textDim = Color(0xFF839496),
        primary = Color(0xFF2AA198), secondary = Color(0xFFD33682), tertiary = Color(0xFFB58900),
        success = Color(0xFF859900), danger = Color(0xFFDC322F), warning = Color(0xFFCB4B16),
        nonterminal = Color(0xFF268BD2), terminal = Color(0xFFB58900), special = Color(0xFFD33682), dot = Color(0xFF859900),
        onAccent = Color(0xFFFDF6E3), scrim = Color(0x99586E75),
        accents = listOf(0xFF2AA198, 0xFF859900, 0xFFD33682, 0xFFB58900, 0xFF268BD2, 0xFFCB4B16, 0xFF6C71C4).map { Color(it) },
    )

    val all = listOf(EVERFOREST, GITHUB_DIMMED, GRUVBOX, AMBER_NIGHT, SEPIA, GREEN_READING, SOLARIZED_LIGHT)

    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: EVERFOREST
}

/** Configurações visuais em uso. As cores abaixo leem daqui, então trocar o tema redesenha tudo. */
object ThemeState {
    var settings by mutableStateOf(Settings())
    val palette: Palette get() = Palettes.byId(settings.themeId)
    val reduceMotion: Boolean get() = settings.reduceMotion
}

val Background: Color get() = ThemeState.palette.background
val Surface: Color get() = ThemeState.palette.surface
val SurfaceHigh: Color get() = ThemeState.palette.surfaceHigh
val Outline: Color get() = ThemeState.palette.outline
val TextMain: Color get() = ThemeState.palette.text
val TextDim: Color get() = ThemeState.palette.textDim

val Primary: Color get() = ThemeState.palette.primary
val Secondary: Color get() = ThemeState.palette.secondary
val Tertiary: Color get() = ThemeState.palette.tertiary
val Success: Color get() = ThemeState.palette.success
val Danger: Color get() = ThemeState.palette.danger
val Warning: Color get() = ThemeState.palette.warning
val OnAccent: Color get() = ThemeState.palette.onAccent
val Scrim: Color get() = ThemeState.palette.scrim

// Cores dos símbolos: sempre o mesmo papel em todo o jogo.
val NonterminalColor: Color get() = ThemeState.palette.nonterminal
val TerminalColor: Color get() = ThemeState.palette.terminal
val SpecialColor: Color get() = ThemeState.palette.special
val DotColor: Color get() = ThemeState.palette.dot

fun accentColor(index: Int): Color = ThemeState.palette.accents.let { it[index.mod(it.size)] }
