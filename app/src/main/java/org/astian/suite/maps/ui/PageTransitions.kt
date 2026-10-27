package org.astian.suite.maps.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** Persisted motion preference; back navigation remains available when motion is disabled. */
object PageTransitions {
    val enabled = mutableStateOf(true)

    fun init(context: Context) {
        enabled.value = context.getSharedPreferences("astian_settings", Context.MODE_PRIVATE)
            .getBoolean("page_transitions", true)
    }

    fun set(context: Context, value: Boolean) {
        enabled.value = value
        context.getSharedPreferences("astian_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("page_transitions", value).apply()
    }
}
