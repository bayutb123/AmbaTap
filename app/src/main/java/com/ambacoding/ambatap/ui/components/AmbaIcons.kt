package com.ambacoding.ambatap.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ikon 24dp sesuai desain; warnanya mengikuti `tint` pada `Icon`. */
object AmbaIcons {
    val Back: ImageVector by lazy {
        strokeIcon("Back") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }

    val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            moveTo(4f, 7f); lineTo(13f, 7f)
            moveTo(17f, 7f); lineTo(20f, 7f)
            moveTo(4f, 17f); lineTo(7f, 17f)
            moveTo(11f, 17f); lineTo(20f, 17f)
            circle(15f, 7f, 2f)
            circle(9f, 17f, 2f)
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    /** Panah keluar dari kotak: buka aplikasi AmbaTap. */
    val OpenApp: ImageVector by lazy {
        strokeIcon("OpenApp") {
            moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
            moveTo(20f, 4f); lineTo(11f, 13f)
            moveTo(18f, 14f); lineTo(18f, 19f); lineTo(5f, 19f); lineTo(5f, 6f); lineTo(10f, 6f)
        }
    }

    val Play: ImageVector by lazy {
        fillIcon("Play") {
            moveTo(8f, 5.5f); lineTo(8f, 18.5f); lineTo(19f, 12f); close()
        }
    }

    val Pause: ImageVector by lazy {
        fillIcon("Pause") {
            rect(6f, 5f, 4f, 14f)
            rect(14f, 5f, 4f, 14f)
        }
    }

    val Record: ImageVector by lazy {
        fillIcon("Record") {
            circle(12f, 12f, 7f)
        }
    }

    val More: ImageVector by lazy {
        fillIcon("More") {
            circle(12f, 5f, 1.8f)
            circle(12f, 12f, 1.8f)
            circle(12f, 19f, 1.8f)
        }
    }

    val Target: ImageVector by lazy {
        strokeIcon("Target") {
            circle(12f, 12f, 7f)
            circle(12f, 12f, 2.5f)
            moveTo(12f, 1.5f); lineTo(12f, 4.5f)
            moveTo(12f, 19.5f); lineTo(12f, 22.5f)
            moveTo(1.5f, 12f); lineTo(4.5f, 12f)
            moveTo(19.5f, 12f); lineTo(22.5f, 12f)
        }
    }

    val Plus: ImageVector by lazy {
        strokeIcon("Plus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Swipe: ImageVector by lazy {
        strokeIcon("Swipe") {
            moveTo(4f, 12f); lineTo(19f, 12f)
            moveTo(14f, 7f); lineTo(19f, 12f); lineTo(14f, 17f)
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("Check") {
            moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7.5f)
        }
    }

    val Repeat: ImageVector by lazy {
        strokeIcon("Repeat") {
            moveTo(17f, 3f); lineTo(20f, 6f); lineTo(17f, 9f)
            moveTo(4f, 11f); lineTo(4f, 9f); arcTo(3f, 3f, 0f, false, true, 7f, 6f); lineTo(20f, 6f)
            moveTo(7f, 21f); lineTo(4f, 18f); lineTo(7f, 15f)
            moveTo(20f, 13f); lineTo(20f, 15f); arcTo(3f, 3f, 0f, false, true, 17f, 18f); lineTo(4f, 18f)
        }
    }

    val Stop: ImageVector by lazy {
        fillIcon("Stop") {
            rect(6f, 6f, 12f, 12f)
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
    }

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    }

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun strokeIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        builder(name).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun fillIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        builder(name).path(fill = SolidColor(Color.Black), pathBuilder = block).build()
}
