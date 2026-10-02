package com.ambacoding.ambatap.domain.model

/** Lama satu aksi berjalan, tanpa jeda sebelumnya. */
val MacroAction.durationMs: Long
    get() = when (this) {
        is MacroAction.Tap -> durationMs
        is MacroAction.LongPress -> durationMs
        is MacroAction.Swipe -> durationMs
        is MacroAction.MultiTouch -> strokes.maxOfOrNull { s ->
            s.startDelayMs + (s.points.lastOrNull()?.tMs ?: 0) - (s.points.firstOrNull()?.tMs ?: 0)
        } ?: 0
        is MacroAction.Wait -> durationMs
        is MacroAction.GlobalAction,
        is MacroAction.LaunchApp,
        is MacroAction.InputText,
        -> 0
    }

/** Perkiraan durasi satu loop pada kecepatan 1×. */
fun List<MacroAction>.totalDurationMs(): Long = sumOf { it.delayBeforeMs + it.durationMs }
