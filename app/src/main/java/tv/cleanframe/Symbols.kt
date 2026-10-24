@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package tv.cleanframe

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Text

object Symbols {
    const val Settings = "\uE8B8"
    const val Apps = "\uE9B0"
    const val Image = "\uE3F4"
    const val Sunny = "\uE81A"
    const val PartlyCloudy = "\uF172"
    const val Cloud = "\uF15C"
    const val Fog = "\uE818"
    const val Rain = "\uF176"
    const val Snow = "\uE2CD"
    const val Storm = "\uEBDB"
}

private val MaterialSymbols = FontFamily(
    Font(
        R.font.material_symbols,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("FILL", 0f),
            FontVariation.Setting("GRAD", 0f),
            FontVariation.Setting("opsz", 24f),
        ),
    ),
)

fun weatherSymbol(code: Int): String = when (code) {
    0, 1 -> Symbols.Sunny
    2 -> Symbols.PartlyCloudy
    3 -> Symbols.Cloud
    45, 48 -> Symbols.Fog
    71, 73, 75, 77, 85, 86 -> Symbols.Snow
    95, 96, 99 -> Symbols.Storm
    else -> Symbols.Rain
}

@Composable
fun Symbol(name: String, color: Color, size: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = name,
        color = color,
        modifier = modifier,
        style = TextStyle(
            fontFamily = MaterialSymbols,
            fontSize = size,
            lineHeight = size,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
