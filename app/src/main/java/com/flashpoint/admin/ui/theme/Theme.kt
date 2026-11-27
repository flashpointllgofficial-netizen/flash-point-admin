package com.flashpoint.admin.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight

val Navy = Color(0xFF0B1B3A)
val Cyan = Color(0xFF00E5FF)

private val Light = lightColorScheme(primary = Color(0xFF007C91), onPrimary = Color.White,
    secondary = Navy, background = Color(0xFFF5F8FB), surface = Color.White)
private val Dark = darkColorScheme(primary = Cyan, onPrimary = Navy,
    background = Navy, surface = Color(0xFF12274D))

@Composable
fun FlashPointTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)

/** Shows the supplied res/drawable/app_logo.png. Never fabricates a logo: text fallback if absent. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val id = remember { ctx.resources.getIdentifier("app_logo", "drawable", ctx.packageName) }
    if (id != 0) Image(painterResource(id), "FLASH POINT logo", modifier)
    else Text("FLASH POINT", modifier, MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
}
