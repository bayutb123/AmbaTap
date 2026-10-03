package com.ambacoding.ambatap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ambacoding.ambatap.R

// Ketiganya variable font (OFL, lisensi di assets/licenses); tiap bobot memilih sumbu wght.
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Space Grotesk: judul dan logo. */
private val DisplayFamily = FontFamily(
    variableFont(R.font.space_grotesk, FontWeight.Medium),
    variableFont(R.font.space_grotesk, FontWeight.Bold),
)

/** IBM Plex Sans: teks dan tombol. */
private val BodyFamily = FontFamily(
    variableFont(R.font.ibm_plex_sans, FontWeight.Normal),
    variableFont(R.font.ibm_plex_sans, FontWeight.Medium),
    variableFont(R.font.ibm_plex_sans, FontWeight.SemiBold),
)

/** JetBrains Mono: koordinat, durasi, dan hitungan. */
private val MonoFamily = FontFamily(
    variableFont(R.font.jetbrains_mono, FontWeight.Normal),
    variableFont(R.font.jetbrains_mono, FontWeight.Medium),
)

/** Gaya untuk koordinat, durasi, dan hitungan (mis. "540, 1820", "+350 ms"). */
val MonoStyle = TextStyle(
    fontFamily = MonoFamily,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val AmbaTapTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
