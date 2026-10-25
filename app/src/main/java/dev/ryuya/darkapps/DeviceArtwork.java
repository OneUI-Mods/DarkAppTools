package dev.ryuya.darkapps;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import java.util.*;

/** Darkens only the reviewed, bundled UI artwork, never user photos or QR codes. */
final class DeviceArtwork {
  private static final Set<String> SESAME_NAMES =
      Set.of(
          "icon_lock",
          "icon_unlock",
          "icon_receiveblee",
          "icon_waitgatt",
          "icon_logining",
          "icon_nosignal",
          "icon_nosetting",
          "icon_opensensor",
          "swtich_no_ble",
          "swtich_receive_ble",
          "swtich_waitgatt",
          "swtich_logining",
          "swtich_locked",
          "swtich_unlocked");

  private static final Set<String> POVO_NAMES =
      Set.of(
          "ic_menu_home",
          "ic_menu_shop",
          "ic_menu_faq",
          "ic_menu_help",
          "ic_menu_games",
          "ic_menu_discover",
          "ic_menu_not",
          "ic_toolbar_profile",
          "toolbar_profile_icon",
          "icon_bell",
          "ud_info_icon",
          "ic_profile_placeholder");
  private final Set<String> names;
  private final boolean povo;

  DeviceArtwork(SupportedApp app) {
    povo = app == SupportedApp.POVO;
    names = povo ? POVO_NAMES : SESAME_NAMES;
  }

  private record Key(int resource, int width, int height, int density) {}

  private final Map<Key, Bitmap> cache =
      new LinkedHashMap<>(32, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<Key, Bitmap> entry) {
          return size() > 32;
        }
      };

  synchronized Drawable map(Resources resources, int id, Drawable drawable) {
    if (!(drawable instanceof BitmapDrawable original)) return drawable;
    try {
      if (!names.contains(resources.getResourceEntryName(id))) return drawable;
    } catch (Resources.NotFoundException missing) {
      return drawable;
    }
    Bitmap source = original.getBitmap();
    if (source == null || source.getWidth() * (long) source.getHeight() > 1_048_576)
      return drawable;
    Key key = new Key(id, source.getWidth(), source.getHeight(), source.getDensity());
    Bitmap result = cache.get(key);
    if (result == null) {
      Bitmap readable =
          source.getConfig() == Bitmap.Config.HARDWARE
              ? source.copy(Bitmap.Config.ARGB_8888, false)
              : source;
      if (readable == null) return drawable;
      int[] pixels = new int[readable.getWidth() * readable.getHeight()];
      readable.getPixels(
          pixels, 0, readable.getWidth(), 0, 0, readable.getWidth(), readable.getHeight());
      for (int i = 0; i < pixels.length; i++) {
        int pixelColor = pixels[i];
        int mapped = DarkPalette.artwork(pixelColor);
        if (povo && DarkPalette.neutral(pixelColor) && (pixelColor >>> 24) != 0)
          mapped =
              (pixelColor & 0xff000000)
                  | (((pixelColor & 255) >= 245 ? DarkPalette.CARD : DarkPalette.ICON)
                      & 0x00ffffff);
        pixels[i] = mapped;
      }
      result =
          Bitmap.createBitmap(
              pixels, readable.getWidth(), readable.getHeight(), Bitmap.Config.ARGB_8888);
      result.setDensity(source.getDensity());
      if (readable != source) readable.recycle();
      cache.put(key, result);
    }
    return new BitmapDrawable(resources, result);
  }
}
