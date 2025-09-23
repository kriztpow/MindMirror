package com.example.tvbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var webView: WebView
    private lateinit var cursorView: View
    private lateinit var urlBar: EditText
    private lateinit var goButton: Button
    private lateinit var toggleOrientation: Button

    private var cursorPos = PointF(200f, 200f)
    private var portraitMode = false
    private val step = 40f

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        webView = findViewById(R.id.webview)
        cursorView = findViewById(R.id.cursorView)
        urlBar = findViewById(R.id.urlBar)
        goButton = findViewById(R.id.goButton)
        toggleOrientation = findViewById(R.id.toggleOrientation)

        setupWebView()
        setupUI()

        // Sitúa cursor al centro de la pantalla una vez layout hecho
        cursorView.post {
            cursorPos.x = (rootLayout.width / 2f) - (cursorView.width / 2f)
            cursorPos.y = (rootLayout.height / 2f) - (cursorView.height / 2f)
            updateCursorPosition()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.settings.userAgentString = webView.settings.userAgentString + " TVBrowser/1.2"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {}

        webView.loadUrl("https://www.google.com")
        urlBar.setText("https://www.google.com")
    }

    private fun setupUI() {
        goButton.setOnClickListener { loadFromBar() }
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromBar()
                true
            } else false
        }
        toggleOrientation.setOnClickListener { togglePortrait() }
    }

    private fun loadFromBar() {
        val input = urlBar.text?.toString() ?: ""
        val target = normalizeUrl(input)
        webView.loadUrl(target)
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        val looksLikeUrl = !trimmed.contains(" ") && (trimmed.contains(".") || trimmed.startsWith("http"))
        return if (looksLikeUrl) {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "https://$trimmed"
        } else {
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    private fun updateCursorPosition() {
        cursorView.translationX = cursorPos.x
        cursorView.translationY = cursorPos.y
    }

    private fun moveCursor(dx: Float, dy: Float) {
        val maxX = (rootLayout.width - cursorView.width).toFloat().coerceAtLeast(0f)
        val maxY = (rootLayout.height - cursorView.height).toFloat().coerceAtLeast(0f)
        cursorPos.x = (cursorPos.x + dx).coerceIn(0f, maxX)
        cursorPos.y = (cursorPos.y + dy).coerceIn(0f, maxY)
        updateCursorPosition()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursor(-step, 0f); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(step, 0f); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { moveCursor(0f, -step); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveCursor(0f, step); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { handleClick(); return true }
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) { webView.goBack(); return true }
            }
            KeyEvent.KEYCODE_MENU -> { togglePortrait(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleClick() {
        // Determinar rect del cursor en coordenadas de pantalla
        val cursorLoc = IntArray(2)
        cursorView.getLocationOnScreen(cursorLoc)
        val cursorRect = Rect(cursorLoc[0], cursorLoc[1], cursorLoc[0] + cursorView.width, cursorLoc[1] + cursorView.height)

        // Lista breve de vistas interactivas que queremos fijar en hit-test
        val interactive = listOf<View>(goButton, toggleOrientation, urlBar)

        for (v in interactive) {
            val vr = Rect()
            val visible = v.getGlobalVisibleRect(vr)
            if (visible && Rect.intersects(cursorRect, vr)) {
                // Si es EditText, darle foco y abrir teclado
                if (v is EditText) {
                    v.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                } else {
                    v.performClick()
                }
                return
            }
        }

        // Si no tocó ninguna vista interactiva -> mandar evento táctil al WebView
        val webLoc = IntArray(2)
        webView.getLocationOnScreen(webLoc)
        val xInWeb = (cursorRect.centerX() - webLoc[0]).toFloat()
        val yInWeb = (cursorRect.centerY() - webLoc[1]).toFloat()

        val down = MotionEvent.obtain(System.currentTimeMillis(), System.currentTimeMillis(),
            MotionEvent.ACTION_DOWN, xInWeb, yInWeb, 0)
        val up = MotionEvent.obtain(System.currentTimeMillis(), System.currentTimeMillis() + 50,
            MotionEvent.ACTION_UP, xInWeb, yInWeb, 0)
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun togglePortrait() {
        portraitMode = !portraitMode
        requestedOrientation = if (portraitMode)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
