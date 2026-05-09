package com.senfine.canvasonwallpaper

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DebugActivity : AppCompatActivity() {

    private lateinit var canvasUrlTextView: TextView
    private lateinit var tokenTextView: TextView
    private lateinit var rawJsonResponseTextView: TextView
    private lateinit var albumArtPreview: ImageView

    private val urlListener: (String, String) -> Unit = { signal, rawJson ->
        runOnUiThread {
            canvasUrlTextView.text = signal
            rawJsonResponseTextView.text = rawJson
            updateArtPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        canvasUrlTextView = findViewById(R.id.canvasUrlTextView)
        tokenTextView = findViewById(R.id.tokenTextView)
        rawJsonResponseTextView = findViewById(R.id.rawJsonResponseTextView)
        albumArtPreview = findViewById(R.id.albumArtPreview)

        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        tokenTextView.text = sp.getString("sp_dc", "Not logged in")
        canvasUrlTextView.text = sp.getString("last_canvas_url", "None")
        rawJsonResponseTextView.text = sp.getString("last_raw_json", "{}")

        val spDcInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.spDcInput)
        spDcInput.setText(sp.getString("sp_dc", ""))
        
        spDcInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val newToken = s?.toString()?.trim() ?: ""
                sp.edit().putString("sp_dc", newToken).apply()
                tokenTextView.text = if (newToken.isEmpty()) "Not logged in" else newToken
            }
        })

        findViewById<android.view.View>(R.id.loginButton).setOnClickListener {
            startActivity(android.content.Intent(this, AuthActivity::class.java))
        }
    }

    private fun updateArtPreview() {
        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        val artUrl = sp.getString("last_album_art_url", null)
        if (artUrl != null) {
            Thread {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(java.net.URL(artUrl).openStream())
                    runOnUiThread { albumArtPreview.setImageBitmap(bitmap) }
                } catch (e: Exception) {
                    runOnUiThread { albumArtPreview.setImageDrawable(null) }
                }
            }.start()
        } else {
            albumArtPreview.setImageDrawable(null)
        }
    }

    override fun onResume() {
        super.onResume()
        SpotifyNotificationService.addUrlListener(urlListener)
    }

    override fun onPause() {
        super.onPause()
        SpotifyNotificationService.removeUrlListener(urlListener)
    }
}
