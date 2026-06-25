package com.nativedemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

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
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
          databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
          setSupportZoom(false)
          setSupportMultipleWindows(false)
          loadsImagesAutomatically = true
          blockNetworkImage = false
          useWideViewPort = true
          loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        WebView.setWebContentsDebuggingEnabled(true)

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

    private fun injectQuickStepOne() {
        val js = """
            (function() {
              function createUuid() {
                if (window.crypto && typeof window.crypto.randomUUID === 'function') {
                  return window.crypto.randomUUID();
                }
                return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                  var r = Math.random() * 16 | 0;
                  var v = c === 'x' ? r : (r & 0x3 | 0x8);
                  return v.toString(16);
                });
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
                  '<p class="small text-muted-custom mb-3">Tap the button to generate UUID and auto-fill Step 2.</p>' +
                  '<button id="autoUuidBtn" class="btn btn-success">Auto Create UUID</button>' +
                  '<div id="autoUuidResult" class="alert alert-success py-2 small d-none mt-3"></div>' +
                '</div>';

              var button = document.getElementById('autoUuidBtn');
              var result = document.getElementById('autoUuidResult');
              if (!button) return;

              button.addEventListener('click', function() {
                var uuid = createUuid();
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
