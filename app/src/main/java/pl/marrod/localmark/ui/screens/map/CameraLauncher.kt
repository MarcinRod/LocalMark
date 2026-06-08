package pl.marrod.localmark.ui.screens.map

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.marrod.localmark.util.resizeImageFile
import java.io.File

/**
 * Stable holder for the camera-capture flow.
 * Owned by [rememberCameraLauncher].
 *
 * @property photoUri Non-null while a photo has been successfully captured.
 *           Cleared by [clear] on sheet dismiss or successful post.
 * @property onTakePhoto Launches the system camera (requesting the CAMERA
 *           permission first if needed). Set internally by [rememberCameraLauncher]
 *           via [SideEffect] after each composition.
 * @property showPermissionRationale `true` when the CAMERA permission was denied.
 *           The UI should show an explanation and offer a link to app settings.
 *           Reset by [dismissPermissionRationale].
 *
 * **Why `@Stable`**
 *
 * `@Stable` is a contract made with the Compose compiler, promising two things:
 * 1. **Equality is stable** — if two instances are equal, they will always be equal.
 * 2. **Change notification** — any publicly readable property that can change is
 *    backed by a [androidx.compose.runtime.MutableState], so Compose is notified
 *    on every mutation (see [photoUri] and [showPermissionRationale]).
 *
 * Without `@Stable` the compiler would treat this class as unstable (it holds a
 * plain `() -> Unit` field for [onTakePhoto]) and would skip recomposition-skipping
 * optimisations for any composable that receives a [CameraLauncherState] — causing
 * unnecessary recompositions on every location update or marker change.
 * With `@Stable`, Compose sees the same remembered instance and can safely skip
 * those composables when [photoUri] has not changed.
 *
 * **Important:** `@Stable` is a promise, not enforcement. The `internal set` on
 * [photoUri] ensures all mutations go through `mutableStateOf`, upholding the
 * contract. Adding a mutable non-State field and mutating it directly would break
 * the contract silently, causing stale UI bugs.
 */
@Stable
class CameraLauncherState {
    var photoUri by mutableStateOf<Uri?>(null)
        internal set

    var onTakePhoto: () -> Unit = {}
        internal set

    /** `true` when the CAMERA permission was denied by the user. */
    var showPermissionRationale by mutableStateOf(false)
        internal set

    /**
     * Transient storage for the file created before the camera (or permission dialog)
     * is launched. Not `mutableStateOf` — it must not trigger recomposition and is
     * always cleared before the next user interaction.
     */
    internal var pendingFile: File? = null

    /**
     * The file that backs the most recently captured photo. Set alongside [photoUri]
     * after a successful capture so that [clear] can delete it from disk.
     */
    internal var photoFile: File? = null

    /** Clears the captured photo URI and deletes the photo file from disk. */
    fun clear() {
        photoFile?.delete()
        photoFile = null
        photoUri = null
    }

    /** Hides the permission rationale message. */
    fun dismissPermissionRationale() { showPermissionRationale = false }
}

/**
 * Creates and remembers a [CameraLauncherState].
 *
 * Registers both the [ActivityResultContracts.TakePicture] launcher and the
 * [ActivityResultContracts.RequestPermission] launcher at the [MapScreenContent]
 * level so the result callbacks are always active even if the OS recreates the
 * activity while the camera or permission dialog is open.
 *
 * **Permission flow**
 *
 * 1. User taps "Attach Photo".
 * 2. [android.Manifest.permission.CAMERA] is checked via [ContextCompat.checkSelfPermission].
 * 3. **Granted** → file is created, FileProvider URI is obtained, camera launches.
 * 4. **Not granted** → the file is stored in [CameraLauncherState.pendingFile], then
 *    the permission request is shown.
 * 5. **Permission granted** → URI is re-derived from [CameraLauncherState.pendingFile] and the camera launches.
 * 6. **Permission denied** → [CameraLauncherState.showPermissionRationale] is set
 *    to `true`; the UI should show an explanation with a link to app settings.
 *
 * After a successful capture the photo is resized on [Dispatchers.IO] before
 * the URI is considered ready for use.
 */
@Composable
fun rememberCameraLauncher(): CameraLauncherState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { CameraLauncherState() }

    // ── Photo capture result ──────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = state.pendingFile
        state.pendingFile = null
        if (!success || file == null) {
            file?.delete()
            state.photoUri = null
        } else {
            scope.launch(Dispatchers.IO) {
                resizeImageFile(file, maxDimension = 1024, quality = 85)
                // Assign photoUri only after the file is fully written and resized,
                // so AsyncImage recomposes exactly once with a ready-to-read file.
                state.photoFile = file
                state.photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        }
    }

    // ── Camera permission result ──────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Launch the camera with the URI re-derived from the pending file.
            val file = state.pendingFile
            if (file != null) {
                cameraLauncher.launch(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                )
            }
        } else {
            state.pendingFile?.delete()
            state.pendingFile = null
            state.showPermissionRationale = true
        }
    }

    // ── onTakePhoto wired via SideEffect ──────────────────────────────────────
    SideEffect {
        state.onTakePhoto = {
            val photoFile = File(context.cacheDir, "camera")
                .also { it.mkdirs() }
                .let { File(it, "photo_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )

            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PermissionChecker.PERMISSION_GRANTED

            state.pendingFile = photoFile
            if (permissionGranted) {
                cameraLauncher.launch(uri)
            } else {
                // Permission will re-derive the URI from pendingFile once granted.
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    return state
}
