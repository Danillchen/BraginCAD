package com.bragincad.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

data class AppSettings(
    val theme: Int = THEME_DARK,
    val measurementDecimalPlaces: Int = DEFAULT_DECIMAL_PLACES,
    val measurementTextSizeSp: Int = DEFAULT_MEASUREMENT_TEXT_SIZE,
    val useLayerColors: Boolean = false,
    val drawingBackground: Int = BACKGROUND_THEME,
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val DEFAULT_DECIMAL_PLACES = 3
        const val DEFAULT_MEASUREMENT_TEXT_SIZE = 15
        const val BACKGROUND_THEME = 0
        const val BACKGROUND_BLACK = 1
        const val BACKGROUND_WHITE = 2
        const val BACKGROUND_NAVY = 3
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

    fun setMeasurementTextSize(sizeSp:Int){val normalized=sizeSp.coerceIn(12,24);preferences.edit().putInt(KEY_MEASUREMENT_TEXT_SIZE,normalized).apply();mutableSettings.value=mutableSettings.value.copy(measurementTextSizeSp=normalized)}
    fun setUseLayerColors(enabled:Boolean){preferences.edit().putBoolean(KEY_USE_LAYER_COLORS,enabled).apply();mutableSettings.value=mutableSettings.value.copy(useLayerColors=enabled)}
    fun setDrawingBackground(value:Int){val normalized=value.coerceIn(AppSettings.BACKGROUND_THEME,AppSettings.BACKGROUND_NAVY);preferences.edit().putInt(KEY_DRAWING_BACKGROUND,normalized).apply();mutableSettings.value=mutableSettings.value.copy(drawingBackground=normalized)}
    fun reset(){preferences.edit().remove(KEY_THEME).remove(KEY_MEASUREMENT_DECIMAL_PLACES).remove(KEY_MEASUREMENT_TEXT_SIZE).remove(KEY_USE_LAYER_COLORS).remove(KEY_DRAWING_BACKGROUND).apply();mutableSettings.value=AppSettings()}

    private fun readSettings(): AppSettings = AppSettings(
        // Keep an explicitly selected legacy theme, but make dark the default for new installs.
        theme = preferences.getInt(KEY_THEME, AppSettings.THEME_DARK)
            .coerceIn(AppSettings.THEME_SYSTEM, AppSettings.THEME_DARK),
        measurementDecimalPlaces = preferences.getInt(
            KEY_MEASUREMENT_DECIMAL_PLACES,
            AppSettings.DEFAULT_DECIMAL_PLACES,
        ).coerceIn(MIN_DECIMAL_PLACES, MAX_DECIMAL_PLACES),
        measurementTextSizeSp=preferences.getInt(KEY_MEASUREMENT_TEXT_SIZE,AppSettings.DEFAULT_MEASUREMENT_TEXT_SIZE).coerceIn(12,24),
        useLayerColors=preferences.getBoolean(KEY_USE_LAYER_COLORS,false),
        drawingBackground=preferences.getInt(KEY_DRAWING_BACKGROUND,AppSettings.BACKGROUND_THEME).coerceIn(AppSettings.BACKGROUND_THEME,AppSettings.BACKGROUND_NAVY),
    )

    companion object {
        const val MIN_DECIMAL_PLACES = 0
        const val MAX_DECIMAL_PLACES = 4
        private const val PREFERENCES_NAME = "bragincad"
        private const val KEY_THEME = "theme"
        private const val KEY_MEASUREMENT_DECIMAL_PLACES = "measurement_decimal_places"
        private const val KEY_MEASUREMENT_TEXT_SIZE="measurement_text_size"
        private const val KEY_USE_LAYER_COLORS="use_layer_colors"
        private const val KEY_DRAWING_BACKGROUND="drawing_background"
    }
}
