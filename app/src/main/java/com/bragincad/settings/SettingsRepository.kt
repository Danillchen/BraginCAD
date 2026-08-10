package com.bragincad.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

data class AppSettings(
    val theme: Int = THEME_DARK,
    val measurementDecimalPlaces: Int = DEFAULT_DECIMAL_PLACES,
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val DEFAULT_DECIMAL_PLACES = 3
    }
}

/** Single persistent and observable source for application settings. */
class SettingsRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = mutableStateOf(readSettings())

    val settings: State<AppSettings> get() = mutableSettings

    fun setTheme(theme: Int) {
        val normalized = theme.coerceIn(AppSettings.THEME_SYSTEM, AppSettings.THEME_DARK)
        preferences.edit().putInt(KEY_THEME, normalized).apply()
        mutableSettings.value = mutableSettings.value.copy(theme = normalized)
    }

    fun setMeasurementDecimalPlaces(decimalPlaces: Int) {
        val normalized = decimalPlaces.coerceIn(MIN_DECIMAL_PLACES, MAX_DECIMAL_PLACES)
        preferences.edit().putInt(KEY_MEASUREMENT_DECIMAL_PLACES, normalized).apply()
        mutableSettings.value = mutableSettings.value.copy(measurementDecimalPlaces = normalized)
    }

    private fun readSettings(): AppSettings = AppSettings(
        // Keep an explicitly selected legacy theme, but make dark the default for new installs.
        theme = preferences.getInt(KEY_THEME, AppSettings.THEME_DARK)
            .coerceIn(AppSettings.THEME_SYSTEM, AppSettings.THEME_DARK),
        measurementDecimalPlaces = preferences.getInt(
            KEY_MEASUREMENT_DECIMAL_PLACES,
            AppSettings.DEFAULT_DECIMAL_PLACES,
        ).coerceIn(MIN_DECIMAL_PLACES, MAX_DECIMAL_PLACES),
    )

    companion object {
        const val MIN_DECIMAL_PLACES = 0
        const val MAX_DECIMAL_PLACES = 4
        private const val PREFERENCES_NAME = "bragincad"
        private const val KEY_THEME = "theme"
        private const val KEY_MEASUREMENT_DECIMAL_PLACES = "measurement_decimal_places"
    }
}
