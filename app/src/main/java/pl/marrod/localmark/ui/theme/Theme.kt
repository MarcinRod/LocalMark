package pl.marrod.localmark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary                  = Primary,
    onPrimary                = OnPrimary,
    primaryContainer         = PrimaryContainer,
    onPrimaryContainer       = OnPrimaryContainer,
    primaryFixed             = PrimaryFixed,
    primaryFixedDim          = PrimaryFixedDim,
    onPrimaryFixed           = OnPrimaryFixed,
    onPrimaryFixedVariant    = OnPrimaryFixedVariant,
    inversePrimary           = InversePrimary,
    secondary                = Secondary,
    onSecondary              = OnSecondary,
    secondaryContainer       = SecondaryContainer,
    onSecondaryContainer     = OnSecondaryContainer,
    secondaryFixed           = SecondaryFixed,
    secondaryFixedDim        = SecondaryFixedDim,
    onSecondaryFixed         = OnSecondaryFixed,
    onSecondaryFixedVariant  = OnSecondaryFixedVariant,
    tertiary                 = Tertiary,
    onTertiary               = OnTertiary,
    tertiaryContainer        = TertiaryContainer,
    onTertiaryContainer      = OnTertiaryContainer,
    tertiaryFixed            = TertiaryFixed,
    tertiaryFixedDim         = TertiaryFixedDim,
    onTertiaryFixed          = OnTertiaryFixed,
    onTertiaryFixedVariant   = OnTertiaryFixedVariant,
    background               = Background,
    onBackground             = OnBackground,
    surface                  = Surface,
    onSurface                = OnSurface,
    surfaceVariant           = SurfaceVariant,
    onSurfaceVariant         = OnSurfaceVariant,
    surfaceTint              = SurfaceTint,
    inverseSurface           = InverseSurface,
    inverseOnSurface         = InverseOnSurface,
    error                    = Error,
    onError                  = OnError,
    errorContainer           = ErrorContainer,
    onErrorContainer         = OnErrorContainer,
    outline                  = Outline,
    outlineVariant           = OutlineVariant,
    surfaceBright            = SurfaceBright,
    surfaceDim               = SurfaceDim,
    surfaceContainerLowest   = SurfaceContainerLowest,
    surfaceContainerLow      = SurfaceContainerLow,
    surfaceContainer         = SurfaceContainer,
    surfaceContainerHigh     = SurfaceContainerHigh,
    surfaceContainerHighest  = SurfaceContainerHighest,
)

private val LightColorScheme = lightColorScheme(
    primary                  = LightPrimary,
    onPrimary                = LightOnPrimary,
    primaryContainer         = LightPrimaryContainer,
    onPrimaryContainer       = LightOnPrimaryContainer,
    primaryFixed             = LightPrimaryFixed,
    primaryFixedDim          = LightPrimaryFixedDim,
    onPrimaryFixed           = LightOnPrimaryFixed,
    onPrimaryFixedVariant    = LightOnPrimaryFixedVariant,
    inversePrimary           = LightInversePrimary,
    secondary                = LightSecondary,
    onSecondary              = LightOnSecondary,
    secondaryContainer       = LightSecondaryContainer,
    onSecondaryContainer     = LightOnSecondaryContainer,
    secondaryFixed           = LightSecondaryFixed,
    secondaryFixedDim        = LightSecondaryFixedDim,
    onSecondaryFixed         = LightOnSecondaryFixed,
    onSecondaryFixedVariant  = LightOnSecondaryFixedVariant,
    tertiary                 = LightTertiary,
    onTertiary               = LightOnTertiary,
    tertiaryContainer        = LightTertiaryContainer,
    onTertiaryContainer      = LightOnTertiaryContainer,
    tertiaryFixed            = LightTertiaryFixed,
    tertiaryFixedDim         = LightTertiaryFixedDim,
    onTertiaryFixed          = LightOnTertiaryFixed,
    onTertiaryFixedVariant   = LightOnTertiaryFixedVariant,
    background               = LightBackground,
    onBackground             = LightOnBackground,
    surface                  = LightSurface,
    onSurface                = LightOnSurface,
    surfaceVariant           = LightSurfaceVariant,
    onSurfaceVariant         = LightOnSurfaceVariant,
    surfaceTint              = LightSurfaceTint,
    inverseSurface           = LightInverseSurface,
    inverseOnSurface         = LightInverseOnSurface,
    error                    = LightError,
    onError                  = LightOnError,
    errorContainer           = LightErrorContainer,
    onErrorContainer         = LightOnErrorContainer,
    outline                  = LightOutline,
    outlineVariant           = LightOutlineVariant,
    surfaceBright            = LightSurfaceBright,
    surfaceDim               = LightSurfaceDim,
    surfaceContainerLowest   = LightSurfaceContainerLowest,
    surfaceContainerLow      = LightSurfaceContainerLow,
    surfaceContainer         = LightSurfaceContainer,
    surfaceContainerHigh     = LightSurfaceContainerHigh,
    surfaceContainerHighest  = LightSurfaceContainerHighest,
)

@Composable
fun LocalMarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = AppShapes,
        content     = content
    )
}