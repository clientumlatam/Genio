package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkCobaltPrimary,
    onPrimary = DarkOnCobalt,
    primaryContainer = DarkCobaltContainer,
    onPrimaryContainer = CobaltContainer,
    secondary = DarkAmberSecondary,
    onSecondary = DarkOnAmber,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = AmberContainer,
    tertiary = DarkEmeraldTertiary,
    onTertiary = OnEmeraldContainer,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = DarkNavyBackground,
    onBackground = DarkInkPrimary,
    surface = DarkNavySurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkNavySurfaceHigh,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = AmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = PorcelainBackground,
    onBackground = InkPrimary,
    surface = SurfaceWhite,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLow = SurfaceWhite,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
  )

val GuiaGenioShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun GuiaGenioTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = GuiaGenioShapes,
    content = content,
  )
}
