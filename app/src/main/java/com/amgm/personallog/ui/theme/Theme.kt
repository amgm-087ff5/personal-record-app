package com.amgm.personallog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.amgm.personallog.ui.design.AppColors
import com.amgm.personallog.ui.design.AppType
import com.amgm.personallog.ui.design.DefaultAppType
import com.amgm.personallog.ui.design.LocalAppColors
import com.amgm.personallog.ui.design.LocalAppType
import com.amgm.personallog.ui.design.appColorsForSystem

/**
 * テーマ「窓辺」。動的カラーは使わない。
 * 画面は AppTheme(ui/design)の値を直接使う。Material の部品(ダイアログ等)が混ざっても
 * 窓辺の配色になるよう、ColorScheme/Typography にも同じ値を渡す。
 */
@Composable
fun PersonalLogTheme(content: @Composable () -> Unit) {
    val colors = appColorsForSystem()
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides DefaultAppType,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = DefaultAppType.toMaterial(),
            content = content,
        )
    }
}

private fun AppColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentWash,
        onPrimaryContainer = ink,
        secondary = inkSoft,
        onSecondary = paper,
        secondaryContainer = accentWash,
        onSecondaryContainer = ink,
        tertiary = moss,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = paperRaised,
        onSurfaceVariant = inkSoft,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = paper,
        surfaceContainerLow = paper,
        surfaceContainer = paperRaised,
        surfaceContainerHigh = paperRaised,
        surfaceContainerHighest = paperRaised,
        outline = inkFaint,
        outlineVariant = line,
        error = danger,
        onError = onAccent,
        scrim = Color(0x99000000),
    )
}

private fun AppType.toMaterial(): Typography {
    val d = Typography()
    return d.copy(
        displayLarge = display,
        displayMedium = display,
        displaySmall = numeralXL,
        headlineLarge = headline,
        headlineMedium = headline,
        headlineSmall = headline,
        titleLarge = title,
        titleMedium = title,
        titleSmall = body,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = body,
        labelMedium = label,
        labelSmall = label,
    )
}
