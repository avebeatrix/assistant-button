package dev.avebeatrix.assistantbutton

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager

internal data class AssistantOption(val packageName: String, val label: String)

internal fun Context.assistantOptions(): List<AssistantOption> {
    val actions = listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)
    return actions.flatMap { action ->
        packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
    }.asSequence()
        .filter { it.activityInfo.exported && it.activityInfo.packageName != packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AssistantOption(
                it.activityInfo.packageName,
                it.activityInfo.applicationInfo.loadLabel(packageManager).toString()
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

internal fun Activity.launchAssistant(targetPackage: String, preferAssist: Boolean): Boolean {
    if (targetPackage == packageName) return false
    val actions = if (preferAssist) {
        listOf(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND)
    } else {
        listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)
    }

    for (action in actions) {
        val intent = Intent(action).setPackage(targetPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            Diagnostics.write(this, "launch=requested action=${action.substringAfterLast('.')}")
            return true
        } catch (error: ActivityNotFoundException) {
            Diagnostics.write(this, "launch=failed action=$action error=${error.javaClass.simpleName}")
        } catch (error: SecurityException) {
            Diagnostics.write(this, "launch=failed action=$action error=${error.javaClass.simpleName}")
        }
    }
    return false
}

class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleCommand()
        } finally {
            finish()
        }
    }

    private fun handleCommand() {
        val action = intent.action
        if (action != Intent.ACTION_VOICE_COMMAND && action != Intent.ACTION_ASSIST) return

        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val callerUid = if (Build.VERSION.SDK_INT >= 34) launchedFromUid else -1
        Diagnostics.write(
            this,
            "incoming action=${action.substringAfterLast('.')} callerUid=$callerUid " +
                "interactive=${power.isInteractive} keyguard=${keyguard.isKeyguardLocked} " +
                "deviceLocked=${keyguard.isDeviceLocked}"
        )

        val settings = Preferences(this)
        if (settings.observeOnly) {
            Diagnostics.write(this, "decision=observe_only")
            return
        }
        val target = settings.targetPackage
        if (target == null) {
            Diagnostics.write(this, "decision=skip reason=assistant_not_selected")
            return
        }

        val service = PanelAccessibilityService.connected
        val handled = service?.closePanelIfFocused(target, settings.panelClass)
        if (handled == null) Diagnostics.write(this, "decision=no_back reason=service_unavailable")
        if (handled != true) launchAssistant(target, settings.preferAssist)
    }
}
