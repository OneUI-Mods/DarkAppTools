package dev.ryuya.sesameui;

/** Role-aware neutral conversion. Colored state indicators and transparent pixels are preserved. */
final class DarkPalette {
  static final int BACKGROUND = 0xff14191c;
  static final int SURFACE = 0xff20282c;
  static final int TEXT = 0xffe5edef;
  static final int SECONDARY = 0xffb4c2c8;

  static boolean neutral(int color) {
    int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
    return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 20;
  }

  static int foreground(int color) {
    if ((color >>> 24) == 0 || !neutral(color)) return color;
    int level = color & 255;
    return alpha(color, level < 90 || level > 220 ? TEXT : SECONDARY);
  }

  static int background(int color) {
    if ((color >>> 24) == 0 || !neutral(color)) return color;
    int level = color & 255;
    if (level >= 242) return alpha(color, BACKGROUND);
    if (level >= 205) return alpha(color, SURFACE);
    if (level >= 160) return alpha(color, 0xff465259);
    return color;
  }

  private static int alpha(int original, int replacement) {
    return (original & 0xff000000) | (replacement & 0x00ffffff);
  }

  private DarkPalette() {}
}
