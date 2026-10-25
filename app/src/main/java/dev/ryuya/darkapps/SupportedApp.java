package dev.ryuya.darkapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Explicitly reviewed host versions; unknown updates retain their original appearance. */
enum SupportedApp {
  SESAME(
      "co.candyhouse.sesame2",
      "sesame",
      906,
      "3.0.266",
      "co.candyhouse.app.",
      R.string.sesame_title),
  POVO("com.kddi.kdla.jp", "povo", 864, "1.71.0-JP", "com.circles.", R.string.povo_title);
  final String packageName, key, version, activityPrefix;
  final long code;
  final int title;

  SupportedApp(
      String packageName, String key, long code, String version, String activityPrefix, int title) {
    this.packageName = packageName;
    this.key = key;
    this.code = code;
    this.version = version;
    this.activityPrefix = activityPrefix;
    this.title = title;
  }

  boolean accepts(long versionCode, String versionName) {
    return code == versionCode && version.equals(versionName);
  }

  Uri uri() {
    return ConfigProvider.URI.buildUpon().appendPath(key).build();
  }

  SharedPreferences settings(Context context) {
    // Retain the installed Sesame module's existing settings when upgrading in place.
    return context.getSharedPreferences(this == SESAME ? "appearance" : "appearance_povo", 0);
  }

  static SupportedApp find(String packageName) {
    for (SupportedApp app : values()) if (app.packageName.equals(packageName)) return app;
    return null;
  }

  static SupportedApp fromUri(Uri uri) {
    if (ConfigProvider.URI.equals(uri)) return SESAME;
    for (SupportedApp app : values()) if (app.uri().equals(uri)) return app;
    throw new IllegalArgumentException("Unknown configuration URI");
  }
}
