package dev.ryuya.darkapps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Renews configuration access after boot/update or a request from the verified supported app UID.
 */
public final class ConfigurationAccessReceiver extends BroadcastReceiver {
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (Intent.ACTION_BOOT_COMPLETED.equals(action)
        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
      ConfigurationAccess.grant(context);
      return;
    }
    int sender = getSentFromUid();
    for (SupportedApp app : SupportedApp.values()) {
      try {
        if (sender == context.getPackageManager().getApplicationInfo(app.packageName, 0).uid) {
          ConfigurationAccess.grant(context);
          return;
        }
      } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
      }
    }
  }
}
