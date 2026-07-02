package app.lawnchairlite.ui

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.roundToInt

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter =
    remember(drawable) { StaticDrawablePainter(drawable) }

private class StaticDrawablePainter(private val drawable: Drawable?) : Painter() {
    override val intrinsicSize: Size
        get() {
            val width = drawable?.intrinsicWidth ?: -1
            val height = drawable?.intrinsicHeight ?: -1
            return if (width > 0 && height > 0) Size(width.toFloat(), height.toFloat()) else Size.Unspecified
        }

    override fun DrawScope.onDraw() {
        val icon = drawable ?: return
        drawIntoCanvas { canvas ->
            icon.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            icon.draw(canvas.nativeCanvas)
        }
    }
}
