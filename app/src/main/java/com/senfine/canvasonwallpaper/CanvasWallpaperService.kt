package com.senfine.canvasonwallpaper

import android.animation.ValueAnimator
import android.graphics.*
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.*

class CanvasWallpaperService : WallpaperService() {
    companion object {
        private const val TAG = "CanvasWallpaper"
    }

    override fun onCreateEngine(): Engine = GLEngine()

    inner class GLEngine : Engine(), SurfaceTexture.OnFrameAvailableListener {
        private var glThread: Thread? = null
        @Volatile private var running = false

        private var mediaPlayer: MediaPlayer? = null
        private var videoSurfaceTexture: SurfaceTexture? = null
        private var videoTextureId = -1
        private var imageTextureId = -1
        private var program = -1

        @Volatile private var isShowingImage = false
        @Volatile private var isPaused = false
        @Volatile private var isIdle = false
        @Volatile private var transitionAlpha = 0f

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        @Volatile private var contentWidth = 1080
        @Volatile private var contentHeight = 1920

        @Volatile private var videoBright = 1.0f
        @Volatile private var videoBlurMax = 0.5f
        @Volatile private var blurEnabled = true

        @Volatile private var imageBlur = 10f
        @Volatile private var imageBright = 0.8f
        @Volatile private var canvasEnabled = true
        @Volatile private var albumArtEnabled = true
        @Volatile private var idleEnabled = true

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingImage: Bitmap? = null
        private var lastUrl: String? = null

        private val urlListener: (String, String) -> Unit = { signal, _ ->
            lastUrl = signal
            mainHandler.post {
                if (!isPaused) startTransition(signal)
            }
        }

        private val playbackListener: (Boolean) -> Unit = { isPlaying ->
            mainHandler.post {
                val wasPaused = isPaused
                isPaused = !isPlaying
                if (wasPaused != isPaused) {
                    val signal = if (isPaused && idleEnabled) "IDLE" else (lastUrl ?: "ALBUM_ART")
                    startTransition(signal)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            SpotifyNotificationService.addUrlListener(urlListener)
            SpotifyNotificationService.addPlaybackListener(playbackListener)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startGLThread()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            running = false
            try { glThread?.join(500) } catch (e: Exception) { Log.e(TAG, "Join error", e) }
            mediaPlayer?.release()
            mediaPlayer = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                updateSettings()
                if (!isShowingImage && !isPaused) mediaPlayer?.start()
            } else {
                mediaPlayer?.pause()
            }
        }

        private fun updateSettings() {
            val sp = getSharedPreferences("prefs", MODE_PRIVATE)
            videoBright = sp.getFloat("video_brightness", 1.0f)
            blurEnabled = sp.getBoolean("blur_enabled", true)
            videoBlurMax = sp.getFloat("blur_intensity", 0.5f)
            imageBlur = sp.getFloat("image_blur", 2.0f)
            imageBright = sp.getFloat("image_brightness", 0.8f)
            canvasEnabled = sp.getBoolean("canvas_enabled", true)
            albumArtEnabled = sp.getBoolean("album_art_enabled", true)
            idleEnabled = sp.getBoolean("idle_wallpaper_enabled", true)
        }

        private fun startTransition(url: String) {
            if (!blurEnabled) {
                handleNewUrl(url)
                return
            }

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                addUpdateListener { transitionAlpha = it.animatedValue as Float }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        handleNewUrl(url)
                        ValueAnimator.ofFloat(1f, 0f).apply {
                            duration = 600
                            startDelay = 100
                            addUpdateListener { transitionAlpha = it.animatedValue as Float }
                            start()
                        }
                    }
                })
                start()
            }
        }

        private fun handleNewUrl(url: String) {
            when (url) {
                "IDLE" -> {
                    isIdle = true
                    loadIdleWallpaper()
                }
                "ALBUM_ART", "Not found", "NONE", "Error" -> {
                    isIdle = false
                    if (albumArtEnabled) {
                        loadAlbumArt()
                    } else {
                        loadIdleWallpaper()
                    }
                }
                else -> {
                    isIdle = false
                    if (canvasEnabled) {
                        playVideo(url)
                    } else if (albumArtEnabled) {
                        loadAlbumArt()
                    } else {
                        loadIdleWallpaper()
                    }
                }
            }
        }

        private fun loadIdleWallpaper() {
            if (!idleEnabled) {
                if (albumArtEnabled) loadAlbumArt()
                return
            }
            val sp = getSharedPreferences("prefs", MODE_PRIVATE)
            val uriString = sp.getString("idle_wallpaper_uri", null)

            if (uriString == null) {
                loadAlbumArt()
                return
            }

            Thread {
                try {
                    val uri = android.net.Uri.parse(uriString)
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            contentWidth = bitmap.width
                            contentHeight = bitmap.height
                            pendingImage = bitmap
                            mainHandler.post {
                                mediaPlayer?.pause()
                                isShowingImage = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Load idle error", e)
                    mainHandler.post { loadAlbumArt() }
                }
            }.start()
        }

        private fun playVideo(url: String) {
            try {
                isShowingImage = false
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setSurface(android.view.Surface(videoSurfaceTexture))
                    isLooping = true
                    prepareAsync()
                    setOnPreparedListener {
                        contentWidth = it.videoWidth
                        contentHeight = it.videoHeight
                        if (!isPaused) it.start()
                    }
                    setOnErrorListener { _, _, _ ->
                        if (albumArtEnabled) {
                            loadAlbumArt()
                        } else {
                            loadIdleWallpaper()
                        }
                        true
                    }
                }
            } catch (e: Exception) {
                if (albumArtEnabled) {
                    loadAlbumArt()
                } else {
                    loadIdleWallpaper()
                }
            }
        }

        private fun loadAlbumArt() {
            if (!albumArtEnabled) {
                loadIdleWallpaper()
                return
            }
            val path = getSharedPreferences("prefs", MODE_PRIVATE).getString("last_album_art_path", null)
            if (path == null) {
                loadIdleWallpaper()
                return
            }

            Thread {
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        contentWidth = bitmap.width
                        contentHeight = bitmap.height
                        pendingImage = bitmap
                        mainHandler.post {
                            mediaPlayer?.pause()
                            isShowingImage = true
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "Load art error", e) }
            }.start()
        }

        private fun startGLThread() {
            running = true
            glThread = Thread {
                val egl = EGLContext.getEGL() as EGL10
                val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
                egl.eglInitialize(display, null)
                val configs = arrayOfNulls<EGLConfig>(1)
                egl.eglChooseConfig(display, intArrayOf(EGL10.EGL_RENDERABLE_TYPE, 4, EGL10.EGL_NONE), configs, 1, IntArray(1))
                val context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, intArrayOf(0x3098, 2, EGL10.EGL_NONE))
                val surface = egl.eglCreateWindowSurface(display, configs[0], surfaceHolder, null)
                egl.eglMakeCurrent(display, surface, surface, context)

                initGL()
                while (running) {
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    pendingImage?.let {
                        updateImageTexture(it)
                        it.recycle()
                        pendingImage = null
                    }
                    drawFrame()
                    egl.eglSwapBuffers(display, surface)
                    try { Thread.sleep(16) } catch (e: Exception) {}
                }
                deinitGL()
                egl.eglDestroySurface(display, surface)
                egl.eglDestroyContext(display, context)
            }.apply { start() }
        }

        private fun initGL() {
            val vs = "attribute vec4 pos; attribute vec2 uv; varying vec2 vUv; void main() { gl_Position = pos; vUv = uv; }".trimIndent()
            val fs = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vUv;
                uniform samplerExternalOES videoTex;
                uniform sampler2D imageTex;
                uniform bool isImage;
                uniform float bright;
                uniform float blur;
                uniform float fade;
                void main() {
                    vec4 color = vec4(0.0);
                    if (blur > 0.01) {
                        float step = blur * 0.01;
                        for(float i = -1.5; i <= 1.5; i+=1.0) {
                            for(float j = -1.5; j <= 1.5; j+=1.0) {
                                vec2 off = vec2(i*step, j*step);
                                color += isImage ? texture2D(imageTex, vUv + off) : texture2D(videoTex, vUv + off);
                            }
                        }
                        color /= 16.0;
                    } else {
                        color = isImage ? texture2D(imageTex, vUv) : texture2D(videoTex, vUv);
                    }
                    gl_FragColor = vec4(color.rgb * bright * (1.0 - fade), color.a);
                }
            """.trimIndent()
            program = GLES20.glCreateProgram().apply {
                val v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).apply { GLES20.glShaderSource(this, vs); GLES20.glCompileShader(this) }
                val f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).apply { GLES20.glShaderSource(this, fs); GLES20.glCompileShader(this) }
                GLES20.glAttachShader(this, v); GLES20.glAttachShader(this, f); GLES20.glLinkProgram(this)
            }
            videoTextureId = createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            imageTextureId = createTexture(GLES20.GL_TEXTURE_2D)
            videoSurfaceTexture = SurfaceTexture(videoTextureId).apply { setOnFrameAvailableListener(this@GLEngine) }
        }

        private fun deinitGL() {
            GLES20.glDeleteTextures(2, intArrayOf(videoTextureId, imageTextureId), 0)
            GLES20.glDeleteProgram(program)
            videoSurfaceTexture?.release()
        }

        private fun createTexture(target: Int): Int {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(target, tex[0])
            GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return tex[0]
        }

        private fun updateImageTexture(bitmap: Bitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }

        private fun drawFrame() {
            GLES20.glUseProgram(program)
            if (!isShowingImage) try { videoSurfaceTexture?.updateTexImage() } catch (e: Exception) {}

            val currentBright = if (isShowingImage) imageBright else videoBright
            val currentBlur = (if (isShowingImage && !isIdle) imageBlur / 10f else 0f) + (transitionAlpha * videoBlurMax)

            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "isImage"), if (isShowingImage) 1 else 0)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "bright"), currentBright)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "blur"), currentBlur.coerceIn(0f, 1f))
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "fade"), transitionAlpha)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "videoTex"), 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "imageTex"), 1)

            val uvs = calculateUVs()
            val vb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).apply { position(0) }
            val tb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(uvs).apply { position(0) }

            val pL = GLES20.glGetAttribLocation(program, "pos")
            val uL = GLES20.glGetAttribLocation(program, "uv")
            GLES20.glEnableVertexAttribArray(pL); GLES20.glVertexAttribPointer(pL, 2, GLES20.GL_FLOAT, false, 0, vb)
            GLES20.glEnableVertexAttribArray(uL); GLES20.glVertexAttribPointer(uL, 2, GLES20.GL_FLOAT, false, 0, tb)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun calculateUVs(): FloatArray {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
            val sAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
            val cAspect = contentWidth.toFloat() / contentHeight.toFloat()
            var uMin = 0f; var uMax = 1f; var vMin = 0f; var vMax = 1f
            if (sAspect > cAspect) {
                val hNeeded = contentWidth.toFloat() / sAspect
                val vOff = (contentHeight - hNeeded) / 2f / contentHeight
                vMin = vOff; vMax = 1f - vOff
            } else {
                val wNeeded = contentHeight.toFloat() * sAspect
                val uOffset = (contentWidth - wNeeded) / 2f / contentWidth
                uMin = uOffset; uMax = 1f - uOffset
            }
            return floatArrayOf(uMin, vMax, uMax, vMax, uMin, vMin, uMax, vMin)
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {}
        override fun onDestroy() {
            SpotifyNotificationService.removeUrlListener(urlListener)
            SpotifyNotificationService.removePlaybackListener(playbackListener)
            mediaPlayer?.release()
            super.onDestroy()
        }
    }
}