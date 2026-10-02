package com.ambacoding.ambatap.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object OnboardingRoute

@Serializable
data object SettingsRoute

@Serializable
data class EditorRoute(val macroId: Long)

@Serializable
data object PlaygroundRoute
