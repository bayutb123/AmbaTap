package com.bayutb123.ambatap.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bayutb123.ambatap.service.ServiceBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    serviceBridge: ServiceBridge,
) : ViewModel() {

    val serviceConnected: StateFlow<Boolean> = serviceBridge.controller
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), serviceBridge.controller.value != null)
}
