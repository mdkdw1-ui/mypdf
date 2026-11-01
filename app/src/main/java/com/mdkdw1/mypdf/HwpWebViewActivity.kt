package com.mdkdw1.mypdf

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * HWPX/HWP 뷰어 (WebView + @baneung-pack/doc-viewer)
 * - 로컬 파일은 WebViewAssetLoader로 가상 URL 제공 (서버 전송 없음)
 * - BaneungDocViewer.mount('#viewer', { src: 'https://appassets.androidplatform.net/hwp/file.hwpx' })
 */
class HwpWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "hwp_path"
        private const val ASSET_DOMAIN = "appassets.androidplatform.net"
    }

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val file = File(path)
        if (!file.exists()) { finish(); return }

        // 파일을 cacheDir/hwp_viewer/ 로 복사 (assetLoader가 참조)
        val cacheDir = File(cacheDir, "hwp_viewer").apply { mkdirs() }
        val localCopy = File(cacheDir, file.name)
        try {
            file.copyTo(localCopy, overwrite = true)
            // ⭐ doc-viewer.min.js 도 같은 경로에 복사
            assets.open("doc-viewer.min.js").use { input ->
                File(cacheDir, "doc-viewer.min.js").outputStream().use { out ->
                    input.copyTo(out)
                }
            }
        } catch (e: Exception) {
            CrashLogger.e(this, "HwpWebView", "파일 복사 실패", e)
            finish(); return
        }

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler(
                "/hwp/",
                WebViewAssetLoader.InternalStoragePathHandler(this, cacheDir)
            )
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
            setBackgroundColor(0xFF0D1117.toInt())
        }

        setContentView(webView)

        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }

        val virtualUrl = "https://$ASSET_DOMAIN/hwp/${Uri.encode(file.name)}"
        val html = buildHtml(virtualUrl, file.name)
        webView.loadDataWithBaseURL(
            "https://$ASSET_DOMAIN/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun buildHtml(srcUrl: String, fileName: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html, body { margin:0; padding:0; background:#0D1117; color:#F0F6FC;
               font-family:sans-serif; height:100%; }
  #viewer { width:100%; height:100vh; box-sizing:border-box; }
  #status { position:fixed; top:50%; left:50%; transform:translate(-50%,-50%);
            color:#8B949E; font-size:14px; z-index:10; text-align:center; }
</style>
</head>
<body>
<div id="status">문서 로드 중...</div>
<div id="viewer"></div>
<script src="/hwp/doc-viewer.min.js"></script>
<script>
(function() {
  function status(msg) {
    var el = document.getElementById('status');
    if (el) { el.textContent = msg; el.style.display = 'block'; }
  }
  function done() {
    var el = document.getElementById('status');
    if (el) el.style.display = 'none';
  }

  try {
    if (typeof BaneungDocViewer === 'undefined') {
      status('❌ BaneungDocViewer 로드 실패');
      return;
    }
    status('렌더링 준비 중...');

    var srcUrl = "${srcUrl}";
    var result = BaneungDocViewer.mount('#viewer', { src: srcUrl });

    // mount가 Promise 반환 시
    if (result && typeof result.then === 'function') {
      result.then(done).catch(function(e) {
        status('❌ 렌더 실패: ' + (e && e.message ? e.message : e));
      });
    } else {
      // 동기 반환 → 약간 대기 후 숨김
      setTimeout(done, 800);
    }
  } catch (e) {
    status('❌ 예외: ' + (e && e.message ? e.message : e));
  }
})();
</script>
</body>
</html>
    """.trimIndent()

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
