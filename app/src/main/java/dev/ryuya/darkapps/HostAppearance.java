package dev.ryuya.darkapps;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.*;
import java.util.*;

/**
 * One per supported app process. Settings are sampled at launch, avoiding destructive screen
 * recreation.
 */
final class HostAppearance {
  final Context context;
  final SupportedApp app;
  final NeutralIcons icons = new NeutralIcons();
  final NativeAppearance nativeViews = new NativeAppearance(icons);
  final DeviceArtwork artwork;
  private final Set<View> watched = Collections.newSetFromMap(new WeakHashMap<>());
  private boolean dark;
  private int failures;
  private final String version;
  private CrashGuard guard;

  HostAppearance(Context context, SupportedApp app) throws Exception {
    this.app = app;
    artwork = new DeviceArtwork(app);
    this.context =
        context.getApplicationContext() != null ? context.getApplicationContext() : context;
    var packageInfo = context.getPackageManager().getPackageInfo(app.packageName, 0);
    version = packageInfo.versionName;
    boolean enabled = false;
    String mode = ThemeChoice.SYSTEM;
    long reset = 0;
    try (var cursor = context.getContentResolver().query(app.uri(), null, null, null, null)) {
      if (cursor == null || !cursor.moveToFirst())
        throw new IllegalStateException("Configuration not visible");
      {
        enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0;
        mode = cursor.getString(cursor.getColumnIndexOrThrow("mode"));
        reset = cursor.getLong(cursor.getColumnIndexOrThrow("reset"));
      }
    }
    if (!app.accepts(packageInfo.getLongVersionCode(), version)) {
      report("unsupported");
      return;
    }
    guard = new CrashGuard(context, reset);
    if (!guard.allowed()) {
      report("blocked");
      return;
    }
    boolean night =
        (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    dark = ThemeChoice.dark(enabled, mode, night);
    if (dark) {
      guard.arm();
      applyTheme(this.context);
    }
    report(!enabled ? "disabled" : dark ? "dark" : "light");
  }

  boolean dark() {
    return dark;
  }

  void prepare(Activity activity) {
    if (!dark || !activity.getClass().getName().startsWith(app.activityPrefix)) return;
    applyTheme(activity);
    activity
        .getWindow()
        .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(DarkPalette.BACKGROUND));
  }

  private void applyTheme(Context themed) {
    int theme =
        themed
            .getResources()
            .getIdentifier("ThemeOverlay.AppCompat.Dark", "style", app.packageName);
    if (theme == 0) {
      dark = false;
      report("unsupported");
      return;
    }
    themed.getTheme().applyStyle(android.R.style.Theme_Material_NoActionBar, true);
    themed.getTheme().applyStyle(theme, true);
  }

  void window(Window window) {
    if (!dark || window == null) return;
    window.setStatusBarColor(DarkPalette.BACKGROUND);
    window.setNavigationBarColor(DarkPalette.BACKGROUND);
    window.setStatusBarContrastEnforced(false);
    window.setNavigationBarContrastEnforced(false);
    var controller = window.getInsetsController();
    if (controller != null)
      controller.setSystemBarsAppearance(
          0,
          WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    watch(window.getDecorView());
  }

  void watch(View root) {
    if (!dark || root == null) return;
    style(root);
    if (!watched.add(root)) return;
    // Layout events cover navigation, recycled list entries and text updates, without a frame
    // timer.
    root.getViewTreeObserver().addOnGlobalLayoutListener(() -> style(root));
  }

  void style(View view) {
    if (!dark) return;
    try {
      guard.arm();
      nativeViews.tree(view);
      failures = 0;
    } catch (Throwable failure) {
      if (++failures >= 3) {
        dark = false;
        report("error");
      }
    }
  }

  private void report(String state) {
    try {
      Bundle data = new Bundle();
      data.putString("state", state);
      data.putString("version", version);
      context.getContentResolver().call(app.uri(), "status", app.packageName, data);
    } catch (RuntimeException unavailable) {
      /* Optional status must never prevent the host from opening. */
    }
  }
}
