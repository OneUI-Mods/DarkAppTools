package dev.ryuya.darkapps;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

/** Tracks actual crash evidence only. The host is never killed or restarted by this guard. */
@android.annotation.SuppressLint(
    "ApplySharedPref") // Guard records must survive an immediate host crash.
final class CrashGuard {
  private final SharedPreferences prefs;
  private boolean blocked;
  private long armedAt;

  CrashGuard(Context context, long reset) {
    prefs = context.getSharedPreferences("dark-apps.guard", 0);
    if (reset != prefs.getLong("reset", 0)) prefs.edit().clear().putLong("reset", reset).commit();
    blocked = prefs.getBoolean("blocked", false);
    int pid = prefs.getInt("pid", -1);
    long operation = prefs.getLong("operation", 0), now = System.currentTimeMillis();
    if (pid > 0 && pid != Process.myPid() && now >= operation && now - operation < 120_000) {
      for (ApplicationExitInfo exit :
          context
              .getSystemService(ActivityManager.class)
              .getHistoricalProcessExitReasons(context.getPackageName(), pid, 4)) {
        if (exit.getTimestamp() < operation || exit.getTimestamp() <= prefs.getLong("consumed", 0))
          continue;
        int reason = exit.getReason();
        if (reason == ApplicationExitInfo.REASON_CRASH
            || reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
          CrashWindow.State state =
              CrashWindow.record(now, prefs.getLong("start", 0), prefs.getInt("count", 0));
          blocked |= state.blocked();
          prefs
              .edit()
              .putLong("start", state.start())
              .putInt("count", state.count())
              .putLong("consumed", exit.getTimestamp())
              .putBoolean("blocked", blocked)
              .commit();
        }
        break;
      }
    }
  }

  boolean allowed() {
    return !blocked;
  }

  void arm() {
    long now = System.currentTimeMillis();
    if (armedAt != 0 && now >= armedAt && now - armedAt < 30_000) return;
    if (!prefs.edit().putInt("pid", Process.myPid()).putLong("operation", now).commit()) {
      blocked = true;
      throw new IllegalStateException("Unable to store crash guard state");
    }
    armedAt = now;
  }
}
