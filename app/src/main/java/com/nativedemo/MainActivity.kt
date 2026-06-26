package com.nativedemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SsoUrlBuilder"
      private const val HTML_ASSET_URL = "file:///android_asset/sso-url-builder-v4.1.html"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()
        webView.loadUrl(HTML_ASSET_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportZoom(false)
            setSupportMultipleWindows(false)
            loadsImagesAutomatically = true
            blockNetworkImage = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setInitialScale(0)

        WebView.setWebContentsDebuggingEnabled(true)
        webView.addJavascriptInterface(NativeBridge(), "NativeBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
                injectQuickStepOne()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.e(TAG, "WebView error: ${error?.description}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "JS Console: ${consoleMessage?.message()}")
                return true
            }
        }
    }

      inner class NativeBridge {
        @JavascriptInterface
        fun postJson(url: String, headersJson: String, bodyJson: String): String {
          return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true

            val headers = JSONObject(headersJson)
            val keys = headers.keys()
            while (keys.hasNext()) {
              val key = keys.next()
              connection.setRequestProperty(key, headers.optString(key))
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
              writer.write(bodyJson)
              writer.flush()
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = if (stream != null) {
              BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
              }
            } else {
              ""
            }

            JSONObject().apply {
              put("ok", status in 200..299)
              put("status", status)
              put("body", responseBody)
            }.toString()
          } catch (e: Exception) {
            JSONObject().apply {
              put("ok", false)
              put("status", 0)
              put("error", e.message ?: "Network error")
            }.toString()
          }
        }
      }

    private fun injectQuickStepOne() {
        val js = """
            (function() {
              async function encryptWithAppKey(plainText) {
                const ENCRYPT_KEY = '6f7e8d9c0b1a2e3d4c5b6a79807f6e5d';
                const ENCRYPT_IV = 'fedcba0987654321';
                const encoder = new TextEncoder();
                const keyData = encoder.encode(ENCRYPT_KEY);
                const ivData = encoder.encode(ENCRYPT_IV);
                const cryptoKey = await crypto.subtle.importKey('raw', keyData, { name: 'AES-CBC' }, false, ['encrypt']);
                const encrypted = await crypto.subtle.encrypt({ name: 'AES-CBC', iv: ivData }, cryptoKey, encoder.encode(plainText));
                return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
              }

              async function createUuidFromApi(config) {
                const baseUrl = config.baseUrl;
                const endpoint = baseUrl + '/abc-web-common/api/v1/redirectionV2';

                const xenv = await encryptWithAppKey(config.mobileNumber);
                const encryptedData = await encryptWithAppKey(JSON.stringify({
                  mobileNumber: config.mobileNumber,
                  username: config.username,
                  productId: config.productId,
                  lmsId: config.lmsId
                }));

                const headers = {
                  mobileNumber: config.mobileNumber,
                  serviceOrigin: config.serviceOrigin,
                  Source: config.source,
                  Secret: config.secret,
                  'Content-Type': 'application/json',
                  xenv: xenv
                };

                const body = JSON.stringify({ data: encryptedData });

                if (!window.NativeBridge || typeof window.NativeBridge.postJson !== 'function') {
                  throw new Error('Native bridge unavailable for API call');
                }

                const nativeResponse = window.NativeBridge.postJson(endpoint, JSON.stringify(headers), body);
                const response = JSON.parse(nativeResponse || '{}');

                if (!response.ok) {
                  const detail = response.error || response.body || ('HTTP ' + response.status);
                  throw new Error('UUID API failed: ' + detail);
                }

                const parsedBody = JSON.parse(response.body || '{}');
                if (!parsedBody.uuid) {
                  throw new Error('UUID not found in response');
                }

                return parsedBody.uuid;
              }

              var step1 = document.getElementById('step1');
              if (!step1) return;
              var step1Content = step1.querySelector('.step-content');
              if (!step1Content) return;

              step1.querySelector('.step-title').textContent = 'Create UUID';
              step1.querySelector('.step-subtitle').textContent = 'Single click to auto-generate UUID';

              step1Content.innerHTML = '' +
                '<div class="config-card">' +
                  '<h6 class="text-info mb-2">Quick Step 1</h6>' +
                  '<p class="small text-muted-custom mb-2">Tap once to call redirectionV2 and auto-fill Step 2 with a valid UUID.</p>' +
                  '<div class="small text-muted-custom mb-3">Using default profile and UAT1 endpoint for speed.</div>' +
                  '<button id="autoUuidBtn" class="btn btn-success">Auto Create UUID</button>' +
                  '<div id="autoUuidResult" class="alert alert-success py-2 small d-none mt-3"></div>' +
                  '<div id="autoUuidError" class="alert alert-danger py-2 small d-none mt-2"></div>' +
                '</div>';

              var button = document.getElementById('autoUuidBtn');
              var result = document.getElementById('autoUuidResult');
              var error = document.getElementById('autoUuidError');
              if (!button) return;

              button.addEventListener('click', async function() {
                button.disabled = true;
                var originalText = button.textContent;
                button.textContent = 'Generating...';
                if (result) result.classList.add('d-none');
                if (error) error.classList.add('d-none');

                try {
                  var config = {
                    mobileNumber: '9008878907',
                    username: 'Manish Hundekar',
                    productId: 'goldloan',
                    lmsId: '00QBl00000EajeTMAR',
                    serviceOrigin: 'Web',
                    source: 'ABCD_WEB_REDIRECTION',
                    secret: '9876556789275439',
                    baseUrl: 'https://abcduat1.abcscuat.com'
                  };

                  var uuid = await createUuidFromApi(config);
                  var ssoInput = document.getElementById('ssoToken');
                  var apiResponse = document.getElementById('apiResponse');
                  if (ssoInput) ssoInput.value = uuid;
                  if (apiResponse) apiResponse.value = JSON.stringify({ uuid: uuid }, null, 2);

                  if (typeof window.setStepStatus === 'function') {
                    window.setStepStatus('step1', 'completed');
                    window.setStepStatus('step2', 'completed');
                  }

                  if (result) {
                    result.textContent = 'UUID generated: ' + uuid;
                    result.classList.remove('d-none');
                  }

                  var step2 = document.getElementById('step2');
                  var step3 = document.getElementById('step3');
                  if (step2) {
                    var c2 = step2.querySelector('.step-content');
                    if (c2) c2.classList.add('collapsed');
                  }
                  if (step3) {
                    var c3 = step3.querySelector('.step-content');
                    if (c3) c3.classList.remove('collapsed');
                    step3.classList.add('active');
                  }
                } catch (e) {
                  if (error) {
                    error.textContent = e && e.message ? e.message : 'Failed to create UUID';
                    error.classList.remove('d-none');
                  }
                } finally {
                  button.disabled = false;
                  button.textContent = originalText;
                }
              });
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
