package dev.avebeatrix.assistantbutton

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

internal data class PanelCandidate(val packageName: String, val className: String)

class PanelAccessibilityService : AccessibilityService() {
    private val settings by lazy { Preferences(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val classesByWindow = mutableMapOf<Int, String>()
    private var trackedPackage: String? = null
    private var setup: PanelSetup? = null

    private data class PanelSetup(
        val packageName: String,
        val launcherClasses: Set<String>,
        val deadline: Long,
        var candidate: PanelCandidate? = null
    )

    private data class FocusedWindow(val id: Int, val rootClass: String?)

    override fun onServiceConnected() {
        classesByWindow.clear()
        connected = this
        Diagnostics.write(this, "accessibility=connected")
    }

    internal fun startSetup(targetPackage: String) {
        val launcher = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(targetPackage)
        val launcherClasses = packageManager.queryIntentActivities(launcher, 0)
            .flatMap { listOfNotNull(it.activityInfo.name, it.activityInfo.targetActivity) }
            .toSet()
        setup = PanelSetup(targetPackage, launcherClasses, SystemClock.uptimeMillis() + 12_000L)
        Diagnostics.write(this, "panel_setup=started")
    }

    internal fun cancelSetup() {
        setup = null
    }

    internal fun candidate(): PanelCandidate? =
        setup?.candidate?.takeIf { it.packageName == settings.targetPackage }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.windowId < 0) return

        val target = currentTarget() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val changes = event.windowChanges
            if (changes and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0) {
                val removedClass = classesByWindow.remove(event.windowId)
                if (removedClass != null && removedClass == settings.panelClass) {
                    Diagnostics.write(this, "panel_window=removed id=${event.windowId}")
                }
            }
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val activityClass = event.className?.toString()
        if (event.packageName?.toString() != target || activityClass == null) {
            classesByWindow.remove(event.windowId)
            return
        }

        classesByWindow[event.windowId] = activityClass
        val pending = setup ?: return
        if (pending.packageName != target || pending.candidate != null ||
            SystemClock.uptimeMillis() > pending.deadline || activityClass in pending.launcherClasses ||
            !isRegisteredActivity(target, activityClass)
        ) return

        val windowId = event.windowId
        handler.postDelayed({
            if (setup === pending && pending.candidate == null &&
                settings.targetPackage == target && SystemClock.uptimeMillis() <= pending.deadline
            ) {
                val focused = focusedWindow(target)
                if (focused?.id == windowId && matches(focused, activityClass)) {
                    pending.candidate = PanelCandidate(target, activityClass)
                    Diagnostics.write(this, "panel_setup=candidate_captured")
                }
            }
        }, 350)
    }

    private fun isRegisteredActivity(targetPackage: String, className: String): Boolean =
        try {
            packageManager.getActivityInfo(ComponentName(targetPackage, className), 0).enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    internal fun closePanelIfFocused(targetPackage: String, className: String?): Boolean {
        if (className == null || currentTarget() != targetPackage) {
            Diagnostics.write(this, "decision=no_back reason=panel_not_configured")
            return false
        }

        // Re-check live focus and the root package before using the global Back action.
        val focused = focusedWindow(targetPackage)
        if (focused == null || !matches(focused, className)) {
            Diagnostics.write(this, "decision=no_back reason=panel_not_confirmed_in_focus")
            return false
        }

        val accepted = performGlobalAction(GLOBAL_ACTION_BACK)
        Diagnostics.write(this, "decision=back window=${focused.id} accepted=$accepted")
        return true
    }

    private fun currentTarget(): String? {
        val target = settings.targetPackage
        if (target != trackedPackage) {
            classesByWindow.clear()
            trackedPackage = target
        }
        return target
    }

    private fun focusedWindow(targetPackage: String): FocusedWindow? {
        val windows = if (Build.VERSION.SDK_INT >= 30) {
            windowsOnAllDisplays[Display.DEFAULT_DISPLAY].orEmpty()
        } else {
            windows
        }
        val focused = windows.singleOrNull { it.isFocused } ?: return null
        if (!focused.isActive || focused.type != AccessibilityWindowInfo.TYPE_APPLICATION) return null
        val root = focused.root ?: return null
        if (root.windowId != focused.id || root.packageName?.toString() != targetPackage) return null
        return FocusedWindow(focused.id, root.className?.toString())
    }

    private fun matches(window: FocusedWindow, className: String): Boolean =
        window.rootClass == className || classesByWindow[window.id] == className

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (connected === this) {
            connected = null
            Diagnostics.write(this, "accessibility=disconnected")
        }
        super.onDestroy()
    }

    internal companion object {
        var connected: PanelAccessibilityService? = null
            private set
    }
}
