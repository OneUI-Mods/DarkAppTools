package dev.ryuya.darkapps;

/** Stored values are explicit so new UI choices cannot change existing preferences. */
final class ThemeChoice {
  static final String SYSTEM = "system", DARK = "dark", LIGHT = "light";

  static String valid(String mode) {
    return DARK.equals(mode) || LIGHT.equals(mode) ? mode : SYSTEM;
  }

  static boolean dark(boolean enabled, String mode, boolean systemDark) {
    return enabled && (DARK.equals(mode) || (!LIGHT.equals(mode) && systemDark));
  }

  private ThemeChoice() {}
}
