package com.randaladams.cakeday.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LivingColor = Color(0xFF2E7D32)
val DeceasedColor = Color(0xFF6D5C8A)

private val Light = lightColorScheme(
    primary = Color(0xFF8C4A2F), secondary = Color(0xFF6D5C8A), tertiary = Color(0xFF2E7D32)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59A), secondary = Color(0xFFCFBDF0), tertiary = Color(0xFF8FD694)
)

@Composable
fun CakedayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
