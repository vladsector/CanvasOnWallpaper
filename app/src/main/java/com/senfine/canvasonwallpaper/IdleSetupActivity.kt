package com.senfine.canvasonwallpaper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class IdleSetupActivity : AppCompatActivity() {

    // Используем OpenDocument для получения перманентного доступа к файлу
    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putString("idle_wallpaper_uri", it.toString())
                .apply()
            findViewById<MaterialButton>(R.id.selectIdleWallpaperButton).text = "Wallpaper Selected ✓"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_idle_setup)

        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        val idleSwitch = findViewById<SwitchMaterial>(R.id.idleWallpaperSwitch)
        val selectBtn = findViewById<MaterialButton>(R.id.selectIdleWallpaperButton)
        val nextBtn = findViewById<MaterialButton>(R.id.nextButton)

        idleSwitch.isChecked = sp.getBoolean("idle_wallpaper_enabled", false)
        
        val savedUri = sp.getString("idle_wallpaper_uri", null)
        if (savedUri != null) {
            selectBtn.text = "Wallpaper Selected ✓"
        }

        idleSwitch.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("idle_wallpaper_enabled", isChecked).apply()
        }

        selectBtn.setOnClickListener {
            pickWallpaper.launch(arrayOf("image/*"))
        }

        nextBtn.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
        }
    }
}
