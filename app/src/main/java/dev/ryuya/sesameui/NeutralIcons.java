package dev.ryuya.sesameui;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/** Only monochrome XML vector UI icons are tinted; multicolor assets and bitmaps stay intact. */
final class NeutralIcons {
  private final Map<Integer, Boolean> monochrome = new HashMap<>();

  synchronized Drawable map(Resources resources, int id, Drawable drawable) {
    if (drawable == null || !monochrome.computeIfAbsent(id, key -> inspect(resources, key)))
      return drawable;
    Drawable.ConstantState state = drawable.getConstantState();
    if (state == null) return drawable;
    Drawable copy = state.newDrawable(resources).mutate();
    copy.setTint(DarkPalette.TEXT);
    return copy;
  }

  private boolean inspect(Resources resources, int id) {
    try {
      String name = resources.getResourceEntryName(id);
      if (!(name.startsWith("ic_") || name.startsWith("abc_ic_")) || name.contains("qr"))
        return false;
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
