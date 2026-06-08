package pl.marrod.localmark.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import pl.marrod.localmark.ui.theme.GlassPanelColor
import pl.marrod.localmark.ui.theme.InnerGlowColor
import pl.marrod.localmark.ui.theme.PrimaryFixed
import pl.marrod.localmark.ui.theme.SurfaceContainerHigh
import pl.marrod.localmark.ui.theme.TertiaryContainer


@Composable
fun ImageWithShimmer(
    imageUri: Uri,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    SubcomposeAsyncImage(
        model = imageUri,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            ShimmerBox(
                modifier = modifier
            )
        },
        error = {
            Box(
                modifier = modifier
                    .background(SurfaceContainerHigh),
            )
        },
        clipToBounds = true,
    )
}


@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_translate",
    ) // animates from 0 to 1000 and back, creating a back-and-forth shimmer effect
    val brush = Brush.linearGradient(
        colors = listOf(TertiaryContainer,InnerGlowColor),
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim + 300f, translateAnim + 300f),
    )
    Box(
        modifier = modifier
            .alpha(0.5f)
            .background(brush)
    )
}