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

  private enum Role {
    FOREGROUND,
    BACKGROUND,
    CARD,
    ICON
  }

  private static final java.util.Map<ColorStateList, ColorStateList> cardCache = cache();
  private static final java.util.Map<ColorStateList, ColorStateList> iconCache = cache();

  static ColorStateList map(ColorStateList original, boolean foreground) {
    return map(original, foreground ? Role.FOREGROUND : Role.BACKGROUND);
  }

  static ColorStateList card(ColorStateList original) {
    return map(original, Role.CARD);
  }

  static ColorStateList icon(ColorStateList original) {
    return map(original, Role.ICON);
  }

  private static int color(int original, Role role) {
    return switch (role) {
      case FOREGROUND -> DarkPalette.foreground(original);
      case BACKGROUND -> DarkPalette.background(original);
      case CARD -> DarkPalette.card(original);
      case ICON -> DarkPalette.icon(original);
    };
  }

  private static synchronized ColorStateList map(ColorStateList original, Role role) {
    if (original == null) return null;
    var cache =
        switch (role) {
          case FOREGROUND -> foregroundCache;
          case BACKGROUND -> backgroundCache;
          case CARD -> cardCache;
          case ICON -> iconCache;
        };
    ColorStateList cached = cache.get(original);
    if (cached != null) return cached;
    if (!original.isStateful()) {
      int color = original.getDefaultColor();
      int mapped = color(color, role);
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
        int color = color(colors[i], role);
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
