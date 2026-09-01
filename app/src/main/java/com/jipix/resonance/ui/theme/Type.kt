package com.jipix.resonance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jipix.resonance.R

/**
 * The system sans (Roboto) is what every first-party Google app falls back to,
 * so it is the right default here. Headlines are tightened slightly and pulled
 * to Medium, which is the main thing that separates the Google-app look from
 * stock Material defaults.
 */
private val Sans = FontFamily.Default

/**
 * Used only for the wordmark in the drawer. A retro script has no business in a
 * list of tracks, but it gives the app a signature the stock type scale cannot.
 */
val WordmarkFont = FontFamily(Font(R.font.pacifico_regular))

/**
 * For tab labels and section headings only.
 *
 * Quicksand shares Pacifico's rounded terminals and geometric bones without
 * being a script, so the two read as related rather than as two unrelated
 * choices. It goes nowhere near track titles or body copy: a display face
 * applied to a list of two thousand songs stops being character and starts
 * being an obstacle.
 */
val DisplayFont = FontFamily(Font(R.font.quicksand_variable, FontWeight.Medium))

val ResonanceTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
