package com.senfine.canvasonwallpaper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putString("idle_wallpaper_uri", it.toString())
                .apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        
        // Canvas Settings
        val canvasSwitch = findViewById<SwitchMaterial>(R.id.canvasEnabledSwitch)
        canvasSwitch.isChecked = sp.getBoolean("canvas_enabled", true)
        canvasSwitch.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("canvas_enabled", isChecked).apply()
        }

        // Video Transition Settings
        val blurSwitch = findViewById<SwitchMaterial>(R.id.blurSwitch)
        val blurSlider = findViewById<Slider>(R.id.blurSlider)
        val brightnessSlider = findViewById<Slider>(R.id.brightnessSlider)

        blurSwitch.isChecked = sp.getBoolean("blur_enabled", true)
        blurSlider.value = sp.getFloat("blur_intensity", 0.5f)
        brightnessSlider.value = sp.getFloat("video_brightness", 1.0f)

        blurSwitch.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("blur_enabled", isChecked).apply()
        }
        blurSlider.addOnChangeListener { _, value, _ ->
            sp.edit().putFloat("blur_intensity", value).apply()
        }
        brightnessSlider.addOnChangeListener { _, value, _ ->
            sp.edit().putFloat("video_brightness", value).apply()
        }

        // Album Art Settings
        val albumArtSwitch = findViewById<SwitchMaterial>(R.id.albumArtEnabledSwitch)
        val imgBlurSlider = findViewById<Slider>(R.id.imageBlurSlider)
        val imgBrightnessSlider = findViewById<Slider>(R.id.imageBrightnessSlider)

        albumArtSwitch.isChecked = sp.getBoolean("album_art_enabled", true)
        imgBlurSlider.value = sp.getFloat("image_blur", 2.0f)
        imgBrightnessSlider.value = sp.getFloat("image_brightness", 0.8f)

        albumArtSwitch.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("album_art_enabled", isChecked).apply()
        }
        imgBlurSlider.addOnChangeListener { _, value, _ ->
            sp.edit().putFloat("image_blur", value).apply()
        }
        imgBrightnessSlider.addOnChangeListener { _, value, _ ->
            sp.edit().putFloat("image_brightness", value).apply()
        }

        // Idle Background Settings
        val idleSwitch = findViewById<SwitchMaterial>(R.id.idleEnabledSwitch)
        idleSwitch.isChecked = sp.getBoolean("idle_wallpaper_enabled", true)
        idleSwitch.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean("idle_wallpaper_enabled", isChecked).apply()
        }

        findViewById<Button>(R.id.selectCustomWallpaperBtn).setOnClickListener {
            pickWallpaper.launch(arrayOf("image/*"))
        }
    }
}
