# Camera with Image Processing

## What is it?

LocalMark lets users attach a photo to a map marker. The flow covers:

1. **Requesting the `CAMERA` permission** at runtime.
2. **Launching the system camera** via `ActivityResultContracts.TakePicture`.
3. **Creating a `FileProvider` URI** so the camera can write to the app's private cache.
4. **Resizing and EXIF-correcting** the captured image before uploading.

Everything is encapsulated in a `@Composable` factory function `rememberCameraLauncher()` that returns a `CameraLauncherState` — a `@Stable` state holder consumed by the bottom sheet UI.

---

## Dependencies

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.exifinterface)  // read/write EXIF metadata
```

`FileProvider` is part of `androidx.core` (already present). Camera permission and the `TakePicture` contract are part of the platform and `activity-ktx`/`activity-compose`.

`FileProvider` must be declared in `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

And a `res/xml/file_provider_paths.xml` file exposes the cache directory:

```xml
<paths>
    <cache-path name="camera" path="camera/" />
</paths>
```

---

## Core Concepts

### `ActivityResultContracts.TakePicture`

The modern way to launch the system camera. The contract accepts a `Uri` (where to write the photo) and returns a `Boolean` (`true` = photo was taken, `false` = user cancelled):

```kotlin
val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
) { success: Boolean ->
    if (success) {
        // the file at the URI is now populated
    }
}

// Launch the camera with a FileProvider URI
cameraLauncher.launch(uri)
```

### `FileProvider` URI

The camera needs a content `Uri` pointing to a file it is allowed to write. `FileProvider` creates a secure, shareable URI without exposing the raw file path:

```kotlin
val photoFile = File(context.cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg")
    .also { it.parentFile?.mkdirs() }

val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",   // must match the authority in the manifest
    photoFile,
)
```

### Camera permission

`CAMERA` is a dangerous runtime permission; it must be requested before launching the camera:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        cameraLauncher.launch(uri)
    } else {
        // show rationale UI
    }
}

// Check at the time the user taps "Attach Photo"
val granted = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.CAMERA,
) == PermissionChecker.PERMISSION_GRANTED

if (granted) cameraLauncher.launch(uri)
else permissionLauncher.launch(Manifest.permission.CAMERA)
```

### Image resizing with `BitmapFactory` and sub-sampling

Camera photos can be very large (8–50 MP). Sub-sampling decodes a smaller version directly to avoid `OutOfMemoryError`:

```kotlin
// 1. Decode only the dimensions (no pixel allocation)
val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
BitmapFactory.decodeFile(file.absolutePath, options)

// 2. Compute sub-sample factor
val scale = maxDimension.toFloat() / maxOf(options.outWidth, options.outHeight)
var sampleSize = 1
while (options.outWidth / (sampleSize * 2) >= targetWidth) sampleSize *= 2

// 3. Decode the sub-sampled bitmap
val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)

// 4. Scale to exact target size
val resized = sampled.scale(targetWidth, targetHeight)
```

### EXIF orientation correction

`BitmapFactory` strips EXIF metadata when decoding, so the orientation tag must be read **before** decoding and re-applied by rotating the bitmap:

```kotlin
val exif = ExifInterface(file.absolutePath)
val orientation = exif.getAttributeInt(
    ExifInterface.TAG_ORIENTATION,
    ExifInterface.ORIENTATION_NORMAL,
)
val rotationDegrees = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90  -> 90f
    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
    else -> 0f
}

val oriented = if (rotationDegrees != 0f) {
    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    Bitmap.createBitmap(resized, 0, 0, resized.width, resized.height, matrix, true)
} else resized
```

### Writing the result back to disk

```kotlin
file.outputStream().use { out ->
    oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)  // quality 0–100
}
```

The result is written back to the **same file** in-place, so `photoUri` remains valid.

---

## How the app uses it

### `CameraLauncherState` — `@Stable` state holder

```kotlin
@Stable
class CameraLauncherState {
    var photoUri by mutableStateOf<Uri?>(null)         // non-null after a successful capture
        internal set
    var onTakePhoto: () -> Unit = {}                   // set by SideEffect
        internal set
    var showPermissionRationale by mutableStateOf(false)
        internal set
    internal var pendingFile: File? = null             // file created before the camera opens
    internal var photoFile: File? = null               // file backing the current photoUri

