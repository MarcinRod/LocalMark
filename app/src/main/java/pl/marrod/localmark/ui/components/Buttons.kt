package pl.marrod.localmark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.ui.helpers.asString
import pl.marrod.localmark.ui.helpers.toUiText
import pl.marrod.localmark.ui.theme.ButtonShapes
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.Spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = ButtonDefaults.MediumContainerHeight,
) {

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = ButtonDefaults.contentPaddingFor(
            buttonHeight = height,
            hasStartIcon = icon != null
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = 0.38f
            ),
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(
                alpha = 0.38f
            ),
        ),
        shapes = ButtonDefaults.shapes(
            shape = ButtonShapes.normal,
            pressedShape = ButtonShapes.large
        )
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = ButtonDefaults.MediumContainerHeight,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = ButtonDefaults.contentPaddingFor(
            buttonHeight = height,
            hasStartIcon = icon != null
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tint.copy(alpha = 0.05f),
            contentColor = if (enabled) tint else tint.copy(
                alpha = 0.38f
            ),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) tint else tint.copy(
                alpha = 0.38f
            )
        ),
        shapes = ButtonDefaults.shapes(
            shape = ButtonShapes.normal,
            pressedShape = ButtonShapes.large
        )
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseProcessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    result: Result<UiText>? = null,
    isProcessing: Boolean = false,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MediumContainerHeight,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = ButtonDefaults.contentPaddingFor(
                buttonHeight = height,
                hasStartIcon = isProcessing
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = 0.38f
                ),
                contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.38f
                ),
            ),
            shapes = ButtonDefaults.shapes(
                shape = ButtonShapes.normal,
                pressedShape = ButtonShapes.large
            )
        ) {
            if (isProcessing) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primaryFixed,
                    trackColor = MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.30f),
                )
                Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
            } else {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
                }
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(tween(350)) + expandVertically(),
            exit = fadeOut(tween(350)) + shrinkVertically(),
        ) {

            val message = if (result?.isSuccess == true) {
                result.getOrNull()?.asString() ?: return@AnimatedVisibility

            } else {
                result?.exceptionOrNull().toUiText().asString()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val color =
                    if (result?.isSuccess == true)
                        MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

                Icon(
                    imageVector = if (result?.isSuccess == true)
                        Icons.Default.Check else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

            }
        }
    }
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MediumContainerHeight,
    icon: @Composable (() -> Unit)? = null,
) {

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = ButtonDefaults.contentPaddingFor(
            buttonHeight = height,
            hasStartIcon = icon != null
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.38f
            ),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(
                alpha = 0.38f
            )
        ),
        shapes = ButtonDefaults.shapes(
            shape = ButtonShapes.normal,
            pressedShape = ButtonShapes.large
        )
    ) {
        icon?.let {
            it.invoke()
            Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseOutlinedProcessButton(
    text: String,
    onClick: () -> Unit,
    result: Result<UiText>? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isProcessing: Boolean = false,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MediumContainerHeight,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = ButtonDefaults.contentPaddingFor(
                buttonHeight = height,
                hasStartIcon = isProcessing
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f
                ),
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.38f
                )
            ),
            shapes = ButtonDefaults.shapes(
                shape = ButtonShapes.normal,
                pressedShape = ButtonShapes.large
            )
        ) {
            if (isProcessing) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.30f),
                )
                Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
            } else {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(Spacing.unit.times(2)))
                }
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(tween(350)) + expandVertically(),
            exit = fadeOut(tween(350)) + shrinkVertically(),
        ) {

            val message = result?.getOrNull()?.asString() ?: return@AnimatedVisibility

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val color =
                    if (result.isSuccess)
                        MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

                Icon(
                    imageVector = if (result.isSuccess)
                        Icons.Default.CheckCircleOutline else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

            }
        }
    }
}

