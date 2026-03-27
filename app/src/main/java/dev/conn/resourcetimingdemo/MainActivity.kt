package dev.conn.resourcetimingdemo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var cacheValidBytes = 0L
    private var cacheRevalidatedBytes = 0L
    private var networkBytes = 0L
    private var undeterminedBytes = 0L
    private var cacheValidNum = 0
    private var cacheRevalidatedNum = 0
    private var networkNum = 0
    private var undeterminedNum = 0

    private lateinit var cacheValidCounter: TextView
    private lateinit var cacheRevalidatedCounter: TextView
    private lateinit var networkCounter: TextView
    private lateinit var undeterminedCounter: TextView
    private lateinit var webview: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        cacheValidCounter = findViewById(R.id.counter_cache_valid)
        cacheRevalidatedCounter = findViewById(R.id.counter_cache_revalidated)
        networkCounter = findViewById(R.id.counter_network)
        undeterminedCounter = findViewById(R.id.counter_undetermined)
        webview = findViewById(R.id.webview)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback {
            if (webview.canGoBack()) webview.goBack()
        }

        // Enable JavaScript so the ResourceTiming API can run.
        webview.settings.javaScriptEnabled = true

        // Setting a WebViewClient allows us to intercept page load events so we can reset counters.
        webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("ResourceTiming", "Loading $url.")
                resetCounters()
            }
        }

        val allowedOriginRules = setOf("*")

        // addWebMessageListener allows JavaScript to send messages back to our Java/Kotlin code.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webview,
                "AndroidListener",
                allowedOriginRules
            ) { _, message, _, _, _ ->
                try {
                    val messageData = message.data
                    if (messageData != null) {
                        val json = JSONObject(messageData)
                        val type = json.getString("type")
                        val sizeBytes = json.getLong("size")
                        val url = json.getString("url")

                        Log.d("ResourceTiming", "$url: $type")
                        when (type) {
                            "cache_valid" -> {
                                cacheValidBytes += sizeBytes
                                cacheValidNum++
                            }
                            "cache_revalidated" -> {
                                cacheRevalidatedBytes += sizeBytes
                                cacheRevalidatedNum++
                            }
                            "network" -> {
                                networkBytes += sizeBytes
                                networkNum++
                            }
                            "undetermined" -> {
                                undeterminedBytes += sizeBytes
                                undeterminedNum++
                            }
                        }
                        updateCounters()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // addDocumentStartJavaScript injects our PerformanceObserver script into the page before
        // any other scripts are executed. This ensures we don't miss any early resource timings.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val jsObserver = """
                const observer = new PerformanceObserver((list) => {
                    for (const entry of list.getEntries()) {
                        let type;
                        if (entry.decodedBodySize === 0) {
                            // This means we couldn't get information about the resource as it was
                            // cross origin.
                            type = 'undetermined';
                        } else if (entry.deliveryType === 'cache' && entry.transferSize === 0) {
                            // 0 bytes were transferred and deliveryType is cache means it was a valid cache hit.
                            type = 'cache_valid';
                        } else if (entry.deliveryType === 'cache' && entry.transferSize > 0) {
                            // > 0 bytes transferred and deliveryType is cache means it was a revalidated cache hit.
                            type = 'cache_revalidated';
                        } else {
                            type = 'network';
                        }
                        
                        AndroidListener.postMessage(JSON.stringify({ 
                            type: type, 
                            size: entry.decodedBodySize || 0,
                            url: entry.name
                        }));
                    }
                });
                // Watch for 'resource' entries and use buffered:true to catch those loaded before
                // the observer was fully registered.
                observer.observe({ type: 'resource', buffered: true });
            """.trimIndent()

            WebViewCompat.addDocumentStartJavaScript(webview, jsObserver, allowedOriginRules)
        }

        // Load testing page
        webview.loadUrl("https://www.wikipedia.org")
    }

    private fun resetCounters() {
        cacheValidBytes = 0L
        cacheRevalidatedBytes = 0L
        networkBytes = 0L
        undeterminedBytes = 0L
        cacheValidNum = 0
        cacheRevalidatedNum = 0
        networkNum = 0
        undeterminedNum = 0
        updateCounters()
    }

    private fun updateCounters() {
        runOnUiThread {
            cacheValidCounter.text = "Valid Cache: $cacheValidNum (${cacheValidBytes / 1024} KB)"
            cacheRevalidatedCounter.text = "Revalidated Cache: $cacheRevalidatedNum (${cacheRevalidatedBytes / 1024} KB)"
            networkCounter.text = "Network: $networkNum (${networkBytes / 1024} KB)"
            undeterminedCounter.text = "Undetermined: ${undeterminedNum}"
        }
    }
}