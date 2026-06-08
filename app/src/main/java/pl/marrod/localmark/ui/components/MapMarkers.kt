package pl.marrod.localmark.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativePaint

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.TertiaryFixedDim




@Composable
fun AlertMarker(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    onClick: () -> Unit = {},
) {
    val resolvedAccent = if (accentColor == Color.Unspecified)
        MaterialTheme.colorScheme.secondary
    else
        accentColor

    val headShape = CircleShape
    val stemShape = RoundedCornerShape(bottomStartPercent = 50, bottomEndPercent = 50)

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = 28.dp),
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {

        // ── Pin head ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                    shape = headShape,
                )
                .border(1.dp, resolvedAccent.copy(alpha = 0.30f), headShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedAccent,
                modifier = Modifier.size(18.dp),
            )
        }

        // ── Stem — overlaps head by 1 dp ────────────────────────────
        Spacer(
            modifier = Modifier
                .offset(y = (-1).dp)
                .width(4.dp)
                .size(width = 4.dp, height = 12.dp)
                .background(resolvedAccent, stemShape),
        )

        // ── Ground shadow — blurred dark ellipse (mt-1) ──────────────────────
        Box(
            modifier = Modifier
                .offset(y = (-4).dp)           // compensates stem offset so shadow stays below stem
                .size(width = 24.dp, height = 8.dp)
                .blur(2.dp)
                .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(50)),
        )
    }
}


@Composable
fun LocalMarkMarker(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    onClick: () -> Unit = {},
) {
    val resolvedAccent = if (accentColor == Color.Unspecified)
        MaterialTheme.colorScheme.secondary
    else
        accentColor

    val headColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    val borderColor = resolvedAccent.copy(alpha = 0.30f)
    val shadowColor = Color.Black.copy(alpha = 0.20f)

    val iconPainter = rememberVectorPainter(icon)

    // Canvas size (dp):
    //   width  = 34 dp  (head diameter)
    //   height = 34 + (12 – 1) + (8 – 4) = 49 dp
    //            head   stem–overlap  shadow–offset
    Canvas(
        modifier = modifier
            .size(width = 34.dp, height = 49.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick,
            )
    ) {
        val headR = 17.dp.toPx()          // radius  = diameter / 2
        val centerX = size.width / 2f
        val overlapPx = 1.dp.toPx()
        val stemW = 4.dp.toPx()
        val stemH = 12.dp.toPx()
        val shadowW = 24.dp.toPx()
        val shadowH = 8.dp.toPx()
        val stemTop = headR * 2 - overlapPx
        val shadowTop = stemTop + stemH - 4.dp.toPx()  // stem bottom – 4 dp offset

        // ── Ground shadow (lowest layer) ──────────────────────────────────────
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                nativePaint.apply {
                    isAntiAlias = true
                    color = shadowColor.toArgb()
                    maskFilter = BlurMaskFilter(4.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.drawOval(
                rect = Rect(
                    left = centerX - shadowW / 2f,
                    top = shadowTop,
                    right = centerX + shadowW / 2f,
                    bottom = shadowTop + shadowH,
                ),
                paint = paint,
            )
        }

        // ── Stem ──────────────────────────────────────────────────────────────
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = centerX - stemW / 2f,
                        top = stemTop,
                        right = centerX + stemW / 2f,
                        bottom = stemTop + stemH,
                        bottomLeftCornerRadius = CornerRadius(stemW / 2f),
                        bottomRightCornerRadius = CornerRadius(stemW / 2f),
                    )
                )
            },
            color = resolvedAccent,
        )

        // ── Pin head fill ─────────────────────────────────────────────────────
        drawCircle(color = headColor, radius = headR, center = Offset(centerX, headR))

        // ── Pin head border ───────────────────────────────────────────────────
        drawCircle(
            color = borderColor,
            radius = headR - 0.5.dp.toPx(),
            center = Offset(centerX, headR),
            style = Stroke(width = 1.dp.toPx()),
        )

        // ── Icon ──────────────────────────────────────────────────────────────
        val iconPx = 18.dp.toPx()
        translate(left = centerX - iconPx / 2f, top = headR - iconPx / 2f) {
            with(iconPainter) {
                draw(
                    size = Size(iconPx, iconPx),
                    colorFilter = ColorFilter.tint(resolvedAccent),
                )
            }
        }
    }
}



