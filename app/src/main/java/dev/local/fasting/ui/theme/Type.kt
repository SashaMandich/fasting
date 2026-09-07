package dev.local.fasting.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * One sans family throughout, including the hero timer — a display face on a big number reads as
 * decoration. Tabular figures are reserved for columns that must align (table views, axis ticks).
 */
val FastingTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Hero figure: the one number a screen leads with. */
val HeroFigureStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    letterSpacing = (-1.5).sp,
    textAlign = TextAlign.Center,
)
