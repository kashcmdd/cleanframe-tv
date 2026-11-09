package dev.riley.cleanframe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;

/**
 * Android TV shell for CleanFrame. The original Tizen widget is a static web
 * app; this activity serves it from assets, maps remote keys into CleanFrame's
 * own press() function, and exposes Android app launch/settings through
 * AndroidBridge.
 */
public final class MainActivity extends Activity {
    private static final String START_URL =
            "https://appassets.androidplatform.net/index.html";
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final long LONG_BACK_MS = 650L;
    private static final long DOUBLE_BACK_MS = 450L;

    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private final Runnable longBackAction = () -> {
        backLongPressed = true;
        sendPress("settings");
    };

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private boolean pageReady;
    private boolean backLongPressed;
    private long lastBackAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        hideSystemUi();

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setLongClickable(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setNeedInitialFocus(false);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return uri == null || !ASSET_HOST.equals(uri.getHost());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                view.requestFocus();
                view.evaluateJavascript(
                        "JSON.stringify({w:innerWidth,h:innerHeight,dpr:devicePixelRatio,"
                                + "sw:screen.width,sh:screen.height,"
                                + "vw:(window.visualViewport&&window.visualViewport.width),"
                                + "vh:(window.visualViewport&&window.visualViewport.height),"
                                + "stage:(document.getElementById('stage')||{}).style&&"
                                + "document.getElementById('stage').style.transform,"
                                + "sw2:document.documentElement.scrollWidth,"
                                + "sh2:document.documentElement.scrollHeight})",
                        value -> Log.i("CleanFrameMetrics", value));
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                pageReady = false;
                Toast.makeText(MainActivity.this,
                        "CleanFrame renderer restarted", Toast.LENGTH_SHORT).show();
                reload();
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage message) {
                Log.i("CleanFrameWeb", message.message()
                        + " @" + message.sourceId() + ":" + message.lineNumber());
                return true;
            }
        });

        setContentView(webView);
        WebView.setWebContentsDebuggingEnabled(true);
        webView.loadUrl(START_URL);
    }

    private void reload() {
        if (webView == null) {
            return;
        }
        pageReady = false;
        webView.loadUrl(START_URL);
    }

    /** Runs JavaScript in the CleanFrame page. */
    void evaluate(String javascript) {
        if (!pageReady || webView == null) {
            return;
        }
        webView.evaluateJavascript(javascript, null);
    }




    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (webView != null) {
            webView.onResume();
            webView.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        keyHandler.removeCallbacks(longBackAction);
        backLongPressed = false;
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        keyHandler.removeCallbacks(longBackAction);
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
            if (webView != null) {
                webView.requestFocus();
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    backLongPressed = false;
                    keyHandler.removeCallbacks(longBackAction);
                    keyHandler.postDelayed(longBackAction, LONG_BACK_MS);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                keyHandler.removeCallbacks(longBackAction);
                if (!backLongPressed) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastBackAt <= DOUBLE_BACK_MS) {
                        lastBackAt = 0;
                        sendPress("settings");
                    } else {
                        lastBackAt = now;
                        sendPress("back");
                    }
                }
                backLongPressed = false;
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_UNKNOWN
                || code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_SETTINGS) {
            Log.i("CleanFrameKey", "key=" + code
                    + " scan=" + event.getScanCode()
                    + " action=" + event.getAction()
                    + " repeat=" + event.getRepeatCount());
        }
        String action = mapKey(event);
        if (action == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean repeated = event.getRepeatCount() > 0;
            if (!repeated || (!"ok".equals(action) && !"back".equals(action)
                    && !"settings".equals(action))) {
                sendPress(action);
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        sendPress("back");
    }

    private void sendPress(String action) {
        if (!pageReady || webView == null) {
            return;
        }
        if ("settings".equals(action)) {
            webView.evaluateJavascript(
                    "window.CleanFrameOpenSettings && window.CleanFrameOpenSettings()", null);
            return;
        }
        webView.evaluateJavascript(
                "window.CleanFramePress && window.CleanFramePress('" + action + "')", null);
    }

    private static String mapKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "right";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return "ok";
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                return "back";
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                return "settings";
            case KeyEvent.KEYCODE_UNKNOWN:
                // The Onn remote's gear button is a vendor key the kernel
                // reports as Linux KEY_UNKNOWN (240).
                return event.getScanCode() == 240 ? "settings" : null;
            default:
                return null;
        }
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }
}