    fun clear() { photoFile?.delete(); photoFile = null; photoUri = null }
    fun dismissPermissionRationale() { showPermissionRationale = false }
}
```

`@Stable` tells the Compose compiler that all observable state is backed by `mutableStateOf`, so Compose can safely skip recomposing any composable that receives this instance when `photoUri` has not changed. This is important because `CameraLauncherState` is passed down to the bottom sheet while unrelated state (location, markers) updates frequently.

### `rememberCameraLauncher()` — wiring the full flow

```kotlin
@Composable
fun rememberCameraLauncher(): CameraLauncherState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { CameraLauncherState() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = state.pendingFile
        state.pendingFile = null
        if (!success || file == null) {
            file?.delete()
            state.photoUri = null
        } else {
            scope.launch(Dispatchers.IO) {
                // Resize on IO thread — BitmapFactory and file I/O are blocking
                resizeImageFile(file, maxDimension = 1024, quality = 85)
                state.photoFile = file
                state.photoUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = state.pendingFile
            if (file != null)
                cameraLauncher.launch(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                )
        } else {
            state.pendingFile?.delete()
            state.pendingFile = null
            state.showPermissionRationale = true
        }
    }

    SideEffect {
        state.onTakePhoto = {
            val photoFile = File(context.cacheDir, "camera")
                .also { it.mkdirs() }
                .let { File(it, "photo_${System.currentTimeMillis()}.jpg") }

            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", photoFile
            )
            state.pendingFile = photoFile

            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PermissionChecker.PERMISSION_GRANTED

            if (granted) cameraLauncher.launch(uri)
            else permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return state
}
```

`SideEffect` re-assigns `onTakePhoto` after every recomposition so the lambda always captures the latest `context` and launcher references without restarting the remembered `state` object.

### Why `pendingFile` is not `mutableStateOf`

`pendingFile` is transient — it must not trigger recomposition (doing so would cause the bottom sheet to rebuild mid-flow) and is always cleared before the next interaction. It is declared as a plain `var` inside `CameraLauncherState`.

### `resizeImageFile` — full processing pipeline

```kotlin
fun resizeImageFile(file: File, maxDimension: Int = 1024, quality: Int = 85) {
    // 1. Read EXIF orientation before any decoding (BitmapFactory strips it)
    val exif = ExifInterface(file.absolutePath)
    val rotationDegrees = …  // 0, 90, 180, or 270

    // 2. Decode just the dimensions
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)

    // 3. Early exit if the image already fits
    if (options.outWidth <= maxDimension && options.outHeight <= maxDimension) return

    // 4. Sub-sample + scale
    val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    val resized = sampled.scale(targetWidth, targetHeight)

    // 5. Rotate to correct display orientation
    val oriented = if (rotationDegrees != 0f) Bitmap.createBitmap(…) else resized

    // 6. Write back to the same file
    file.outputStream().use { out -> oriented.compress(JPEG, quality, out) }

    // 7. Recycle bitmaps to release memory
    sampled.recycle(); oriented.recycle()
}
```

This runs on `Dispatchers.IO` inside `rememberCameraLauncher`; `state.photoUri` is assigned only after the file is fully written, so `AsyncImage` never sees a partially-written file.

---

## Permission flow summary

```
User taps "Attach Photo"
        │
        ▼
CAMERA permission granted?
   ├─ YES → create file → get FileProvider URI → launch camera
   └─ NO  → store pendingFile → request CAMERA permission
                │
                ▼
        Permission dialog
           ├─ Granted → re-derive URI from pendingFile → launch camera
           └─ Denied  → delete pendingFile → show rationale UI
```

---

## Minimal Setup Checklist

1. Declare `<uses-permission android:name="android.permission.CAMERA" />` in `AndroidManifest.xml`.
2. Declare `<provider>` for `FileProvider` with matching authority and `file_provider_paths.xml`.
3. Register `ActivityResultContracts.TakePicture` and `ActivityResultContracts.RequestPermission` launchers in a `@Composable` function.
4. Create the destination `File` in the app's cache directory before launching.
5. Wrap capture result processing in `Dispatchers.IO` — `BitmapFactory` and file I/O are blocking.
6. Read EXIF orientation **before** decoding and rotate the bitmap to correct display orientation.
7. Use sub-sampling (`inSampleSize`) to avoid `OutOfMemoryError` on large camera images.
8. Assign the final `Uri` to state only **after** the file is fully written.

