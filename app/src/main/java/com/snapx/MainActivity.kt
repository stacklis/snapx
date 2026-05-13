package com.snapx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.snapx.service.ScreenshotWatcherService
import com.snapx.service.SnapWindowService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        checkAndAdvance()
    }

    private fun checkAndAdvance() {
        val status = findViewById<TextView>(R.id.status_text)
        val hint = findViewById<TextView>(R.id.hint_text)

        if (!Settings.canDrawOverlays(this)) {
            status.text = "Step 1 of 2: Allow drawing over other apps"
            hint.text = "In the next screen, find SnapX and enable the toggle."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }

        if (!isAccessibilityEnabled()) {
            status.text = "Step 2 of 2: Enable SnapX Accessibility Service"
            hint.text = "In the next screen, find SnapX under Installed Services and enable it."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        ScreenshotWatcherService.start(this)
        finish()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val component = "$packageName/${SnapWindowService::class.java.name}"
        return flat.split(":").any { it.equals(component, ignoreCase = true) }
    }
}
