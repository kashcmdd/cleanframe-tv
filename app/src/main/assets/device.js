/* Android TV bindings for CleanFrame. The native shell exposes AndroidBridge. */
(function () {
  'use strict';

  function bridge() { return window.AndroidBridge || null; }
  function unavailable(done) {
    done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The browser preview cannot control the TV.' });
  }

  function apps(done) {
    var b = bridge();
    if (!b) { unavailable(done); return; }
    try {
      var raw = b.listApps();
      done(null, JSON.parse(raw || '[]'));
    } catch (err) {
      done({ message: 'Could not read installed apps.', detail: err && (err.message || err.name) || '' });
    }
  }

  function launchApp(appId, done) {
    var b = bridge();
    if (!b) { unavailable(done); return; }
    try {
      if (b.launchApp(appId)) done(null);
      else done({ message: 'Could not open this app.', detail: appId });
    } catch (err) {
      done({ message: 'Could not open this app.', detail: err && (err.message || err.name) || '' });
    }
  }

  function launch(brand, done) {
    done({ message: 'Use the app tile to open this app.' });
  }

  function art(done) {
    done({ message: 'Art Mode is available on Samsung TVs only.' });
  }

  function home(done) {
    var b = bridge();
    if (b) b.openHome();
    done(null);
  }

  function settings(done) {
    var b = bridge();
    if (b) b.openSettings();
    done(null);
  }


  function exportConfig(json) {
    var b = bridge();
    return b && b.exportConfig ? b.exportConfig(json) : '';
  }
  function importConfig() {
    var b = bridge();
    return b && b.importConfig ? b.importConfig() : '';
  }

  window.FrameDevice = { launch: launch, launchApp: launchApp, art: art, home: home, settings: settings, apps: apps, exportConfig: exportConfig, importConfig: importConfig };
}());
