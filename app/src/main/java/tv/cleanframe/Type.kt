@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package tv.cleanframe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun roboto(weight: Int) = Font(
    R.font.roboto_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("wdth", 100f),
    ),
)

val Roboto = FontFamily(
    roboto(300),
    roboto(400),
    roboto(500),
    roboto(600),
)

fun typeface(choice: TypefaceChoice): FontFamily = Roboto

val MotionEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun focusColor(focused: Boolean, focus: Color, idle: Color, durationMillis: Int): Color {
    val color by animateColorAsState(
        targetValue = if (focused) focus else idle,
        animationSpec = tween(durationMillis = durationMillis, easing = MotionEase),
        label = "focus",
    )
    return color
}

private val NameShadow = Shadow(
    color = Color(0x520E0A06),
    offset = Offset(0f, 3f),
    blurRadius = 14f,
)

private val QuietShadow = Shadow(
    color = Color(0x660E0A06),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
)

fun homeNameStyle(choice: TypefaceChoice) = TextStyle(
    fontFamily = typeface(choice),
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    letterSpacing = (-0.9).sp,
    lineHeight = 52.sp,
    shadow = NameShadow,
)

fun clockStyle(choice: TypefaceChoice) = TextStyle(
    fontFamily = typeface(choice),
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
    letterSpacing = (-0.4).sp,
    shadow = QuietShadow,
)

fun controlStyle(choice: TypefaceChoice) = TextStyle(
    fontFamily = typeface(choice),
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    letterSpacing = (-0.1).sp,
    lineHeight = 18.sp,
    shadow = QuietShadow,
)
