package pl.marrod.localmark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typography scale derived from the Tailwind config.
 *
 * Tailwind token → Material3 slot mapping:
 *   body-lg      → bodyLarge
 *   body-md      → bodyMedium
 *   headline-lg  → headlineLarge
 *   headline-md  → headlineMedium
 *   label-caps   → labelSmall  (12 sp, spaced-caps style)
 *   marker-label → labelMedium (13 sp, tight-tracking label)
 */
val Typography = Typography(

    // body-lg  – 16 sp / 24 sp / weight 400
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.sp
    ),

    // body-md  – 14 sp / 20 sp / weight 400
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.sp
    ),

    // headline-lg  – 28 sp / 34 sp / weight 700
    headlineLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        letterSpacing = (-0.02).em
    ),

    // headline-md  – 22 sp / 28 sp / weight 600
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = (-0.01).em
    ),

    // label-caps  – 12 sp / 16 sp / weight 600 / +0.08 em tracking
    labelSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.08.em
    ),

    // marker-label  – 13 sp / 16 sp / weight 600 / −0.01 em tracking
    labelMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 13.sp,
        lineHeight   = 16.sp,
        letterSpacing = (-0.01).em
    ),
)