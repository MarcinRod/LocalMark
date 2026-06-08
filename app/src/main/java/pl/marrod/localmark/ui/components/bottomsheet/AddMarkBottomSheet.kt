package pl.marrod.localmark.ui.components.bottomsheet

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import pl.marrod.localmark.R
import pl.marrod.localmark.data.model.MarkerCategory
import pl.marrod.localmark.data.model.MarkerData
import pl.marrod.localmark.ui.components.BaseCard
import pl.marrod.localmark.ui.components.GlassCard
import pl.marrod.localmark.ui.components.ImageWithShimmer
import pl.marrod.localmark.ui.components.MarkTag
import pl.marrod.localmark.ui.components.MultilineTextField
import pl.marrod.localmark.ui.components.BaseOutlinedButton
import pl.marrod.localmark.ui.components.BaseProcessButton
import pl.marrod.localmark.ui.helpers.UiText
import pl.marrod.localmark.ui.helpers.UiTextThrowable
import pl.marrod.localmark.ui.helpers.asString
import pl.marrod.localmark.ui.theme.LocalMarkTheme
import pl.marrod.localmark.ui.theme.Spacing


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkerBottomSheetContent(
   locationString: String,
   message: String,
   selectedCategory: MarkerCategory,
   isProcessing: Boolean,
   processingResult: Result<UiText>? = null,
   onMessageChange: (String) -> Unit,
   onCategorySelected: (MarkerCategory) -> Unit,
   onTakePhoto: () -> Unit,
   onPost: () -> Unit,
   modifier: Modifier = Modifier,
   categories: List<MarkerCategory> = MarkerCategory.entries,
   photoUri: Uri? = null,
   showPermissionRationale: Boolean = false,
   onDismissPermissionRationale: () -> Unit = {},
) {
   val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

   GlassCard(
      modifier = modifier,
      cardShape = sheetShape,
      background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f)
   ) {
      Column(
         modifier = Modifier.padding(
            start = Spacing.edgeMargin,
            end = Spacing.edgeMargin,
            top = 24.dp,
            bottom = 24.dp,
         ),
         verticalArrangement = Arrangement.spacedBy(Spacing.stackGap * 2),

         ) {

         // ── Location header chip ─────────────────────────────────────────
         BaseCard(
            modifier = Modifier,
            background = MaterialTheme.colorScheme.surfaceContainerHigh
         ) {
            Row(
               modifier = Modifier.padding(16.dp),
               verticalAlignment = Alignment.CenterVertically,
               horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
               Box(
                  modifier = Modifier
                     .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        CircleShape
                     )
                     .padding(8.dp),
                  contentAlignment = Alignment.Center,
               ) {
                  Icon(
                     imageVector = Icons.Default.LocationOn,
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.primaryContainer,
                     modifier = Modifier.size(20.dp),
                  )
               }
               Column(modifier = Modifier) {
                  Text(
                     text = stringResource(R.string.posting_at),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(Modifier.height(2.dp))
                  if (locationString.isEmpty()) {
                     LinearWavyProgressIndicator(
                        modifier = Modifier
                           .height(12.dp)
                           .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                     )
                  } else {
                     Text(
                        text = locationString,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                     )
                  }
               }
            }
         }

         // ── Multi-line text input ────────────────────────────────────────
         MultilineTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = stringResource(R.string.describe_the_alert),
            modifier = Modifier.fillMaxWidth()
         )

         // ── Tag chips ────────────────────────────────────────────────────
         Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
               text = stringResource(R.string.tag_your_alert),
               style = MaterialTheme.typography.labelSmall,
               color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
               horizontalArrangement = Arrangement.spacedBy(8.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
               categories.forEach { category ->
                  MarkTag(
                     label = category.label.asString(),
                     icon = category.icon,
                     selected = selectedCategory == category,
                     onClick = { onCategorySelected(category) },
                  )
               }
            }
         }

         // ── Photo preview ────────────────────────────────────────────────
         if (photoUri != null) {
            Box(
               modifier = Modifier
                  .fillMaxWidth(),
               contentAlignment = Alignment.Center,
            ) {
               ImageWithShimmer(
                  imageUri = photoUri,
                  contentDescription = "Attached photo preview",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier
                     .height(200.dp)
                     .clip(RoundedCornerShape(12.dp)),
               )

            }
         }

         // ── Attach photo button ──────────────────────────────────────────
         BaseOutlinedButton(
            text = stringResource(
               if (photoUri != null)
                  R.string.retake_photo
               else
                  R.string.attach_photo
            ),
            icon = if (photoUri != null) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
            onClick = onTakePhoto,
            modifier = Modifier.fillMaxWidth(),
         )

         // ── Camera permission denied warning ─────────────────────────────
         if (showPermissionRationale) {
            val context = LocalContext.current
            BaseCard(
               modifier = Modifier,
               background = MaterialTheme.colorScheme.errorContainer,
            ) {
               Row(
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
               ) {
                  Icon(
                     imageVector = Icons.Default.WarningAmber,
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.onErrorContainer,
                     modifier = Modifier.size(20.dp),
                  )
                  Text(
                     text = stringResource(R.string.camera_permission_rationale),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onErrorContainer,
                     modifier = Modifier.weight(1f),
                  )
                  TextButton(
                     onClick = {
                        onDismissPermissionRationale()
                        val intent = Intent(
                           Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                           Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                     },
                     colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                     ),
                  ) {
                     Text(stringResource(R.string.settings))
                  }
               }
            }
         }

         // ── Post button ──────────────────────────────────────────────────
         BaseProcessButton(
            text = stringResource(R.string.post_mark),
            icon = Icons.AutoMirrored.Filled.Send,
            onClick = onPost,
            result = processingResult,
            isProcessing = isProcessing,
            modifier = Modifier.fillMaxWidth(),
         )
      }
   }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMarkerBottomSheet(
   author: String,
   location: LatLng,
   locationString: String,
   photoUri: Uri?,
   isProcessing: Boolean,
   processingResult: Result<UiText>? = null,
   onTakePhoto: () -> Unit,
   onDismissRequest: () -> Unit,
   onPost: (MarkerData) -> Unit,
   categories: List<MarkerCategory> = MarkerCategory.entries,
   showPermissionRationale: Boolean = false,
   onDismissPermissionRationale: () -> Unit = {},
) {
   val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

   var message by rememberSaveable { mutableStateOf("") }
   var selectedCategoryIdx by rememberSaveable { mutableIntStateOf(0) }
   var selectedCategory: MarkerCategory by remember(selectedCategoryIdx) {
      mutableStateOf(categories[selectedCategoryIdx])
   }


   ModalBottomSheet(
      onDismissRequest = onDismissRequest,
      sheetState = sheetState,
      containerColor = Color.Transparent,
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
   ) {
      MarkerBottomSheetContent(
         locationString = locationString,
         message = message,
         onMessageChange = { message = it },
         categories = categories,
         isProcessing = isProcessing,
         processingResult = processingResult,
         selectedCategory = selectedCategory,
         onCategorySelected = { selectedCategory = it },
         photoUri = photoUri,
         onTakePhoto = onTakePhoto,
         showPermissionRationale = showPermissionRationale,
         onDismissPermissionRationale = onDismissPermissionRationale,
         onPost = {
            onPost(
               MarkerData(
                  location = location,
                  locationName = locationString,
                  category = selectedCategory,
                  message = message
               )
            )
         },
      )
   }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMarkerBottomSheet(
   markerData: MarkerData,
   photoUri: Uri?,
   isProcessing: Boolean,
   processingResult: Result<UiText>? = null,
   onTakePhoto: () -> Unit,
   onDismissRequest: () -> Unit,
   onPost: (MarkerData) -> Unit,
   categories: List<MarkerCategory> = MarkerCategory.entries,
   showPermissionRationale: Boolean = false,
   onDismissPermissionRationale: () -> Unit = {},
) {
   val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

   var message by rememberSaveable(markerData) { mutableStateOf(markerData.message) }
   var selectedCategoryIdx by rememberSaveable { mutableIntStateOf(0) }
   var selectedCategory: MarkerCategory by remember(selectedCategoryIdx) {
      mutableStateOf(categories[selectedCategoryIdx])
   }

   ModalBottomSheet(
      onDismissRequest = onDismissRequest,
      sheetState = sheetState,
      containerColor = Color.Transparent,
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
   ) {
      MarkerBottomSheetContent(
         locationString = markerData.locationName,
         message = message,
         onMessageChange = { message = it },
         categories = categories,
         isProcessing = isProcessing,
         processingResult = processingResult,
         selectedCategory = selectedCategory,
         onCategorySelected = { selectedCategory = it },
         photoUri = photoUri,
         onTakePhoto = onTakePhoto,
         showPermissionRationale = showPermissionRationale,
         onDismissPermissionRationale = onDismissPermissionRationale,
         onPost = {
            onPost(markerData.copy(message = message, category = selectedCategory))
         },
      )
   }
}
