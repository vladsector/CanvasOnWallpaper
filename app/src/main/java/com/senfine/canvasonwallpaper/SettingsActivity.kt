package com.senfine.canvasonwallpaper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        
        // Video Settings
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
        val imgBlurSlider = findViewById<Slider>(R.id.imageBlurSlider)
        val imgBrightnessSlider = findViewById<Slider>(R.id.imageBrightnessSlider)

        imgBlurSlider.value = sp.getFloat("image_blur", 2.0f)
        imgBrightnessSlider.value = sp.getFloat("image_brightness", 0.8f)

        imgBlurSlider.addOnChangeListener { _, value, _ ->
            sp.edit().putFloat("image_blur", value).apply()
        }
        imgBrightnessSlider.addOnChangeListener { _, value, _ ->
            sp.edit().putFloat("image_brightness", value).apply()
        }
    }
}
