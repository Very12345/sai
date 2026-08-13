package com.phoneagent.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.phoneagent.app.service.VoiceConversationService

/** Brief visible trampoline required by Android 14 before a microphone foreground service starts. */
class VoiceStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice() else finish()
    }

    private fun startVoice() {
        ContextCompat.startForegroundService(this, Intent(this, VoiceConversationService::class.java).setAction(VoiceConversationService.ACTION_TOGGLE))
        finish()
        overridePendingTransition(0, 0)
    }

    private companion object { const val REQUEST_MIC = 2401 }
}
