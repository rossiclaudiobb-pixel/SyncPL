package com.example.myplaylistsync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myplaylistsync.data.Settings
import com.example.myplaylistsync.data.SettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.first().let { initialSettings ->
                _settings.value = initialSettings
            }
        }

        _settings
            .debounce(500)
            .onEach { repository.updateSettings(it) }
            .launchIn(viewModelScope)
    }

    fun updateHost(value: String) { _settings.value = _settings.value.copy(remoteHost = value) }
    fun updateHttpPort(value: String) { _settings.value = _settings.value.copy(httpPort = value) }
    fun updateFilePath(value: String) { _settings.value = _settings.value.copy(filePath = value) }
    fun updateLocalPath(value: String) { _settings.value = _settings.value.copy(localPathUri = value) }
    fun updateIsPremium(value: Boolean) { _settings.value = _settings.value.copy(isPremium = value) }
}
