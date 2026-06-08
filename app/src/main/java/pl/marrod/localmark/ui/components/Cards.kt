package pl.marrod.localmark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.marrod.localmark.ui.theme.CardShapes
import pl.marrod.localmark.ui.theme.GlassPanelColor


@Composable
fun BaseCard(
    modifier: Modifier = Modifier,
    cardShape: Shape = CardShapes.normal,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    backgroundAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = cardShape,
    ) {
        // card content
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(background.copy(alpha = backgroundAlpha))
                .ambientShadow(color = background.copy(alpha = 0.3f), offsetY = 12.dp, blurRadius = 32.dp),
        ) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cardShape: Shape = CardShapes.normal,
    background: Color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f),
    isDarkTheme: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = Color.Transparent,
        border = BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.20f)),
    ) {
        // card content
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(background)
                .ambientShadow(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = if(isDarkTheme) 0.3f else 0.5f),
                    offsetY = 12.dp,
                    blurRadius = 32.dp
                ),
        ) {
            content()
        }
    }
}