package dev.ryuya.darkapps;

/** Repeated confirmed host crashes, rather than ordinary force-stops, trigger the guard. */
final class CrashWindow {
  record State(long start, int count) {
    boolean blocked() {
      return count >= 3;
    }
  }

  static State record(long now, long start, int count) {
    return now < start || now - start > 120_000 || count == 0
        ? new State(now, 1)
        : new State(start, count + 1);
  }

  private CrashWindow() {}
}
