package com.shadow306.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadow306.app.core.modeldownload.ModelRepository
import com.shadow306.app.core.modeldownload.PackDownloadState
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(context: Context) : ViewModel() {

    private val repository: ModelRepository = ServiceLocator.provideModelRepository(context)
    private val prefs = ServiceLocator.providePrefs(context)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val packState: StateFlow<PackDownloadState> = repository.state

    init {
        viewModelScope.launch {
            repository.loadBundledManifest()
            repository.refreshVerification()
        }
        viewModelScope.launch {
            prefs.setupDoneFlow.collect { done ->
                _uiState.value = _uiState.value.copy(setupDone = done)
            }
        }
    }

    data class HomeUiState(
        val setupDone: Boolean = false,
        val showPrivacyNotice: Boolean = true
    )
}
