package com.senfine.canvasonwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var trackInfoTextView: TextView
    private lateinit var trackIdTextView: TextView
    
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { 
        updateTrackUI()
        registerMediaCallback()
    }
    
    private val urlListener: (String, String) -> Unit = { _, _ ->
        updateTrackUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sp = getSharedPreferences("prefs", MODE_PRIVATE)
        val setupComplete = sp.getBoolean("setup_complete", false)
        
        if (!setupComplete) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        trackInfoTextView = findViewById(R.id.trackInfoTextView)
        trackIdTextView = findViewById(R.id.trackIdTextView)
        
        findViewById<Button>(R.id.setWallpaperButton).setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, CanvasWallpaperService::class.java)
                )
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.debugButton).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val component = ComponentName(this, SpotifyNotificationService::class.java)
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsListener, component)
        } catch (ignored: Exception) { }

        SpotifyNotificationService.addUrlListener(urlListener)
        updateTrackUI()
        registerMediaCallback()
    }

    override fun onPause() {
        super.onPause()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        SpotifyNotificationService.removeUrlListener(urlListener)
        activeController?.unregisterCallback(mediaCallback)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateTrackUI()
        }
    }

    private fun updateTrackUI() {
        val component = ComponentName(this, SpotifyNotificationService::class.java)
        val spotify = mediaSessionManager?.getActiveSessions(component)
            ?.find { it.packageName == "com.spotify.music" }

        if (spotify != null) {
            val meta = spotify.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val rawId = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            
            trackInfoTextView.text = if ((title != null) && (artist != null)) "$title — $artist" else "Playing"
            trackIdTextView.text = rawId?.replace("spotify:track:", "ID: ") ?: "ID: Unknown"
            activeController = spotify
        } else {
            trackInfoTextView.text = "Not playing"
            trackIdTextView.text = "ID: None"
            activeController = null
        }
    }

    private fun registerMediaCallback() {
        activeController?.registerCallback(mediaCallback)
    }
}
