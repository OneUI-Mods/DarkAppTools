package dev.ryuya.darkapps;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SupportedAppTest {
  @Test
  public void unknownPackageCannotEnterTheModule() {
    assertNull(SupportedApp.find("com.android.settings"));
    assertEquals(SupportedApp.POVO, SupportedApp.find("com.kddi.kdla.jp"));
  }

  @Test
  public void reviewsAreIndependentForEachHost() {
    assertTrue(SupportedApp.SESAME.accepts(906, "3.0.266"));
    assertTrue(SupportedApp.POVO.accepts(864, "1.71.0-JP"));
    assertFalse(SupportedApp.SESAME.accepts(864, "1.71.0-JP"));
  }

  @Test
  public void unknownUpdatesKeepTheOriginalAppearance() {
    assertFalse(SupportedApp.POVO.accepts(865, "1.71.0-JP"));
    assertFalse(SupportedApp.POVO.accepts(864, "1.72.0-JP"));
  }
}
