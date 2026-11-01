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

        val cacheDir = File(cacheDir, "hwp_viewer").apply { mkdirs() }
        val localCopy = File(cacheDir, file.name)
        try {
            file.copyTo(localCopy, overwrite = true)
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

            // ⭐ 핀치 줌 활성화
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false   // 기본 버튼 숨김 (핀치만 사용)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            
            // ⭐ 텍스트 확대 시 자동 조정 방지 (CSS 깔끕 방지)
            settings.textZoom = 100

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        setContentView(webView)

        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }

        val virtualUrl = "https://$ASSET_DOMAIN/hwp/${Uri.encode(file.name)}"
        val html = buildHtml(virtualUrl)
        webView.loadDataWithBaseURL(
            "https://$ASSET_DOMAIN/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun buildHtml(srcUrl: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
<style>
  html, body {
    margin:0; padding:0; background:#FFFFFF; color:#000000;
    font-family:sans-serif; height:100%; -webkit-text-size-adjust: 100%;
  }
  #viewer { width:100%; height:100vh; box-sizing:border-box; background:#FFFFFF; }
  #status {
    position:fixed; top:50%; left:50%; transform:translate(-50%,-50%);
    color:#666; font-size:14px; z-index:10; text-align:center;
  }
  /* CSS 1차 방어: hr 태그만 명확히 숨김 */
  hr { display: none !important; }
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

  // \u2b50 \uc120 \uc81c\uac70 \ud568\uc218: \uac00\ub85c\uc904\uc774 \uc544\ub2c8\ub77c \uc6d0\ubcf8\uc758 \ucde8\uc18c\uc120/\ubc11\uc904\ub9cc \uc81c\uac70
  function killStrikeLines() {
    try {
      var viewer = document.getElementById('viewer');
      if (!viewer) return;

      // text-decoration (line-through, underline) \uc81c\uac70
      var all = viewer.querySelectorAll('*');
      for (var i = 0; i < all.length; i++) {
        var el = all[i];
        var tag = el.tagName;

        // \ud45c \ud0dc\uadf8\ub294 \uc548\uc804 \uc7a5\uc18c
        if (tag === 'TABLE' || tag === 'TR' || tag === 'TD' || tag === 'TH' ||
            tag === 'TBODY' || tag === 'THEAD' || tag === 'TFOOT' ||
            tag === 'IMG' || tag === 'SVG' || tag === 'PATH') {
          continue;
        }

        var cs = window.getComputedStyle(el);
        // \uae00\uc790 \uc911\uac04\uc744 \uac00\ub85c\uc9c0\ub974\ub294 \uc120 \uc81c\uac70
        if (cs.textDecorationLine && cs.textDecorationLine !== 'none') {
          el.style.setProperty('text-decoration', 'none', 'important');
        }
        // \ubc11\uc904 \uc81c\uac70
        if (cs.borderBottomStyle !== 'none' && cs.borderBottomWidth !== '0px') {
          el.style.setProperty('border-bottom', 'none', 'important');
        }
        // \uc704\ucabd \ud14c\ub450\ub9ac \uc81c\uac70 (\uc120\ucc98\ub7fc \ubcf4\uc77c \uc218 \uc788\uc74c)
        if (cs.borderTopStyle !== 'none' && cs.borderTopWidth !== '0px') {
          el.style.setProperty('border-top', 'none', 'important');
        }
      }

      // hr \ud0dc\uadf8 \ubb34\uc870\uac74 \uc228\uae40
      var hrs = viewer.querySelectorAll('hr');
      for (var j = 0; j < hrs.length; j++) {
        hrs[j].style.display = 'none';
      }
    } catch(e) {
      console.error('killStrikeLines error:', e);
    }
  }

  try {
    if (typeof BaneungDocViewer === 'undefined') {
      status('\u274c BaneungDocViewer \ub85c\ub4dc \uc2e4\ud328');
      return;
    }
    status('\ub80c\ub354\ub9c1 \uc900\ube44 \uc911...');

    var srcUrl = "${srcUrl}";
    var result = BaneungDocViewer.mount('#viewer', { src: srcUrl });

    if (result && typeof result.then === 'function') {
      result.then(function() {
        done();

        // \u2b50 MutationObserver: \ub77c\uc774\ube0c\ub7ec\ub9ac\uac00 \uc0c8 \uc694\uc18c\ub97c \uadf8\ub9b4 \ub54c\ub9c8\ub2e4 \uc120 \uc81c\uac70
        try {
          var viewer = document.getElementById('viewer');
          if (viewer && window.MutationObserver) {
            var obs = new MutationObserver(function(mutations) {
              var needKill = false;
              for (var i = 0; i < mutations.length; i++) {
                if (mutations[i].addedNodes.length > 0) { needKill = true; break; }
              }
              if (needKill) setTimeout(killStrikeLines, 50);
            });
            obs.observe(viewer, { childList: true, subtree: true });
          }
        } catch(_) {}

        // \ub2e8\uacc4\ubcc4 \ud074\ub9ac\ub2dd
        setTimeout(killStrikeLines, 100);
        setTimeout(killStrikeLines, 500);
        setTimeout(killStrikeLines, 1500);
        setTimeout(killStrikeLines, 3000);
      }).catch(function(e) {
        status('\u274c \ub80c\ub354 \uc2e4\ud328: ' + (e && e.message ? e.message : e));
      });
    } else {
      setTimeout(function() {
        done();
        setTimeout(killStrikeLines, 100);
        setTimeout(killStrikeLines, 500);
        setTimeout(killStrikeLines, 1500);
      }, 800);
    }
  } catch (e) {
    status('\u274c \uc608\uc678: ' + (e && e.message ? e.message : e));
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
