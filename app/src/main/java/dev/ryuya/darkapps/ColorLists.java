package dev.ryuya.darkapps;

import android.content.res.ColorStateList;
import android.os.Parcel;

/** Parcel round-trip retains every original state specification, including custom app states. */
final class ColorLists {
  private static final java.util.Map<ColorStateList, ColorStateList> foregroundCache = cache();
  private static final java.util.Map<ColorStateList, ColorStateList> backgroundCache = cache();

  private static java.util.Map<ColorStateList, ColorStateList> cache() {
    return new java.util.LinkedHashMap<>(128, .75f, true) {
      protected boolean removeEldestEntry(
          java.util.Map.Entry<ColorStateList, ColorStateList> entry) {
        return size() > 128;
      }
    };
  }

  static synchronized ColorStateList map(ColorStateList original, boolean foreground) {
    if (original == null) return null;
    var cache = foreground ? foregroundCache : backgroundCache;
    ColorStateList cached = cache.get(original);
    if (cached != null) return cached;
    if (!original.isStateful()) {
      int color = original.getDefaultColor();
      int mapped = foreground ? DarkPalette.foreground(color) : DarkPalette.background(color);
      ColorStateList result = mapped == color ? original : ColorStateList.valueOf(mapped);
      cache.put(original, result);
      return result;
    }
    Parcel parcel = Parcel.obtain();
    try {
      original.writeToParcel(parcel, 0);
      parcel.setDataPosition(0);
      int count = parcel.readInt();
      if (count < 1 || count > 256) return original;
      int[][] states = new int[count][];
      for (int i = 0; i < count; i++) states[i] = parcel.createIntArray();
      int[] colors = parcel.createIntArray();
      if (colors == null || colors.length != count) return original;
      boolean changed = false;
      for (int i = 0; i < count; i++) {
        int color =
            foreground ? DarkPalette.foreground(colors[i]) : DarkPalette.background(colors[i]);
        changed |= color != colors[i];
        colors[i] = color;
      }
      ColorStateList result = changed ? new ColorStateList(states, colors) : original;
      cache.put(original, result);
      return result;
    } finally {
      parcel.recycle();
    }
  }

  private ColorLists() {}
}
