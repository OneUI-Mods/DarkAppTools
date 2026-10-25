package dev.ryuya.sesameui;

import android.content.Context;
import android.content.Intent;

/** A narrow URI grant also makes the preferences provider visible to the scoped app. */
final class ConfigurationAccess {
  static void grant(Context context) {
    try {
      context.getPackageManager().getApplicationInfo(ConfigProvider.TARGET, 0);
      context.grantUriPermission(
          ConfigProvider.TARGET, ConfigProvider.URI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (android.content.pm.PackageManager.NameNotFoundException
        | SecurityException unavailable) {
      // Setup remains usable when Sesame is not installed yet.
    }
  }

  private ConfigurationAccess() {}
}
