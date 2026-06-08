package pl.marrod.localmark.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.marrod.localmark.R
import pl.marrod.localmark.ui.theme.GlassPanelColor
import pl.marrod.localmark.ui.theme.LocalMarkTheme


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchRadiusCard(
    radiusKm: Float,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minKm: Float = 0.5f,
    maxKm: Float = 10f,
    stepKm: Float = 0.5f,
    trackHeight: Dp = 12.dp
) {
    val shape = RoundedCornerShape(16.dp)
    // steps param = number of intermediate snapping points (total positions = steps + 2)
    val steps = ((maxKm - minKm) / stepKm).toInt() - 1

    GlassCard(
        modifier = modifier,
        cardShape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

            // ── Header row: label + current value ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_radius),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%.1f km".format(radiusKm),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ── Slider ────────────────────────────────────────────────────────
            // The Slider's internal touch-target padding makes it taller than the
            // visible thumb.  The layout modifier below reports a reduced height
            // to the parent (collapsing the gap) while the actual touch area still
            // extends silently outside the bounds — so accessibility is unaffected.

            Slider(
                value = radiusKm,
                onValueChange = onRadiusChange,
                valueRange = minKm..maxKm,
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        // Desired visual height: thumb height + 8 dp breathing room
                        val desiredHeight = (trackHeight + 8.dp).roundToPx()
                            .coerceAtMost(placeable.height)
                        layout(placeable.width, desiredHeight) {
                            // Centre the slider within the reduced slot
                            placeable.placeRelative(0, (desiredHeight - placeable.height) / 2)
                        }
                    },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTickColor       = MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                            inactiveTickColor     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.0f),
                        ),
                        modifier = Modifier.height(trackHeight)
                    )
                },
                thumb = { sliderState ->
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        sliderState = sliderState,
                        thumbSize = DpSize(4.dp, trackHeight.times(2))
                    )

                },
            )

            // ── Min / max tick labels ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${minKm}km".replace(".0km", "km"),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                )
                Text(
                    text = "${maxKm.toInt()}km",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                )
            }
        }
    }
}
