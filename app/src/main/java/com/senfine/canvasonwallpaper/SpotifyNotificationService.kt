package com.senfine.canvasonwallpaper

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet

class SpotifyNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTrackId: String? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    companion object {
        private val listeners = CopyOnWriteArraySet<(String, String) -> Unit>()
        
        fun addUrlListener(listener: (String, String) -> Unit) {
            listeners.add(listener)
        }
        
        fun removeUrlListener(listener: (String, String) -> Unit) {
            listeners.remove(listener)
        }

        private fun notifyDataUpdated(url: String, rawJson: String) {
            listeners.forEach { it.invoke(url, rawJson) }
        }
    }

    private val sessionCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            // Извлекаем обложку напрямую из плеера Spotify
            val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            
            if (albumArt != null) {
                saveAlbumArt(albumArt)
            }

            metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.let { rawId ->
                val trackId = rawId.replace("spotify:track:", "")
                if (trackId != lastTrackId) {
                    lastTrackId = trackId
                    Log.d("CanvasApp", "New track: $trackId")
                    fetchCanvasUrl(trackId)
                }
            }
        }
    }

    private fun saveAlbumArt(bitmap: android.graphics.Bitmap) {
        // Сохраняем битмап во временный файл, чтобы передать между процессами через путь
        serviceScope.launch {
            try {
                val file = java.io.File(cacheDir, "spotify_art.jpg")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                getSharedPreferences("prefs", MODE_PRIVATE).edit {
                    putString("last_album_art_path", file.absolutePath)
                }
            } catch (e: Exception) {
                Log.e("CanvasApp", "Failed to save art to file")
            }
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        findAndRegisterSpotify()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, SpotifyNotificationService::class.java)
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            findAndRegisterSpotify()
        } catch (ignored: Exception) {
            Log.e("CanvasApp", "Listener registration failed")
        }
    }

    private fun findAndRegisterSpotify() {
        val componentName = ComponentName(this, SpotifyNotificationService::class.java)
        val spotifySession = mediaSessionManager?.getActiveSessions(componentName)
            ?.find { it.packageName == "com.spotify.music" }

        if (spotifySession != null && activeController?.sessionToken != spotifySession.sessionToken) {
            activeController?.unregisterCallback(sessionCallback)
            activeController = spotifySession
            activeController?.registerCallback(sessionCallback)
            sessionCallback.onMetadataChanged(activeController?.metadata)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "com.spotify.music") findAndRegisterSpotify()
    }

    private fun fetchCanvasUrl(trackId: String) {
        serviceScope.launch {
            var connection: HttpURLConnection? = null
            try {
                val sp = getSharedPreferences("prefs", MODE_PRIVATE)
                val spDc = sp.getString("sp_dc", "") ?: ""
                
                // Обновленный формат запроса с токеном
                val url = URL("http://95.85.245.174:3000/api/canvas?trackId=$trackId&sp_dc=$spDc")

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = connection.responseCode
                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $responseCode"
                }

                getSharedPreferences("prefs", MODE_PRIVATE).edit { 
                    putString("last_raw_json", responseText) 
                }

                val canvasUrl = parseCanvasUrl(responseText)
                
                getSharedPreferences("prefs", MODE_PRIVATE).edit {
                    putString("last_canvas_url", canvasUrl)
                }

                withContext(Dispatchers.Main) { 
                    val signal = canvasUrl ?: "ALBUM_ART"
                    notifyDataUpdated(signal, responseText)
                }
            } catch (ignored: Exception) {
                Log.e("CanvasApp", "Background API failed")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseCanvasUrl(response: String): String? {
        return try {
            val json = JSONObject(response)
            json.optJSONArray("canvasesList")?.optJSONObject(0)?.optString("canvasUrl")
        } catch (ignored: Exception) { null }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        activeController?.unregisterCallback(sessionCallback)
        serviceScope.cancel()
    }
}
