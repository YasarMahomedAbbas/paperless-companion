package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The outlined icons the canvas draws that `Icons.Rounded` (material-icons-core) doesn't have,
 * as 24dp stroke paths. The extended icon set would add megabytes to an unminified APK for a
 * handful of glyphs. `Icon` tints them, so the stroke colour here is only a placeholder.
 */
object AppIcons {
    val Document = outline("Document", "M6 3h8l5 5v13H6z", "M14 3v5h5M9 13h6M9 17h4")
    val Visibility = outline("Visibility", EYE, "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    val VisibilityOff = outline("VisibilityOff", EYE, "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M3 3l18 18")
    val WifiOff = outline("WifiOff", "M2 9a15 15 0 0 1 20 0M5.5 12.5a10 10 0 0 1 13 0M9 16a5 5 0 0 1 6 0M12 19.5v.01M3 3l18 18")
    val Alert = outline("Alert", CIRCLE, "M12 8v5M12 16.5v.01")
    val Info = outline("Info", CIRCLE, "M12 11v5M12 7.5v.01")
    val Warning = outline("Warning", "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4M12 17v.01")
    val Check = outline("Check", "M5 12l5 5 9-10")
    val Download = outline("Download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val OpenExternal = outline("OpenExternal", "M14 4h6v6M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
}

private const val EYE = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"
private const val CIRCLE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"

private fun outline(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()
