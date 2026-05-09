package com.senfine.canvasonwallpaper

import android.animation.ValueAnimator
import android.graphics.*
import android.media.MediaPlayer
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
        @Volatile private var transitionAlpha = 0f
        
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        @Volatile private var contentWidth = 1
        @Volatile private var contentHeight = 1

        @Volatile private var videoBright = 1.0f
        @Volatile private var videoBlurMax = 0.5f
        @Volatile private var blurEnabled = true
        
        @Volatile private var imageBlur = 10f
        @Volatile private var imageBright = 0.8f
        
        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingImage: Bitmap? = null

        private val urlListener: (String, String) -> Unit = { signal, _ ->
            mainHandler.post { startTransition(signal) }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            SpotifyNotificationService.addUrlListener(urlListener)
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
            try { glThread?.join() } catch (ignored: Exception) {}
            mediaPlayer?.release()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                updateSettings()
                if (!isShowingImage) mediaPlayer?.start()
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
            if (url == "ALBUM_ART" || url == "Not found" || url == "NONE" || url == "Error") {
                loadAlbumArt()
            } else {
                playVideo(url)
            }
        }

        private fun playVideo(url: String) {
            try {
                isShowingImage = false
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setSurface(android.view.Surface(videoSurfaceTexture))
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    prepareAsync()
                    setOnPreparedListener { 
                        contentWidth = it.videoWidth
                        contentHeight = it.videoHeight
                        it.start() 
                    }
                }
            } catch (e: Exception) {
                loadAlbumArt()
            }
        }

        private fun loadAlbumArt() {
            val path = getSharedPreferences("prefs", MODE_PRIVATE).getString("last_album_art_path", null)
            if (path == null) {
                isShowingImage = false
                return
            }

            Thread {
                try {
                    val file = File(path)
                    if (file.exists()) {
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
                    }
                } catch (ignored: Exception) { }
            }.start()
        }

        private fun startGLThread() {
            running = true
            glThread = Thread {
                val egl = EGLContext.getEGL() as EGL10
                val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
                egl.eglInitialize(display, null)
                val configAttribs = intArrayOf(EGL10.EGL_RENDERABLE_TYPE, 4, EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_NONE)
                val configs = arrayOfNulls<EGLConfig>(1)
                egl.eglChooseConfig(display, configAttribs, configs, 1, IntArray(1))
                val context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, intArrayOf(0x3098, 2, EGL10.EGL_NONE))
                val surface = egl.eglCreateWindowSurface(display, configs[0], surfaceHolder, null)
                egl.eglMakeCurrent(display, surface, surface, context)

                initGL()

                while (running) {
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    pendingImage?.let {
                        updateImageTexture(it)
                        pendingImage = null
                    }
                    drawFrame()
                    egl.eglSwapBuffers(display, surface)
                    try { Thread.sleep(16) } catch (ignored: Exception) {}
                }

                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                egl.eglDestroySurface(display, surface)
                egl.eglDestroyContext(display, context)
                egl.eglTerminate(display)
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

                vec4 getSample(vec2 offset) {
                    if (isImage) return texture2D(imageTex, vUv + offset);
                    return texture2D(videoTex, vUv + offset);
                }

                void main() {
                    vec4 color = vec4(0.0);
                    if (blur > 0.01) {
                        float step = blur * 0.015;
                        float count = 0.0;
                        // 7x7 Gaussian sampling for deep and smooth blur
                        for(float i = -3.0; i <= 3.0; i++) {
                            for(float j = -3.0; j <= 3.0; j++) {
                                float weight = exp(-(i*i+j*j) / 5.0);
                                color += getSample(vec2(i * step, j * step)) * weight;
                                count += weight;
                            }
                        }
                        color /= count;
                        color.rgb *= (1.0 - blur * 0.4);
                    } else {
                        color = getSample(vec2(0.0));
                    }
                    gl_FragColor = vec4(color.rgb * bright * (1.0 - fade), color.a);
                }
            """.trimIndent()

            program = GLES20.glCreateProgram().apply {
                val v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).apply { GLES20.glShaderSource(this, vs); GLES20.glCompileShader(this) }
                val f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).apply { GLES20.glShaderSource(this, fs); GLES20.glCompileShader(this) }
                GLES20.glAttachShader(this, v); GLES20.glAttachShader(this, f); GLES20.glLinkProgram(this)
            }
            videoTextureId = createTexture(0x8D65)
            imageTextureId = createTexture(GLES20.GL_TEXTURE_2D)
            videoSurfaceTexture = SurfaceTexture(videoTextureId).apply { setOnFrameAvailableListener(this@GLEngine) }
            updateSettings()
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
            if (!isShowingImage) {
                try { videoSurfaceTexture?.updateTexImage() } catch (ignored: Exception) {}
            }

            val currentBright = if (isShowingImage) imageBright else videoBright
            val currentBlur = (if (isShowingImage) imageBlur / 5f else 0f) + (transitionAlpha * videoBlurMax)

            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "isImage"), if (isShowingImage) 1 else 0)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "bright"), currentBright)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "blur"), currentBlur.coerceIn(0f, 1f))
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "fade"), transitionAlpha)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(0x8D65, videoTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "videoTex"), 0)
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "imageTex"), 1)

            val uvs = calculateUVs()
            val v = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val vb = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v).apply { position(0) }
            val tb = ByteBuffer.allocateDirect(uvs.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(uvs).apply { position(0) }
            
            val pL = GLES20.glGetAttribLocation(program, "pos")
            val uL = GLES20.glGetAttribLocation(program, "uv")
            GLES20.glEnableVertexAttribArray(pL)
            GLES20.glVertexAttribPointer(pL, 2, GLES20.GL_FLOAT, false, 0, vb)
            GLES20.glEnableVertexAttribArray(uL)
            GLES20.glVertexAttribPointer(uL, 2, GLES20.GL_FLOAT, false, 0, tb)
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
            super.onDestroy()
            SpotifyNotificationService.removeUrlListener(urlListener)
            mediaPlayer?.release()
        }
    }
}
