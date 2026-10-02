package com.ambacoding.ambatap.ui.playground

import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.model.TimedPoint

/**
 * Macro contoh untuk menguji pemutaran. Semua titik berada di separuh bawah layar
 * agar mendarat di kanvas Playground, bukan di tombol kontrol.
 */
object PlaygroundMacros {

    private val tapPoints = listOf(
        0.2f to 0.55f,
        0.5f to 0.55f,
        0.8f to 0.55f,
        0.35f to 0.68f,
        0.65f to 0.68f,
    )
    private val longPressPoint = 0.5f to 0.62f
    private val swipeStart = 0.15f to 0.8f
    private val swipeEnd = 0.85f to 0.8f

    /** Titik yang seharusnya disentuh (koordinat ternormalisasi), untuk mengukur akurasi. */
    val targets: List<Pair<Float, Float>> = tapPoints + longPressPoint + swipeStart + swipeEnd

    private val tapActions = tapPoints.mapIndexed { i, (x, y) ->
        MacroAction.Tap(x, y, delayBeforeMs = if (i == 0) 0 else 300)
    }

    private val swipeAction = MacroAction.Swipe(
        points = listOf(
            TimedPoint(swipeStart.first, swipeStart.second, 0),
            TimedPoint(0.5f, 0.79f, 200),
            TimedPoint(swipeEnd.first, swipeEnd.second, 400),
        ),
        durationMs = 400,
        delayBeforeMs = 300,
    )

    private val longPressAction = MacroAction.LongPress(
        x = longPressPoint.first,
        y = longPressPoint.second,
        durationMs = 800,
        delayBeforeMs = 300,
    )

    val tap = macro("Uji tap", tapActions)
    val swipe = macro("Uji swipe", listOf(swipeAction))
    val longPress = macro("Uji long press", listOf(longPressAction))
    val combined = macro(
        name = "Uji gabungan",
        actions = tapActions + swipeAction + longPressAction,
        config = PlaybackConfig(repeat = RepeatMode.Count(3), delayBetweenLoopsMs = 500),
    )

    /** Scroll ke atas 3×; aman dicoba di aplikasi lain (feed, daftar, browser). */
    val scrollOtherApp = macro(
        name = "Scroll di aplikasi lain",
        actions = listOf(
            MacroAction.Swipe(
                points = listOf(TimedPoint(0.5f, 0.75f, 0), TimedPoint(0.5f, 0.35f, 350)),
                durationMs = 350,
            ),
        ),
        config = PlaybackConfig(repeat = RepeatMode.Count(3), delayBetweenLoopsMs = 800),
    )

    val inApp = listOf(tap, swipe, longPress, combined)

    private fun macro(
        name: String,
        actions: List<MacroAction>,
        config: PlaybackConfig = PlaybackConfig(),
    ) = Macro(
        name = name,
        actions = actions,
        // Metadata saja; koordinat ternormalisasi menyesuaikan layar saat diputar.
        screen = ScreenInfo(widthPx = 1080, heightPx = 2400, rotation = 0),
        config = config,
        createdAt = 0,
        updatedAt = 0,
    )
}
