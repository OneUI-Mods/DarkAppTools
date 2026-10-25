package dev.ryuya.sesameui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Renews configuration access after boot/update or a request from the verified Sesame UID. */
public final class ConfigurationAccessReceiver extends BroadcastReceiver {
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (Intent.ACTION_BOOT_COMPLETED.equals(action)
        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
      ConfigurationAccess.grant(context);
      return;
    }
    int sender = getSentFromUid();
    try {
      if (sender == context.getPackageManager().getApplicationInfo(ConfigProvider.TARGET, 0).uid)
        ConfigurationAccess.grant(context);
    } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
      // Ignore callers that are not the installed Sesame application.
    }
  }
}
