package dev.conn.resourcetimingdemo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var cacheBytes = 0L
    private var networkBytes = 0L
    private var undeterminedBytes = 0L

    private lateinit var cacheCounter: TextView
    private lateinit var networkCounter: TextView
    private lateinit var undeterminedCounter: TextView
    private lateinit var webview: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        cacheCounter = findViewById(R.id.counter_cache)
        networkCounter = findViewById(R.id.counter_network)
        undeterminedCounter = findViewById(R.id.counter_undetermined)
        webview = findViewById(R.id.webview)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Enable JavaScript so the ResourceTiming API can run.
        webview.settings.javaScriptEnabled = true

        // Setting a WebViewClient allows us to intercept page load events so we can reset counters.
        webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                resetCounters()
            }
        }

        val allowedOriginRules = setOf("*")

        // addWebMessageListener allows JavaScript to send messages back to our Java/Kotlin code.
        // This is safer and more performant than addJavascriptInterface.
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

                        when (type) {
                            "cache" -> cacheBytes += sizeBytes
                            "network" -> networkBytes += sizeBytes
                            "undetermined" -> undeterminedBytes += sizeBytes
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
                        // According to the PerformanceResourceTiming API:
                        // If cross-origin without Timing-Allow-Origin header, sizes are 0.
                        // We mark these as 'undetermined'.
                        // If the resource is fetched from local cache, transferSize is 0.
                        // Otherwise, it was fetched from the network.
                        if (entry.decodedBodySize === 0) {
                            type = 'undetermined';
                        } else if (entry.transferSize === 0) {
                            type = 'cache';
                        } else {
                            type = 'network';
                        }
                        
                        // Send the categorized size back to the native Android code.
                        AndroidListener.postMessage(JSON.stringify({ 
                            type: type, 
                            size: entry.decodedBodySize || 0 
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
        webview.loadUrl("https://peconn.github.io/resource_timing.html")
    }

    private fun resetCounters() {
        cacheBytes = 0L
        networkBytes = 0L
        undeterminedBytes = 0L
        updateCounters()
    }

    private fun updateCounters() {
        runOnUiThread {
            cacheCounter.text = "Cache: ${cacheBytes / 1024} KB"
            networkCounter.text = "Network: ${networkBytes / 1024} KB"
            undeterminedCounter.text = "Undetermined: ${undeterminedBytes / 1024} KB"
        }
    }
}