package dev.ryuya.darkapps;

import static org.junit.Assert.*;

import org.junit.Test;

public final class AppearancePolicyTest {
  @Test
  public void preservesDeviceStateColors() {
    for (int color : new int[] {0xff28aeb1, 0xffcc4a44, 0xff43a047, 0xff3366ff}) {
      assertEquals(color, DarkPalette.foreground(color));
      assertEquals(color, DarkPalette.background(color));
    }
  }

  @Test
  public void artworkKeepsStateColorsAndHasDarkSurround() {
    assertEquals(DarkPalette.SURFACE, DarkPalette.artwork(0xffffffff));
    assertEquals(0xffcc4a44, DarkPalette.artwork(0xffcc4a44));
    assertEquals(0xff28aeb1, DarkPalette.artwork(0xff28aeb1));
    assertEquals(0, DarkPalette.artwork(0));
  }

  @Test
  public void keepsTransparency() {
    assertEquals(0x00000000, DarkPalette.background(0x00000000));
    assertEquals(0x00000000, DarkPalette.foreground(0x00000000));
    assertEquals(0x66, DarkPalette.foreground(0x66000000) >>> 24);
  }

  @Test
  public void createsContrastWithoutRepeatedInversion() {
    assertEquals(DarkPalette.BACKGROUND, DarkPalette.background(0xffffffff));
    assertEquals(DarkPalette.TEXT, DarkPalette.foreground(0xff000000));
    assertEquals(DarkPalette.BACKGROUND, DarkPalette.background(DarkPalette.BACKGROUND));
    assertEquals(DarkPalette.TEXT, DarkPalette.foreground(DarkPalette.TEXT));
    assertEquals(DarkPalette.SECONDARY, DarkPalette.foreground(DarkPalette.SECONDARY));
  }

  @Test
  public void systemModeFollowsDeviceAndSwitchWins() {
    assertTrue(ThemeChoice.dark(true, ThemeChoice.SYSTEM, true));
    assertFalse(ThemeChoice.dark(true, ThemeChoice.SYSTEM, false));
    assertTrue(ThemeChoice.dark(true, ThemeChoice.DARK, false));
    assertFalse(ThemeChoice.dark(true, ThemeChoice.LIGHT, true));
    assertFalse(ThemeChoice.dark(false, ThemeChoice.DARK, true));
  }

  @Test
  public void crashWindowExpiresAndRequiresThreeCrashes() {
    var a = CrashWindow.record(1000, 0, 0);
    assertFalse(a.blocked());
    var b = CrashWindow.record(2000, a.start(), a.count());
    assertFalse(b.blocked());
    var c = CrashWindow.record(3000, b.start(), b.count());
    assertTrue(c.blocked());
    assertEquals(1, CrashWindow.record(130001, c.start(), c.count()).count());
    assertEquals(1, CrashWindow.record(100, c.start(), c.count()).count());
  }
}
