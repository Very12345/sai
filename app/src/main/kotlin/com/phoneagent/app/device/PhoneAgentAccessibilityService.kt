package com.phoneagent.app.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.time.Instant

data class AccessibleNodeSnapshot(
    val id: Int,
    val className: String,
    val text: String,
    val description: String,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: String,
)

class PhoneAgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { DeviceControlAuthorization.revoke() }

    fun observe(): List<AccessibleNodeSnapshot> {
        val packageName = rootInActiveWindow?.packageName?.toString() ?: return emptyList()
        DeviceControlAuthorization.require(packageName)
        val output = mutableListOf<AccessibleNodeSnapshot>()
        var id = 0
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            if (!node.isPassword) {
                val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                output += AccessibleNodeSnapshot(
                    id = id++,
                    className = node.className?.toString().orEmpty(),
                    text = node.text?.toString().orEmpty().take(500),
                    description = node.contentDescription?.toString().orEmpty().take(500),
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    bounds = bounds.toShortString(),
                )
            }
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(rootInActiveWindow)
        audit("observe", packageName, true)
        return output.take(2_000)
    }

    fun click(nodeId: Int, finalSubmit: Boolean = false): Boolean = withNode(nodeId, finalSubmit = finalSubmit) { node ->
        node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun input(nodeId: Int, text: String): Boolean = withNode(nodeId, textInput = true) { node ->
        if (node.isPassword || !node.isEditable) false
        else node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMillis: Long = 450): Boolean {
        val packageName = rootInActiveWindow?.packageName?.toString() ?: return false
        DeviceControlAuthorization.require(packageName)
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val result = dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMillis)).build(),
            null,
            null,
        )
        audit("swipe", packageName, result)
        return result
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun launch(packageName: String): Boolean {
        val resolvedPackage = resolveLaunchPackage(packageName) ?: return false
        DeviceControlAuthorization.require(resolvedPackage)
        val intent = packageManager.getLaunchIntentForPackage(resolvedPackage)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        startActivity(intent)
        audit("launch", resolvedPackage, true)
        return true
    }

    @Suppress("DEPRECATION")
    private fun resolveLaunchPackage(packageOrLabel: String): String? {
        val query = packageOrLabel.trim()
        if (query.isBlank()) return null
        packageManager.getLaunchIntentForPackage(query)?.let { return query }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = packageManager.queryIntentActivities(launcher, 0)
            .map { info -> info.activityInfo.packageName to info.loadLabel(packageManager).toString() }
            .filterNot { (packageName, _) -> packageName == applicationContext.packageName }
        return candidates.firstOrNull { (_, label) -> label.equals(query, ignoreCase = true) }?.first
            ?: candidates.firstOrNull { (_, label) -> label.contains(query, ignoreCase = true) }?.first
    }

    private fun withNode(
        targetId: Int,
        textInput: Boolean = false,
        finalSubmit: Boolean = false,
        action: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString() ?: return false
        DeviceControlAuthorization.require(packageName, textInput, finalSubmit)
        var id = 0
        var found: AccessibilityNodeInfo? = null
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || found != null) return
            if (!node.isPassword) {
                if (id == targetId) { found = node; return }
                id++
            }
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(root)
        val result = found?.let(action) ?: false
        audit(if (textInput) "input" else "click", packageName, result)
        return result
    }

    private fun audit(action: String, target: String, success: Boolean) {
        runCatching {
            val safeTarget = target.replace(Regex("[^A-Za-z0-9._-]"), "_")
            File(filesDir, "audit/device-actions.log").apply { parentFile?.mkdirs() }
                .appendText("${Instant.now()}\t$action\t$safeTarget\t$success\n")
        }
    }

    override fun onDestroy() {
        DeviceControlAuthorization.revoke()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object { @Volatile var instance: PhoneAgentAccessibilityService? = null; private set }
}
