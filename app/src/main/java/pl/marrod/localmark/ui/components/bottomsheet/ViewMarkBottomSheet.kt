package pl.marrod.localmark.ui.components.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import pl.marrod.localmark.data.DummyMarkerDataSource
import pl.marrod.localmark.data.model.MarkerData
import pl.marrod.localmark.ui.components.BaseCard
import pl.marrod.localmark.ui.components.BaseOutlinedButton
import pl.marrod.localmark.ui.components.GlassCard
import pl.marrod.localmark.ui.components.ImageWithShimmer
import pl.marrod.localmark.ui.components.MetaChip
import pl.marrod.localmark.ui.helpers.asString
import pl.marrod.localmark.ui.helpers.distanceLabel
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.Spacing
import pl.marrod.localmark.ui.theme.TextFieldShape



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewMarkBottomSheetContent(
    marker: MarkerData,
    viewerId: String,
    currentLocation: LatLng? = null,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val distanceLabelText = remember(currentLocation, marker.location) {
        distanceLabel(currentLocation, marker.location)
    }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    GlassCard(
        modifier = modifier,
        cardShape = sheetShape,
        background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f)
    ) {

        // ── Scrollable content area ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.edgeMargin,
                    end = Spacing.edgeMargin,
                    top = 24.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.stackGap * 2),
        ) {

            // ── Author row ───────────────────────────────────────────────
            BaseCard(
                modifier = Modifier,
                background = surfaceColor
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = marker.category.accentColor.copy(alpha = 0.20f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = marker.category.icon,
                            contentDescription = null,
                            tint = marker.category.accentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier) {
                        Text(
                            text = marker.category.label.asString().uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = marker.category.accentColor,
                        )
                        Text(
                            text = marker.authorName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Text(
                            text = marker.locationName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            marker.imageUri?.let { imageUrl ->

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceColor)
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ImageWithShimmer(
                        imageUri = imageUrl,
                        contentDescription = "Attached photo preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            // ── Message body ─────────────────────────────────────────────
            Text(
                text = marker.message,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.titleMedium.fontSize * 1.3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TextFieldShape)
                    .background(surfaceColor)
                    .padding(16.dp),
            )

            // ── Metadata row ─────────────────────────────────────────────
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.stackGap),
                verticalArrangement = Arrangement.spacedBy(Spacing.stackGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (distanceLabelText != null) {
                    MetaChip(
                        icon = Icons.Default.LocationOn,
                        label = distanceLabelText,
                    )
                }
                MetaChip(
                    icon = Icons.Default.Schedule,
                    label = marker.timeAgoLabel.asString(),
                )
                MetaChip(
                    icon = Icons.Default.ThumbUp,
                    label = "${marker.confirmations.size} Confirmations",
                    iconTint = MaterialTheme.colorScheme.secondary,
                )
            }


            Column(verticalArrangement = Arrangement.spacedBy(Spacing.stackGap * 3)) {

                if (viewerId == marker.authorId)
                    BaseOutlinedButton(
                        text = "Edit",
                        icon = Icons.Default.Edit,
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth(),
                    )

                val color = if (viewerId == marker.authorId) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
                BaseOutlinedButton(
                    text = if (viewerId == marker.authorId) "Delete" else "Confirm",
                    icon = if (viewerId == marker.authorId) Icons.Default.Delete else Icons.Default.ThumbUp,
                    onClick = if (viewerId == marker.authorId) onDelete else onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    tint = color
                )

            }
        }


    }

}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewMarkBottomSheet(
    marker: MarkerData,
    viewerId: String,
    onDismissRequest: () -> Unit,
    currentLocation: LatLng? = null,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        ViewMarkBottomSheetContent(
            marker = marker,
            currentLocation = currentLocation,
            onDelete = onDelete,
            onConfirm = onConfirm,
            viewerId = viewerId,
            onEdit = onEdit,
        )
    }
}


