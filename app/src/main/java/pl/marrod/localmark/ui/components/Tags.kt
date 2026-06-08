package pl.marrod.localmark.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.Spacing
import pl.marrod.localmark.ui.theme.TagShapes

@Composable
fun MetaChip(
    icon: ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 100)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
class TagColors(
    val background: Color,
    val content: Color,
    val border: Color,
)


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkTag(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (Boolean) -> Unit,
    icon: ImageVector? = null,
    height: Dp = ButtonDefaults.ExtraSmallContainerHeight,
    tagColor: TagColors = TagColors(
        background = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
        border = MaterialTheme.colorScheme.outline,
    ),
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = onClick,
        modifier = modifier.heightIn(height),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = if (selected) tagColor.background else tagColor.content,
            contentColor = if (selected) tagColor.content else tagColor.background

            ),
        shapes = ToggleButtonDefaults.shapes(
            shape = TagShapes.large,
            pressedShape = TagShapes.normal,
            checkedShape = TagShapes.small,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) tagColor.border.copy(alpha = 0.10f) else tagColor.border.copy(alpha = 0.30f)
        ),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        icon?.let {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height.times(2))))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
        )

    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "AlertTag – Default (Dark)", showBackground = true, backgroundColor = 0xFF131313)
@Composable
private fun MarkTagDefaultDarkPreview() {
    LocalMarkTheme(darkTheme = true) {
        MarkTag(
            label = "Hazard",
            icon = Icons.Default.Warning,
            selected = false,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "AlertTag – Selected (Dark)", showBackground = true, backgroundColor = 0xFF131313)
@Composable
private fun MarkTagSelectedDarkPreview() {
    LocalMarkTheme(darkTheme = true) {
        MarkTag(
            label = "Traffic",
            selected = true,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(
    name = "AlertTag – Both states (Dark)",
    showBackground = true,
    backgroundColor = 0xFF131313
)
@Composable
private fun MarkTagBothDarkPreview() {
    LocalMarkTheme(darkTheme = true) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkTag(
                    label = "Hazard",
                    icon = Icons.Default.Warning,
                    selected = false,
                    onClick = {})
                MarkTag(
                    label = "Traffic",
                    icon = Icons.Default.DirectionsCar,
                    selected = true,
                    onClick = {})
            }
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkTag(
                    label = "All",
                    icon = null,
                    selected = false,
                    onClick = {})
                MarkTag(
                    label = "Traffic",
                    icon = null,
                    selected = true,
                    onClick = {})
            }
        }
    }
}

@Preview(
    name = "AlertTag – Both states (Light)",
    showBackground = true,
    backgroundColor = 0xFFF7F9FB
)
@Composable
private fun MarkTagBothLightPreview() {
    LocalMarkTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkTag(label = "Hazard", icon = Icons.Default.Warning, selected = false, onClick = {})
            MarkTag(
                label = "Traffic",
                icon = Icons.Default.DirectionsCar,
                selected = true,
                onClick = {})
        }
    }
}



