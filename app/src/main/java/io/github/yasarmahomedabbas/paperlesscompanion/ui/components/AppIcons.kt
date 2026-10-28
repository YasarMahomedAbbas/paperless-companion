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
    val Search = outline("Search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-3.5-3.5")
    val Close = outline("Close", "M18 6L6 18M6 6l12 12")
    val Back = outline("Back", "M19 12H5M12 19l-7-7 7-7")
    val Plus = outline("Plus", "M12 5v14M5 12h14")
    val ChevronRight = outline("ChevronRight", "M9 6l6 6-6 6")
    val Settings = outline("Settings", GEAR, "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    val Filters = outline(
        "Filters",
        "M4 7h10M18 7h2M4 17h4M12 17h8",
        "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z",
        "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z",
    )
    val Sort = outline("Sort", "M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4")
    val Scan = outline("Scan", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10")
    val Camera = outline("Camera", "M4 8h3l2-3h6l2 3h3v11H4z", "M12 9.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z")
    val File = outline("File", "M6 3h8l5 5v13H6z", "M14 3v5h5")
    val Person = outline("Person", "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M4 21c1-4 4.5-6 8-6s7 2 8 6")
    val Server = outline(
        "Server",
        "M5 4h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M5 13h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        "M7 7.5v.01M7 16.5v.01",
    )
}

private const val EYE = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"
private const val GEAR = "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
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
