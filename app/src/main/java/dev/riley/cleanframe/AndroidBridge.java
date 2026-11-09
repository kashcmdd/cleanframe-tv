package dev.riley.cleanframe;

import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Small Android bridge for CleanFrame's FrameDevice API. It replaces the Tizen
 * application APIs with PackageManager/Intent equivalents.
 */
public final class AndroidBridge {
    private final MainActivity activity;

    private static final Map<String, String> BRANDS = new java.util.HashMap<>();
    static {
        BRANDS.put("org.smarttube.stable", "smarttube");
        BRANDS.put("com.netflix.ninja", "netflix");
        BRANDS.put("com.amazon.amazonvideo.livingroom", "primevideo");
        BRANDS.put("com.nuvio.app", "nuvio");
        BRANDS.put("com.google.android.youtube.tv", "youtube");
        BRANDS.put("com.plexapp.android", "plex");
        BRANDS.put("tv.twitch.android.app", "twitch");
        BRANDS.put("com.esaba.downloader", "downloader");
        BRANDS.put("com.nordvpn.android", "nordvpn");
        BRANDS.put("com.android.vending", "googleplay");
        BRANDS.put("com.android.tv.settings", "settings");
        BRANDS.put("com.rs.explorer.filemanager", "rsfile");
        BRANDS.put("com.phlox.tvwebbrowser", "tvbro");
        // Fire TV package names for the same apps.
        BRANDS.put("com.amazon.firebat", "primevideo");
        BRANDS.put("com.amazon.firetv.youtube", "youtube");
        BRANDS.put("org.smarttube.beta", "smarttube");
        BRANDS.put("com.nuvio.tv", "nuvio");
        BRANDS.put("tv.twitch.android.viewer", "twitch");
        BRANDS.put("com.amazon.tv.settings.v2", "settings");
        BRANDS.put("com.amazon.webview.chromium", "");
    }

    AndroidBridge(MainActivity activity) {
        this.activity = activity;
    }

    /** Returns installed leanback apps as JSON: [{appId,name,brand,icon}]. */
    @JavascriptInterface
    public String listApps() {
        PackageManager pm = activity.getPackageManager();
        // Leanback entries first so a TV activity wins over a phone one, then
        // every plain LAUNCHER app (sideloaded APKs usually only declare that).
        Intent probe = new Intent(Intent.ACTION_MAIN);
        probe.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(probe, 0));
        Intent plain = new Intent(Intent.ACTION_MAIN);
        plain.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo extra : pm.queryIntentActivities(plain, 0)) {
            found.add(extra);
        }

        Map<String, String> labels = new LinkedHashMap<>();
        for (ResolveInfo resolve : found) {
            ActivityInfo info = resolve.activityInfo;
            if (info == null || info.packageName == null) {
                continue;
            }
            String pkg = info.packageName;
            if (pkg.equals(activity.getPackageName())
                    || pkg.equals("com.gathin.tvhome")
                    || pkg.equals("dev.riley.home")
                    || labels.containsKey(pkg)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            String name = label == null ? pkg : label.toString().trim();
            if (name.isEmpty()) {
                continue;
            }
            labels.put(pkg, name);
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(labels.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                int byName = a.getValue().compareToIgnoreCase(b.getValue());
                return byName != 0 ? byName : a.getKey().compareTo(b.getKey());
            }
        });

        JSONArray result = new JSONArray();
        for (Map.Entry<String, String> entry : entries) {
            String brand = BRANDS.get(entry.getKey());
            try {
                result.put(new JSONObject()
                        .put("appId", entry.getKey())
                        .put("name", entry.getValue())
                        .put("brand", brand == null ? "" : brand)
                        .put("icon", ""));
            } catch (Exception ignored) {
                // Skip a malformed entry rather than failing the whole list.
            }
        }
        return result.toString();
    }

    @JavascriptInterface
    public boolean launchApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        PackageManager pm = activity.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(packageName);
        }
        if (intent == null) {
            return false;
        }
        final Intent launch = intent;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(launch);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The page receives a false return only if the intent is missing.
            }
        });
        return true;
    }

    @JavascriptInterface
    public void openSettings() {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Nothing else to try.
            }
        });
    }

    @JavascriptInterface
    public void openHome() {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Not used by the current UI.
            }
        });
    }

}
