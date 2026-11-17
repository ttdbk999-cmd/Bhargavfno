package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface NseBrowserClient {
    suspend fun warmUpAndSyncCookies(forceReload: Boolean = false): Boolean
    suspend fun fetchJson(url: String): String?
    fun release() {}
}

/**
 * Uses Android's real Chromium WebView on an off-screen instance so NSE India's
 * Akamai Bot Manager JS challenge + Chrome TLS fingerprint succeed naturally,
 * syncing session cookies to OkHttp and providing same-origin `fetch()` fallback.
 */
class AndroidNseWebViewBridge(
    private val context: Context,
) : NseBrowserClient {

    private val mutex = Mutex()
    private val pendingFetches = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var pageReady: Boolean = false

    @Volatile
    private var lastWarmupMs: Long = 0L

    @Volatile
    private var pageFinishedSignal: CompletableDeferred<Boolean>? = null

    override suspend fun warmUpAndSyncCookies(forceReload: Boolean): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceReload && pageReady && (now - lastWarmupMs) < 4 * 60_000L) {
            syncWebViewCookiesToOkHttp()
            if (NseNetworkModule.cookieJar.hasCookies()) {
                return@withLock true
            }
        }

        val ensured = ensureWebViewCreated()
        if (!ensured) return@withLock false

        val loadedSignal = CompletableDeferred<Boolean>()
        pageFinishedSignal = loadedSignal
        pageReady = false

        withContext(Dispatchers.Main) {
            runCatching {
                CookieManager.getInstance().setAcceptCookie(true)
                webView?.loadUrl(NSE_WARMUP_URL)
            }.onFailure {
                loadedSignal.complete(false)
            }
        }

        // Wait up to 10s for page load + Akamai sensor cookies
        withTimeoutOrNull(10_000L) {
            loadedSignal.await()
        }

        // Give Akamai JS sensor a brief window to set nsit/nseappid/_abck cookies
        repeat(6) {
            val rawCookies = withContext(Dispatchers.Main) {
                runCatching { CookieManager.getInstance().getCookie(NSE_ORIGIN) }.getOrNull()
            }
            if (!rawCookies.isNullOrBlank() &&
                (rawCookies.contains("nsit") || rawCookies.contains("nseappid") || rawCookies.contains("bm_sz") || rawCookies.contains("_abck"))
            ) {
                NseNetworkModule.cookieJar.importCookieHeader("www.nseindia.com", rawCookies)
                pageReady = true
                lastWarmupMs = System.currentTimeMillis()
                return@withLock true
            }
            delay(350L)
        }

        syncWebViewCookiesToOkHttp()
        pageReady = true
        lastWarmupMs = System.currentTimeMillis()
        NseNetworkModule.cookieJar.hasCookies()
    }

    override suspend fun fetchJson(url: String): String? {
        if (!pageReady) {
            warmUpAndSyncCookies(forceReload = false)
        }
        val wv = webView ?: return null
        val reqId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String?>()
        pendingFetches[reqId] = deferred

        val quotedUrl = JSONObject.quote(url)
        val quotedId = JSONObject.quote(reqId)
        val script = """
            (function() {
              try {
                fetch($quotedUrl, {
                  method: 'GET',
                  credentials: 'include',
                  headers: {
                    'Accept': 'application/json, text/plain, */*'
                  }
                }).then(function(resp) {
                  return resp.text().then(function(body) {
                    window.AndroidNseBridge.onFetchResult($quotedId, resp.status, body);
                  });
                }).catch(function(err) {
                  window.AndroidNseBridge.onFetchError($quotedId, String(err));
                });
              } catch (e) {
                window.AndroidNseBridge.onFetchError($quotedId, String(e));
              }
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            runCatching {
                wv.evaluateJavascript(script, null)
            }.onFailure {
                pendingFetches.remove(reqId)
                deferred.complete(null)
            }
        }

        val result = withTimeoutOrNull(12_000L) {
            deferred.await()
        }
        pendingFetches.remove(reqId)
        return result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureWebViewCreated(): Boolean = withContext(Dispatchers.Main) {
        if (webView != null) return@withContext true
        runCatching {
            val wv = WebView(context.applicationContext)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = NseNetworkModule.CHROME_USER_AGENT
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }
            wv.addJavascriptInterface(JsFetchBridge(), "AndroidNseBridge")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pageFinishedSignal?.complete(true)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        pageFinishedSignal?.complete(false)
                    }
                }
            }
            webView = wv
            true
        }.getOrDefault(false)
    }

    private suspend fun syncWebViewCookiesToOkHttp() {
        val raw = withContext(Dispatchers.Main) {
            runCatching { CookieManager.getInstance().getCookie(NSE_ORIGIN) }.getOrNull()
        }
        if (!raw.isNullOrBlank()) {
            NseNetworkModule.cookieJar.importCookieHeader("www.nseindia.com", raw)
        }
    }

    override fun release() {
        pendingFetches.values.forEach { it.complete(null) }
        pendingFetches.clear()
        val existing = webView ?: return
        webView = null
        existing.post {
            runCatching {
                existing.stopLoading()
                existing.removeJavascriptInterface("AndroidNseBridge")
                existing.destroy()
            }
        }
    }

    private inner class JsFetchBridge {
        @JavascriptInterface
        fun onFetchResult(requestId: String, status: Int, body: String?) {
            val deferred = pendingFetches.remove(requestId) ?: return
            if (status in 200..299 && NseOptionChainParser.isLikelyJsonObject(body)) {
                deferred.complete(body)
            } else {
                deferred.complete(null)
            }
        }

        @JavascriptInterface
        fun onFetchError(requestId: String, error: String?) {
            pendingFetches.remove(requestId)?.complete(null)
        }
    }

    private companion object {
        const val NSE_ORIGIN = "https://www.nseindia.com"
        const val NSE_WARMUP_URL = "https://www.nseindia.com/option-chain"
    }
}
