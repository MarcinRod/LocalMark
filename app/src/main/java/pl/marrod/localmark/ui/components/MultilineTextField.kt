package pl.marrod.localmark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import pl.marrod.localmark.R
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.OnSurface
import pl.marrod.localmark.ui.theme.OnSurfaceVariant
import pl.marrod.localmark.ui.theme.Outline
import pl.marrod.localmark.ui.theme.OutlineVariant
import pl.marrod.localmark.ui.theme.PrimaryContainer
import pl.marrod.localmark.ui.theme.SurfaceContainerLow
import pl.marrod.localmark.ui.theme.TextFieldShape


@Composable
fun MultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    maxLength: Int = 200,
    minLines: Int = 4,
    maxLines: Int = 6,
) {
    var isError by remember { mutableStateOf(false) }
    val errorMessage = if (isError) stringResource(R.string.maximum_length_exceeded, maxLength) else null
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (it.length <= maxLength) onValueChange(it)
                isError = it.length >= maxLength
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            minLines = minLines,
            maxLines = maxLines,
            shape = TextFieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            isError = isError,
            supportingText = {
                AnimatedVisibility(
                    visible = isError,
                    enter = fadeIn(tween(550)) + expandVertically(),
                    exit = fadeOut(tween(550)) + shrinkVertically(),
                ) {
                    errorMessage?.let {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )

        // Inline character counter
        Text(
            text = "${value.length}/$maxLength",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
        )
    }
}


