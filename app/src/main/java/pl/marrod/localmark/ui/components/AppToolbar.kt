package pl.marrod.localmark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.marrod.localmark.R
import pl.marrod.localmark.ui.theme.CardShapes
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.OnSurfaceVariant
import pl.marrod.localmark.ui.theme.Primary
import pl.marrod.localmark.ui.theme.Spacing

/**
 * Floating top navigation bar.
 *
 * @param title            Center label.
 * @param navigationIcon   Leading icon (default: Explore).
 * @param actionIcon       Trailing icon (default: ExitToApp / logout).
 * @param onNavigationClick Called when the leading icon is tapped.
 * @param onActionClick    Called when the trailing icon is tapped.
 */
@Composable
fun AppToolbar(
    title: String,
    navigationIcon: ImageVector = Icons.Default.Explore,
    actionIcon: ImageVector = Icons.AutoMirrored.Filled.ExitToApp,
    onNavigationClick: () -> Unit = {},
    onActionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape =  CardShapes.normal

    GlassCard(  modifier = modifier
        .fillMaxWidth()

    ){
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), shape = shape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {

            // Leading icon – explore / navigation
            IconButton(onClick = onNavigationClick, colors = IconButtonDefaults.iconButtonColors().copy(
                contentColor = MaterialTheme.colorScheme.primaryFixedDim,
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primaryFixedDim.copy(alpha = 0.38f),

            )) {
                Icon(
                    painter = painterResource(R.drawable.localmark),
                    contentDescription = "Navigation",
                )
            }

            // Center title
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )

            // Trailing icon – logout / action
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    tint = MaterialTheme.colorScheme.primaryFixedDim,
                )
            }
        }
    }
}
