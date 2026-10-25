package dev.ryuya.darkapps;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/** Only monochrome XML vector UI icons are tinted; multicolor assets and bitmaps stay intact. */
final class NeutralIcons {
  private int foreground = DarkPalette.TEXT;

  void foreground(int color) {
    foreground = color;
  }

  private final Map<Integer, Boolean> monochrome = new HashMap<>();
  private final Set<Drawable> mapped = Collections.newSetFromMap(new WeakHashMap<>());

  synchronized Drawable map(Resources resources, int id, Drawable drawable) {
    if (drawable == null || !monochrome.computeIfAbsent(id, key -> inspect(resources, key)))
      return drawable;
    Drawable.ConstantState state = drawable.getConstantState();
    if (state == null) return drawable;
    Drawable copy = state.newDrawable(resources).mutate();
    copy.setTint(foreground);
    mapped.add(copy);
    return copy;
  }

  synchronized void contrast(Drawable drawable, boolean brightBackground) {
    if (drawable != null && mapped.contains(drawable))
      drawable.setTint(brightBackground ? 0xff172327 : foreground);
  }

  private boolean inspect(Resources resources, int id) {
    try {
      String name = resources.getResourceEntryName(id);
      if (!(name.startsWith("ic_") || name.startsWith("abc_ic_") || name.equals("svg_arrow_down"))
          || name.contains("qr")) return false;
      try (XmlResourceParser xml = resources.getXml(id)) {
        boolean vector = false, visible = false;
        for (int event = xml.getEventType();
            event != XmlPullParser.END_DOCUMENT;
            event = xml.next()) {
          if (event != XmlPullParser.START_TAG) continue;
          if (xml.getName().equals("vector")) vector = true;
          if (!vector) return false;
          for (int i = 0; i < xml.getAttributeCount(); i++) {
            String attr = xml.getAttributeName(i);
            if (!(attr.equals("fillColor") || attr.equals("strokeColor"))) continue;
            // Resource references and theme-dependent selectors are left to the host theme.
            if (xml.getAttributeResourceValue(i, 0) != 0) return false;
            int color = xml.getAttributeIntValue(i, 0);
            if ((color >>> 24) == 0) continue;
            if (!DarkPalette.neutral(color)) return false;
            visible = true;
          }
        }
        return vector && visible;
      }
    } catch (Exception unsupported) {
      return false;
    }
  }
}
