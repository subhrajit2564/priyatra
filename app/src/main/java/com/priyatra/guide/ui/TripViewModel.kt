package com.priyatra.guide.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.priyatra.guide.feedback.FeedbackStore
import com.priyatra.guide.weather.WeatherClient
import com.priyatra.guide.weather.WeatherNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripViewModel(application: Application) : AndroidViewModel(application) {
    private val weatherClient = WeatherClient()
    private val feedbackStore = FeedbackStore(application)

    private val _weather = MutableStateFlow<WeatherNow?>(null)
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

    fun refreshWeather(lat: Double, lng: Double) {
        viewModelScope.launch {
            _weather.value = runCatching { weatherClient.fetch(lat, lng) }.getOrNull()
        }
    }

    fun shouldAskFeedback(spotId: String): Boolean = !feedbackStore.hasFeedback(spotId)

    fun saveFeedback(spotId: String, stars: Int, note: String) {
        feedbackStore.save(spotId, stars, note)
    }
}
