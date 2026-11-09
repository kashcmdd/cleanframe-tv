# Extending CleanFrame TV

The launcher is a WebView app: a static web UI (`app/src/main/assets/`) talking to
Android through one JavaScript bridge (`AndroidBridge`). Almost everything you might
want to add lives in one of three places.

## 1. The bridge — `AndroidBridge.java`

Methods exposed to JavaScript are annotated `@JavascriptInterface`. Add one there and
a matching wrapper in `app/src/main/assets/device.js`, then call it from `main.js`.

Existing methods:

| Method | Purpose |
|---|---|
| `listApps()` | JSON array of installed launchable apps |
| `launchApp(pkg)` | Launch by package name |
| `openSettings()` | Android TV settings |
| `openHome()` | System home intent |
| `exportConfig(json)` | Write config to `Download/cleanframe-config.json` |
| `importConfig()` | Read that file back |

Example — add a method that returns the device model:

```java
@JavascriptInterface
public String deviceModel() {
    return android.os.Build.MODEL;
}
```

```js
// device.js
function deviceModel() {
  var b = bridge();
  return b && b.deviceModel ? b.deviceModel() : '';
}
window.FrameDevice = { /* … */ deviceModel: deviceModel };
```

## 2. App marks — `assets/icons/`

Each icon is a white-on-transparent SVG plus an entry in `metrics.js` describing its
ink box, used to size it consistently:

```js
"myapp": { "n": 240, "x": 20, "y": 40, "w": 200, "h": 160, "fill": 0.6 }
```

Map a package name to a mark id in `AndroidBridge.BRANDS`:

```java
BRANDS.put("com.example.app", "myapp");
```

Apps without a mapping fall back to a monogram tile, so nothing breaks.

## 3. Home apps — `main.js`

`PRIMARY_APPS` defines the built-in app entries (id, display name, brand). Users can
reorder or remove them from **Settings → Apps on Home**; the editor writes to the
saved config.

## Adding your own settings

Settings groups are declared in `settingsRows()` and rendered by `detailSections()`.
A new group needs: a row in `settingsRows()`, an entry in `DETAIL_NAMES`, a branch in
`detailSections()`, and a handler in `detailOk()`.

Values are stored by copying fields through `clean()` (defaults) and `next()`
(persistence) — if you add a field and forget `next()`, it will silently reset on the
next save. That's the one gotcha worth knowing.

## Device-specific forks

Anything hardware- or household-specific belongs in a fork, not here. Examples that
were deliberately left out:

- Smart-light control over Bluetooth (Govee and similar)
- Game-mode shortcuts that launch a streaming client
- Fire OS launcher workarounds (its launcher is a protected package; see the
  `firetv-cleanframe` project for that approach)

Keeping those out means this base stays useful to anyone with an Android TV box.
