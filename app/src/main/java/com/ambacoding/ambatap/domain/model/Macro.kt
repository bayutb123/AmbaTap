package com.ambacoding.ambatap.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Satu macro hasil rekaman atau susunan manual.
 *
 * Koordinat di dalam [actions] ternormalisasi (0..1) relatif terhadap [screen],
 * sehingga macro tetap bisa diputar di resolusi lain.
 */
data class Macro(
    val id: Long = 0,
    val name: String,
    val actions: List<MacroAction>,
    val screen: ScreenInfo,
    val config: PlaybackConfig = PlaybackConfig(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ScreenInfo(
    val widthPx: Int,
    val heightPx: Int,
    /** Salah satu konstanta `Surface.ROTATION_*`. */
    val rotation: Int,
)

@Serializable
data class PlaybackConfig(
    val repeat: RepeatMode = RepeatMode.Count(1),
    val speed: Float = 1f,
    val delayBetweenLoopsMs: Long = 0,
    val randomOffsetPx: Int = 0,
    val randomDelayMs: Long = 0,
)

@Serializable
sealed interface RepeatMode {
    @Serializable
    @SerialName("count")
    data class Count(val times: Int) : RepeatMode

    @Serializable
    @SerialName("infinite")
    data object Infinite : RepeatMode

    @Serializable
    @SerialName("duration")
    data class Duration(val millis: Long) : RepeatMode
}

/** Titik ternormalisasi (0..1) dengan offset waktu sejak awal gerakan. */
@Serializable
data class TimedPoint(val x: Float, val y: Float, val tMs: Long)

/** Satu jari dalam gesture multi-touch. */
@Serializable
data class Stroke(val points: List<TimedPoint>, val startDelayMs: Long = 0)

@Serializable
enum class GlobalType { BACK, HOME, RECENTS, NOTIFICATIONS }

@Serializable
sealed interface MacroAction {
    /** Jeda sebelum aksi ini dijalankan, dihitung dari akhir aksi sebelumnya. */
    val delayBeforeMs: Long

    @Serializable
    @SerialName("tap")
    data class Tap(
        val x: Float,
        val y: Float,
        val durationMs: Long = 50,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("long_press")
    data class LongPress(
        val x: Float,
        val y: Float,
        val durationMs: Long,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val points: List<TimedPoint>,
        val durationMs: Long,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("multi_touch")
    data class MultiTouch(
        val strokes: List<Stroke>,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("wait")
    data class Wait(
        val durationMs: Long,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("global")
    data class GlobalAction(
        val action: GlobalType,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(
        val packageName: String,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction

    @Serializable
    @SerialName("input_text")
    data class InputText(
        val text: String,
        override val delayBeforeMs: Long = 0,
    ) : MacroAction
}
