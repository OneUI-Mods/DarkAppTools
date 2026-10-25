package dev.ryuya.darkapps;

import android.content.Context;
import android.content.Intent;

/** A narrow URI grant also makes the preferences provider visible to the scoped app. */
final class ConfigurationAccess {
  static void grant(Context context) {
    for (SupportedApp app : SupportedApp.values()) {
      try {
        context.getPackageManager().getApplicationInfo(app.packageName, 0);
        context.grantUriPermission(
            app.packageName, app.uri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (android.content.pm.PackageManager.NameNotFoundException
          | SecurityException unavailable) {
        // One supported app may be absent while the others remain available.
      }
    }
  }

  private ConfigurationAccess() {}
}
