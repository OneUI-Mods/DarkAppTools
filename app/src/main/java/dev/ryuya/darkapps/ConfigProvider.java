package dev.ryuya.darkapps;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;

/** Per-app appearance settings. Runtime writes are limited to that app's installed UID. */
public final class ConfigProvider extends ContentProvider {
  static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".config/settings");

  static void changed(Context c, SupportedApp app) {
    c.getContentResolver().notifyChange(app.uri(), null);
  }

  public boolean onCreate() {
    return true;
  }

  private void checkCaller(SupportedApp app) {
    int uid = Binder.getCallingUid();
    if (uid == android.os.Process.myUid()) return;
    try {
      if (uid == getContext().getPackageManager().getApplicationInfo(app.packageName, 0).uid)
        return;
    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
    }
    throw new SecurityException("Installed host UID required");
  }

  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    SupportedApp app = SupportedApp.fromUri(uri);
    checkCaller(app);
    var p = app.settings(getContext());
    MatrixCursor out = new MatrixCursor(new String[] {"enabled", "mode", "reset"});
    out.addRow(
        new Object[] {
          p.getBoolean("enabled", true) ? 1 : 0,
          ThemeChoice.valid(p.getString("mode", ThemeChoice.SYSTEM)),
          p.getLong("reset", 0)
        });
    return out;
  }

  public Bundle call(String method, String arg, Bundle data) {
    SupportedApp app = SupportedApp.find(arg);
    if (app == null || !"status".equals(method) || data == null)
      throw new IllegalArgumentException("Unknown call");
    checkCaller(app);
    String state = data.getString("state", "waiting");
    if (!java.util.Set.of("dark", "light", "disabled", "unsupported", "blocked", "error")
        .contains(state)) throw new IllegalArgumentException("Invalid state");
    app.settings(getContext())
        .edit()
        .putString("state", state)
        .putString("host_version", data.getString("version", ""))
        .putLong("checked", System.currentTimeMillis())
        .apply();
    return Bundle.EMPTY;
  }

  public String getType(Uri uri) {
    SupportedApp.fromUri(uri);
    return "vnd.android.cursor.item/vnd.darkapps.settings";
  }

  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri uri, ContentValues values, String where, String[] args) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri uri, String where, String[] args) {
    throw new UnsupportedOperationException();
  }
}
