package com.taurus.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary   = androidx.compose.ui.graphics.Color(0xFF90CAF9),
    secondary = androidx.compose.ui.graphics.Color(0xFF80CBC4),
    background= androidx.compose.ui.graphics.Color(0xFF0D1117),
    surface   = androidx.compose.ui.graphics.Color(0xFF161B22),
)

private val LightColors = lightColorScheme(
    primary   = androidx.compose.ui.graphics.Color(0xFF1565C0),
    secondary = androidx.compose.ui.graphics.Color(0xFF00695C),
    background= androidx.compose.ui.graphics.Color(0xFFF5F5F5),
    surface   = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
)

@Composable
fun TaurusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content,
    )
}
