package com.grey.naswatchdog.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.R

internal val Bg = Color(0xFF080D10)
internal val Surface = Color(0xFF11191D)
internal val SurfaceHigh = Color(0xFF19252A)
internal val Line = Color(0xFF2A383C)
internal val TextMain = Color(0xFFEDF5F0)
internal val Dim = Color(0xFF9DAFAA)
internal val Green = Color(0xFF8CE6C1)
internal val Amber = Color(0xFFF2C078)
internal val Red = Color(0xFFFF8589)
internal val Blue = Color(0xFF9ABBD3)
internal val Purple = Blue
internal val Cyan = Blue

private val InstrumentColors = darkColorScheme(
    primary = Green,
    onPrimary = Bg,
    secondary = Blue,
    onSecondary = Bg,
    background = Bg,
    onBackground = TextMain,
    surface = Surface,
    onSurface = TextMain,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Dim,
    outline = Line,
    outlineVariant = Line.copy(alpha = 0.65f),
    error = Red,
    onError = Bg,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
)

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resource: Int, weight: FontWeight) = Font(
    resource,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val InstrumentFont: FontFamily
    @OptIn(ExperimentalTextApi::class)
    get() = FontFamily(
        variableFont(R.font.geist_variable, FontWeight.Normal),
        variableFont(R.font.geist_variable, FontWeight.Medium),
        variableFont(R.font.geist_variable, FontWeight.SemiBold),
        variableFont(R.font.geist_variable, FontWeight.Bold),
    )

val MachineFont: FontFamily
    @OptIn(ExperimentalTextApi::class)
    get() = FontFamily(
        variableFont(R.font.geist_mono_variable, FontWeight.Normal),
        variableFont(R.font.geist_mono_variable, FontWeight.Medium),
    )

private fun textStyle(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = InstrumentFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
)

private val InstrumentTypography = Typography(
    displayLarge = textStyle(56, 60, FontWeight.Medium),
    displayMedium = textStyle(44, 48, FontWeight.Medium),
    displaySmall = textStyle(36, 40, FontWeight.Medium),
    headlineLarge = textStyle(32, 38, FontWeight.Medium),
    headlineMedium = textStyle(27, 34, FontWeight.Medium),
    headlineSmall = textStyle(23, 29, FontWeight.Medium),
    titleLarge = textStyle(21, 28, FontWeight.Medium),
    titleMedium = textStyle(16, 23, FontWeight.Medium),
    titleSmall = textStyle(14, 20, FontWeight.Medium),
    bodyLarge = textStyle(16, 25),
    bodyMedium = textStyle(14, 21),
    bodySmall = textStyle(12, 18),
    labelLarge = textStyle(14, 20, FontWeight.Medium),
    labelMedium = textStyle(12, 17, FontWeight.Medium),
    labelSmall = textStyle(11, 16, FontWeight.Medium),
)

val NumberTextStyle = TextStyle(
    fontFamily = InstrumentFont,
    fontFeatureSettings = "tnum",
)

@Composable
fun heroNumberSize(baseSp: Int) = with(LocalDensity.current) {
    val effectiveScale = 1f + (fontScale - 1f) * 0.5f
    (baseSp / effectiveScale.coerceIn(0.85f, 1.75f)).sp
}

val SignalPlateShape: Shape = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val corner = with(density) { 26.dp.toPx() }
        val cut = with(density) { 30.dp.toPx() }
        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height - corner)
            quadraticTo(size.width, size.height, size.width - corner, size.height)
            lineTo(corner, size.height)
            quadraticTo(0f, size.height, 0f, size.height - corner)
            lineTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun WatchdogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InstrumentColors,
        typography = InstrumentTypography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
