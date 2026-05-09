package com.senfine.canvasonwallpaper

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val loginBtn = findViewById<MaterialButton>(R.id.loginSpotifyButton)
        val manualInput = findViewById<TextInputEditText>(R.id.manualTokenInput)
        val applyBtn = findViewById<MaterialButton>(R.id.applyTokenButton)
        val nextBtn = findViewById<MaterialButton>(R.id.nextButton)

        val sp = getSharedPreferences("prefs", MODE_PRIVATE)

        // Проверяем, есть ли уже токен (например, если вернулись из AuthActivity)
        checkTokenStatus(loginBtn, applyBtn, nextBtn)

        loginBtn.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        applyBtn.setOnClickListener {
            val token = manualInput.text?.toString()?.trim() ?: ""
            if (token.isNotEmpty()) {
                sp.edit().putString("sp_dc", token).apply()
                setButtonSuccess(applyBtn)
                nextBtn.visibility = View.VISIBLE
            }
        }

        nextBtn.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val loginBtn = findViewById<MaterialButton>(R.id.loginSpotifyButton)
        val applyBtn = findViewById<MaterialButton>(R.id.applyTokenButton)
        val nextBtn = findViewById<MaterialButton>(R.id.nextButton)
        checkTokenStatus(loginBtn, applyBtn, nextBtn)
    }

    private fun checkTokenStatus(loginBtn: MaterialButton, applyBtn: MaterialButton, nextBtn: Button) {
        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        val token = sp.getString("sp_dc", null)
        if (!token.isNullOrEmpty()) {
            setButtonSuccess(loginBtn)
            nextBtn.visibility = View.VISIBLE
        }
    }

    private fun setButtonSuccess(btn: MaterialButton) {
        btn.setIconResource(android.R.drawable.checkbox_on_background)
        if (!btn.text.contains("✓")) {
            btn.text = btn.text.toString() + " ✓"
        }
    }
}
