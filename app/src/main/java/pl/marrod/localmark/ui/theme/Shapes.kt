package pl.marrod.localmark.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3 Shapes wired into [LocalMarkTheme].
 *
 * Tailwind borderRadius token → Material3 slot:
 *   DEFAULT (4 dp)   → extraSmall
 *   lg      (8 dp)   → small
 *   xl      (12 dp)  → medium
 *   full    (9999 dp) → large / extraLarge (pill/full)
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(9999.dp),
    extraLarge = RoundedCornerShape(9999.dp),
)

// ── Legacy component shapes (kept for existing usages) ────────────────────────
object CardShapes {
    val extraSmall = RoundedCornerShape(12.dp)
    val small      = RoundedCornerShape(16.dp)
    val normal     = RoundedCornerShape(24.dp)
    val large      = RoundedCornerShape(32.dp)
}

object ButtonShapes {
    val extraSmall = RoundedCornerShape(8.dp)
    val small      = RoundedCornerShape(12.dp)
    val normal     = RoundedCornerShape(16.dp)
    val large      = RoundedCornerShape(24.dp)
}

object ChipShapes {
    val extraSmall = RoundedCornerShape(12.dp)
    val small      = RoundedCornerShape(16.dp)
    val normal     = RoundedCornerShape(28.dp)
    val large      = RoundedCornerShape(32.dp)
}

object TagShapes {
    val extraSmall = RoundedCornerShape(4.dp)
    val small      = RoundedCornerShape(8.dp)
    val normal     = RoundedCornerShape(12.dp)
    val large      = RoundedCornerShape(16.dp)
}

val TextFieldShape = RoundedCornerShape(16.dp)
val BarShape       = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
