package dev.ryuya.sesameui;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;

/** Read-only appearance settings. Only Sesame or this app may write the runtime status. */
public final class ConfigProvider extends ContentProvider {
  static final String TARGET = "co.candyhouse.sesame2";
  static final Uri URI = Uri.parse("content://dev.ryuya.sesameui.config/settings");

  static SharedPreferences settings(Context c) {
    return c.getSharedPreferences("appearance", 0);
  }

  static void changed(Context c) {
    c.getContentResolver().notifyChange(URI, null);
  }

  public boolean onCreate() {
    return true;
  }

  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
    var p = settings(getContext());
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
    int uid = Binder.getCallingUid();
    if (uid != android.os.Process.myUid()) {
      try {
        if (uid != getContext().getPackageManager().getApplicationInfo(TARGET, 0).uid)
          throw new SecurityException("Sesame UID required");
      } catch (android.content.pm.PackageManager.NameNotFoundException e) {
        throw new SecurityException(e);
      }
    }
    if (!"status".equals(method) || data == null)
      throw new IllegalArgumentException("Unknown call");
    String status = data.getString("state", "waiting");
    if (!java.util.Set.of("dark", "light", "disabled", "unsupported", "blocked", "error")
        .contains(status)) throw new IllegalArgumentException("Invalid state");
    settings(getContext())
        .edit()
        .putString("state", status)
        .putString("host_version", data.getString("version", ""))
        .putLong("checked", System.currentTimeMillis())
        .apply();
    return Bundle.EMPTY;
  }

  public String getType(Uri uri) {
    return "vnd.android.cursor.item/vnd.sesameui.settings";
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
