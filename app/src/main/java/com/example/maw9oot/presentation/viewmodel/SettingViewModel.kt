package com.example.maw9oot.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maw9oot.data.local.DataStoreManager
import com.example.maw9oot.data.utils.cancelDailyNotification
import com.example.maw9oot.data.utils.rescheduleAllAlarms
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.maw9oot.data.repository.PrayerTimesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Calendar

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val prayerTimesRepository: PrayerTimesRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val isDarkTheme = dataStoreManager.isDarkTheme
    val language = dataStoreManager.language
    val isSecurityEnabled = dataStoreManager.isSecurityEnabled

    val notificationTime = dataStoreManager.notificationTime
    val isDailyNotificationEnabled = dataStoreManager.isDailyNotificationEnabled

    val isPrayerReminderEnabled = dataStoreManager.isPrayerReminderEnabled
    val prayerReminderDelay = dataStoreManager.prayerReminderDelay

    private val _isPrayerTimesSynced = MutableStateFlow(false)
    val isPrayerTimesSynced: StateFlow<Boolean> = _isPrayerTimesSynced

    init {
        viewModelScope.launch {
            _isPrayerTimesSynced.value = prayerTimesRepository.isPrayerTimesSynced()
        }
    }

    fun syncPrayerTimes() {
        val latitude = 36.402482
        val longitude = 3.323412
        val year: Int = Calendar.getInstance().get(Calendar.YEAR)
        viewModelScope.launch {
            try {
                prayerTimesRepository.fetchAndStorePrayerTimes(latitude, longitude, year)
                _isPrayerTimesSynced.value = prayerTimesRepository.isPrayerTimesSynced()

                // If prayer reminders are enabled, reschedule them with new times
                if (dataStoreManager.isPrayerReminderEnabled.first()) {
                    rescheduleAllAlarms(appContext, dataStoreManager, prayerTimesRepository)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error syncing prayer times: ${e.message}")
            }
        }
    }

    // Theme
    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setDarkTheme(isDark)
        }
    }

    // Language
    fun setLanguage(language: String) {
        viewModelScope.launch {
            dataStoreManager.setLanguage(language)
            updateLocale(language)
            Log.d("SettingsViewModel", "Language set to $language")
        }
    }

    private fun updateLocale(language: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(language)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    // Daily Notification
    fun setNotificationTime(time: String) {
        Log.d("SettingsViewModel", "Notification time set to $time")
        viewModelScope.launch {
            dataStoreManager.setNotificationTime(time)
            if (dataStoreManager.isDailyNotificationEnabled.first()) {
                rescheduleAllAlarms(appContext, dataStoreManager, prayerTimesRepository)
            }
        }
    }

    fun toggleDailyNotification(enabled: Boolean) {
        Log.d("SettingsViewModel", "Daily notification enabled: $enabled")
        viewModelScope.launch {
            dataStoreManager.setDailyNotificationEnabled(enabled)
            if (enabled) {
                rescheduleAllAlarms(appContext, dataStoreManager, prayerTimesRepository)
            } else {
                cancelDailyNotification(appContext)
            }
        }
    }

    // Prayer Reminder
    fun togglePrayerReminder(enabled: Boolean, delayMinutes: String) {
        viewModelScope.launch {
            dataStoreManager.setPrayerReminderEnabled(enabled)
            dataStoreManager.setPrayerReminderDelay(delayMinutes)
            // Always use the robust rescheduleAllAlarms which handles both enable/disable and data fetching
            rescheduleAllAlarms(appContext, dataStoreManager, prayerTimesRepository)
        }
    }


    fun setPrayerReminderDelay(delay: String) {
        viewModelScope.launch {
            dataStoreManager.setPrayerReminderDelay(delay)
            Log.d("SettingsViewModel", "Prayer reminder delay set to $delay")
            if (dataStoreManager.isPrayerReminderEnabled.first()) {
                rescheduleAllAlarms(appContext, dataStoreManager, prayerTimesRepository)
            }
        }
    }

    // Security
    fun enableSecurity(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setSecurityEnabled(enabled)
        }
    }
}
