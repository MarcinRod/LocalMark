package pl.marrod.localmark.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.marrod.localmark.ui.theme.AmbientShadowColor


/**
 * Draws a soft blurred drop shadow behind the composable.
 *
 *
 * @param color       Shadow colour. Defaults to [AmbientShadowColor].
 * @param offsetY     Vertical shadow offset. Defaults to `12.dp`.
 * @param blurRadius  Shadow blur radius. Defaults to `32.dp`.
 */
fun Modifier.ambientShadow(
    color: Color = AmbientShadowColor,
    offsetY: Dp = 12.dp,
    blurRadius: Dp = 32.dp,
): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        with(paint.nativePaint) {
            isAntiAlias = true
            this.color = color.copy(alpha = 0f).toArgb()
            maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
            setShadowLayer(blurRadius.toPx(), 0f, offsetY.toPx(), color.toArgb())
        }
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}


