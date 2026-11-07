package com.intentgate.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.intentgate.app.R
import com.intentgate.app.data.AppDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun appPainter(app: AppDefinition): Painter {
    val context = LocalContext.current
    val loaded = produceState<ImageBitmap?>(null, context, app) {
        value = null
        value = withContext(Dispatchers.IO) {
            // Launcher icons can be adaptive, layered, or supplied by another package.
            try {
                val drawable = loadInstalledIcon(app.packageName, app.installed, app.systemIcon) { packageName ->
                    context.packageManager.getApplicationIcon(packageName)
                }
                drawable?.let(::rasterizeIcon)
            } catch (_:RuntimeException) {
                null
            } catch (_:android.content.pm.PackageManager.NameNotFoundException) {
                null
            }
        }
    }.value
    return if (loaded != null) remember(loaded) { BitmapPainter(loaded) }
    else painterResource(R.drawable.ic_app_icon_placeholder)
}

/** Unknown installation state is resolved by PackageManager; known missing apps use the placeholder. */
internal fun <Icon> loadInstalledIcon(
    packageName: String,
    installed: Boolean?,
    cachedIcon: Icon?,
    load: (String) -> Icon?,
): Icon? {
    if (installed == false || packageName.isBlank()) return null
    return cachedIcon ?: load(packageName)
}

private fun rasterizeIcon(drawable: Drawable): ImageBitmap {
    val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
    synchronized(drawable) {
        val bounds = Rect(drawable.bounds)
        try {
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
        } finally {
            drawable.bounds = bounds
        }
    }
    return bitmap.asImageBitmap()
}
