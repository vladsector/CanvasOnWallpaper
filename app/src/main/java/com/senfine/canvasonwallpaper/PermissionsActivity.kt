package com.senfine.canvasonwallpaper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class PermissionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        val grantBtn = findViewById<MaterialButton>(R.id.grantPermissionButton)
        val finishBtn = findViewById<MaterialButton>(R.id.finishButton)

        grantBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        finishBtn.setOnClickListener {
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("setup_complete", true).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    private fun checkPermission() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = listeners != null && listeners.contains(packageName)
        
        val grantBtn = findViewById<MaterialButton>(R.id.grantPermissionButton)
        val finishBtn = findViewById<MaterialButton>(R.id.finishButton)

        if (isEnabled) {
            grantBtn.setIconResource(android.R.drawable.checkbox_on_background)
            grantBtn.text = "Permission Granted ✓"
            finishBtn.visibility = View.VISIBLE
        } else {
            grantBtn.setIconResource(0)
            grantBtn.text = "Grant Notification Access"
            finishBtn.visibility = View.GONE
        }
    }
}
