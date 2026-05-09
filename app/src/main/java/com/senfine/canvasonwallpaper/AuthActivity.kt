package com.senfine.canvasonwallpaper // ВСТАВЬТЕ СЮДА ВАШ ПАКЕТ

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val webView = findViewById<WebView>(R.id.webView)
        
        // Настройка куки-менеджера
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Устанавливаем современный User Agent, чтобы Spotify не ругался на "старый браузер"
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookies = CookieManager.getInstance().getCookie(url)
                if (cookies != null && cookies.contains("sp_dc=")) {
                    val spDc = cookies.split(";")
                        .find { it.trim().startsWith("sp_dc=") }
                        ?.split("=")?.get(1)

                    if (spDc != null) {
                        // Сохраняем куку в память
                        getSharedPreferences("prefs", MODE_PRIVATE).edit {
                            putString("sp_dc", spDc)
                        }
                        
                        // Принудительно сохраняем куки
                        CookieManager.getInstance().flush()

                        // Переходим на главный экран
                        startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                        // И отправляем пользователя давать права на уведомления
                        openNotificationSettings()
                        finish()
                    }
                }
            }
        }
        webView.loadUrl("https://accounts.spotify.com/en/login")
    }

    private fun openNotificationSettings() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }
}