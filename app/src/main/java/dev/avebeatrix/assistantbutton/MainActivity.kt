package dev.avebeatrix.assistantbutton

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val settings by lazy { Preferences(this) }
    private lateinit var assistantStatus: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var panelStatus: TextView
    private lateinit var confirmButton: Button
    private lateinit var resetButton: Button
    private lateinit var journal: TextView
    private var setupStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val side = (20 * resources.displayMetrics.density).toInt()
            setPadding(side, side, side, side)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        @Suppress("DEPRECATION")
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safeArea = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            } else {
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }

        assistantStatus = content.addText()
        content.addButton(R.string.choose_assistant, ::chooseAssistant)
        content.addButton(R.string.manual_launch) { launchManually(learnPanel = false) }
        serviceStatus = content.addText()
        content.addButton(R.string.accessibility_settings) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        panelStatus = content.addText()
        content.addButton(R.string.learn_panel, ::showSetupInstructions)
        confirmButton = content.addButton(R.string.confirm_panel, ::confirmPanel)
        resetButton = content.addButton(R.string.reset_panel) {
            settings.clearPanel()
            PanelAccessibilityService.connected?.cancelSetup()
            setupStarted = false
            refresh()
        }
        content.addButton(R.string.assistant_settings) { openAssistantSettings() }
        content.addView(CheckBox(this).apply {
            setText(R.string.prefer_assist)
            isChecked = settings.preferAssist
            setOnCheckedChangeListener { _, checked -> settings.preferAssist = checked }
        })
        content.addView(CheckBox(this).apply {
            setText(R.string.observe_only)
            isChecked = settings.observeOnly
            setOnCheckedChangeListener { _, checked -> settings.observeOnly = checked }
        })

        content.addButton(R.string.refresh_journal, ::refresh)
        journal = content.addText().apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun chooseAssistant() {
        val options = assistantOptions()
        if (options.isEmpty()) {
            notice(R.string.no_assistants_found)
            return
        }
        val selected = options.indexOfFirst { it.packageName == settings.targetPackage }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_assistant)
            .setSingleChoiceItems(options.map { it.label }.toTypedArray(), selected) { dialog, position ->
                settings.selectTarget(options[position].packageName)
                PanelAccessibilityService.connected?.cancelSetup()
                setupStarted = false
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAssistantSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun showSetupInstructions() {
        if (settings.targetPackage == null) {
            notice(R.string.no_assistant_selected)
            return
        }
        if (PanelAccessibilityService.connected == null) {
            notice(R.string.enable_accessibility)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.learn_panel)
            .setMessage(R.string.setup_instructions)
            .setPositiveButton(R.string.open_panel) { _, _ -> launchManually(learnPanel = true) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchManually(learnPanel: Boolean) {
        val target = settings.targetPackage
        if (target == null) {
            notice(R.string.no_assistant_selected)
            return
        }

        val service = PanelAccessibilityService.connected
        if (learnPanel) {
            if (service == null) {
                notice(R.string.enable_accessibility)
                return
            }
            setupStarted = true
            service.startSetup(target)
        }
        Diagnostics.write(this, "incoming source=manual_button")
        if (!launchAssistant(target, settings.preferAssist)) {
            if (learnPanel) {
                setupStarted = false
                service?.cancelSetup()
            }
            notice(R.string.cannot_launch_assistant)
            refresh()
        }
    }

    private fun confirmPanel() {
        val service = PanelAccessibilityService.connected ?: return
        val candidate = service.candidate() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_panel)
            .setMessage(R.string.confirm_instructions)
            .setPositiveButton(R.string.save_panel) { _, _ ->
                if (service.candidate() == candidate) {
                    settings.savePanel(candidate.packageName, candidate.className)
                    Diagnostics.write(this, "panel_setup=confirmed")
                }
                service.cancelSetup()
                setupStarted = false
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        val service = PanelAccessibilityService.connected
        val candidate = service?.candidate()
        val serviceState = if (service != null) R.string.connected else R.string.disconnected
        serviceStatus.text = getString(R.string.accessibility_status, getString(serviceState))
        val label = settings.targetPackage?.let(::packageLabel)
            ?: getString(R.string.no_assistant_selected)
        assistantStatus.text = getString(R.string.assistant_status, label)
        panelStatus.setText(
            when {
                candidate != null -> R.string.panel_candidate
                setupStarted -> R.string.panel_not_detected
                settings.panelClass != null -> R.string.panel_configured
                else -> R.string.panel_not_configured
            }
        )
        confirmButton.visibility = if (candidate != null) View.VISIBLE else View.GONE
        resetButton.visibility = if (settings.panelClass != null) View.VISIBLE else View.GONE
        journal.text = settings.history.ifEmpty { getString(R.string.no_commands) }
    }

    private fun packageLabel(name: String): String =
        try {
            packageManager.getApplicationInfo(name, 0).loadLabel(packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            name
        }

    private fun notice(label: Int) {
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun LinearLayout.addText(): TextView = TextView(this@MainActivity).also { addView(it) }

    private fun LinearLayout.addButton(label: Int, action: () -> Unit): Button =
        Button(this@MainActivity).apply {
            setText(label)
            setOnClickListener { action() }
            this@addButton.addView(this)
        }
}
