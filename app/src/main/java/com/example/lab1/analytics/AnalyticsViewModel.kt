package com.example.lab1.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lab1.SafeBoxApp
import com.example.lab1.repository.ScenarioRepository
import com.example.lab1.repository.SensorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The "Analytics" screen: the Room data (Flow) becomes the screen State. */
class AnalyticsViewModel(repository: SensorRepository, scenarios: ScenarioRepository) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    init {
        // Room emits a new list after every INSERT, so the chart updates live
        viewModelScope.launch {
            combine(
                repository.chartRecords, repository.alarmRecords, repository.recordCount, scenarios.guardSettings
            ) { chart, alarms, count, settings -> AnalyticsState(chart, alarms, count, settings.openThreshold) }
                .collect { _state.value = it }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SafeBoxApp).container
                AnalyticsViewModel(container.sensorRepository, container.scenarioRepository)
            }
        }
    }
}
