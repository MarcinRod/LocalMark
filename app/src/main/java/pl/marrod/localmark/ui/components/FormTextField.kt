package pl.marrod.localmark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.marrod.localmark.ui.theme.LocalMarkTheme


@Composable
fun FormTextField(
   value: String,
   onValueChange: (String) -> Unit,
   label: String,
   leadingIcon: ImageVector,
   modifier: Modifier = Modifier,
   placeholder: String = "",
   isPassword: Boolean = false,
   trailingLabelText: String? = null,
   onTrailingLabelClick: (() -> Unit)? = null,
   isError: Boolean = false,
   errorMessage: String? = null,
   keyboardType: KeyboardType = KeyboardType.Text,
   imeAction: ImeAction = ImeAction.Next,
) {
   var passwordVisible by rememberSaveable { mutableStateOf(false) }

   val visualTransformation = if (isPassword && !passwordVisible)
      PasswordVisualTransformation()
   else
      VisualTransformation.None

   val resolvedKeyboardType = if (isPassword) KeyboardType.Password else keyboardType

   Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(4.dp),
   ) {

      // ── Label row ─────────────────────────────────────────────────────────
      Row(
         modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
         horizontalArrangement = Arrangement.SpaceBetween,
         verticalAlignment = Alignment.CenterVertically,
      ) {
         Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
         )

         // Optional trailing action
         if (trailingLabelText != null && onTrailingLabelClick != null) {

            Text(
               text = trailingLabelText,
               style = MaterialTheme.typography.bodyMedium,
               color = MaterialTheme.colorScheme.primaryContainer,
               modifier = Modifier
                  .padding(end = 2.dp)
                  .clickable(
                     onClick = onTrailingLabelClick,
                     indication = ripple(), // Remove ripple
                     interactionSource = remember { MutableInteractionSource() }
                  )
            )
         }
      }

      // ── Input ─────────────────────────────────────────────────────────────
      OutlinedTextField(
         value = value,
         onValueChange = onValueChange,
         modifier = Modifier.fillMaxWidth(),
         singleLine = true,
         placeholder = {
            Text(
               text = placeholder,
               style = MaterialTheme.typography.bodyMedium,
            )
         },
         leadingIcon = {
            Icon(
               imageVector = leadingIcon,
               contentDescription = null,
            )
         },
         trailingIcon = if (isPassword) {
            {
               IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                     imageVector = if (passwordVisible)
                        Icons.TwoTone.Visibility
                     else
                        Icons.TwoTone.VisibilityOff,
                     contentDescription = if (passwordVisible) "Hide password" else "Show password",
                  )
               }
            }
         } else null,
         visualTransformation = visualTransformation,
         keyboardOptions = KeyboardOptions(
            keyboardType = resolvedKeyboardType,
            imeAction = imeAction,
         ),
         shape = RoundedCornerShape(8.dp),
         colors = OutlinedTextFieldDefaults.colors(
            // Container
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.50f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
               alpha = 0.50f
            ),
            // Border
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            // Text
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            // Placeholder
            focusedPlaceholderColor = MaterialTheme.colorScheme.outline,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline,
            // Leading icon
            focusedLeadingIconColor = MaterialTheme.colorScheme.outline,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.outline,
            // Trailing icon (visibility toggle)
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.outline,
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
   }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "FormTextField – Email (Dark)", showBackground = true, backgroundColor = 0xFF131313)
@Composable
private fun FormTextFieldEmailDarkPreview() {
   var email by rememberSaveable { mutableStateOf("") }
   LocalMarkTheme(darkTheme = true) {
      FormTextField(
         value = email,
         onValueChange = { email = it },
         label = "Email",
         leadingIcon = Icons.Default.Email,
         placeholder = "agent@citygrid.com",
         keyboardType = KeyboardType.Email,
         imeAction = ImeAction.Next,
         modifier = Modifier.padding(16.dp),
      )
   }
}

@Preview(
   name = "FormTextField – Password (Dark)",
   showBackground = true,
   backgroundColor = 0xFF131313
)
@Composable
private fun FormTextFieldPasswordDarkPreview() {
   var password by rememberSaveable { mutableStateOf("") }
   LocalMarkTheme(darkTheme = true) {
      FormTextField(
         value = password,
         onValueChange = { password = it },
         label = "Password",
         leadingIcon = Icons.Default.Lock,
         placeholder = "••••••••",
         isPassword = true,
         trailingLabelText = "Forgot Password?",
         onTrailingLabelClick = {},
         imeAction = ImeAction.Done,
         modifier = Modifier.padding(16.dp),
      )
   }
}

@Preview(
   name = "FormTextField – Both fields (Dark)",
   showBackground = true,
   backgroundColor = 0xFF131313,
   widthDp = 400
)
@Composable
private fun FormTextFieldBothDarkPreview() {
   var email by rememberSaveable { mutableStateOf("") }
   var password by rememberSaveable { mutableStateOf("") }
   LocalMarkTheme(darkTheme = true) {
      Column(
         modifier = Modifier.padding(16.dp),
         verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
         FormTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = Icons.Default.Email,
            placeholder = "agent@citygrid.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
         )
         FormTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            leadingIcon = Icons.Default.Lock,
            placeholder = "••••••••",
            isPassword = true,
            trailingLabelText = "Forgot Password?",
            onTrailingLabelClick = {},
            imeAction = ImeAction.Done,
         )
      }
   }
}

@Preview(
   name = "FormTextField – Both fields (Light)",
   showBackground = true,
   backgroundColor = 0xFFF7F9FB,
   widthDp = 400
)
@Composable
private fun FormTextFieldBothLightPreview() {
   var email by rememberSaveable { mutableStateOf("") }
   var password by rememberSaveable { mutableStateOf("") }
   LocalMarkTheme(darkTheme = false) {
      Column(
         modifier = Modifier.padding(16.dp),
         verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
         FormTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            leadingIcon = Icons.Default.Email,
            placeholder = "agent@citygrid.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
         )
         FormTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            leadingIcon = Icons.Default.Lock,
            placeholder = "••••••••",
            isPassword = true,
            trailingLabelText = "Forgot Password?",
            onTrailingLabelClick = {},
            imeAction = ImeAction.Done,
         )
      }
   }
}

