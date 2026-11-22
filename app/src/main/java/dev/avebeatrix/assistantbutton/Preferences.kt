package dev.avebeatrix.assistantbutton

import android.content.Context

internal class Preferences(context: Context) {
    private val store = context.getSharedPreferences("assistant_button", Context.MODE_PRIVATE)

    val targetPackage: String?
        get() = store.getString("target_package", null)

    val panelClass: String?
        get() = store.getString("panel_class", null)

    fun selectTarget(packageName: String) {
        if (packageName != targetPackage) {
            store.edit().putString("target_package", packageName).remove("panel_class").apply()
        }
    }

    fun savePanel(packageName: String, className: String): Boolean {
        if (packageName != targetPackage) return false
        store.edit().putString("panel_class", className).apply()
        return true
    }

    fun clearPanel() {
        store.edit().remove("panel_class").apply()
    }

    var observeOnly: Boolean
        get() = store.getBoolean("observe_only", false)
        set(value) { store.edit().putBoolean("observe_only", value).apply() }

    var preferAssist: Boolean
        get() = store.getBoolean("prefer_assist", false)
        set(value) { store.edit().putBoolean("prefer_assist", value).apply() }

    var history: String
        get() = store.getString("history", "").orEmpty()
        set(value) { store.edit().putString("history", value).apply() }
}
