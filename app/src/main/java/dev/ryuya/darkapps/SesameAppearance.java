package dev.ryuya.darkapps;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;

/** Reviewed Sesame search header; tint existing shapes without changing scrolling or geometry. */
final class SesameAppearance {
  void surface(View view) {
    String name;
    try {
      name = view.getResources().getResourceEntryName(view.getId());
    } catch (Resources.NotFoundException unnamed) {
      return;
    }
    if (name.equals("appBarLayout")) {
      // AppBarLayout replaces the XML ColorDrawable with its bundled Material shape.
      // View tint covers that fill through its public API, preserving the host drawable.
      background(view, DarkPalette.BACKGROUND);
    } else if (name.equals("searchEditText") && view instanceof TextView search) {
      background(view, DarkPalette.SURFACE);
      if (search.getCurrentHintTextColor() != DarkPalette.SECONDARY)
        search.setHintTextColor(DarkPalette.SECONDARY);
      ColorStateList current = search.getCompoundDrawableTintList();
      if (current == null || current.isStateful() || current.getDefaultColor() != DarkPalette.ICON)
        search.setCompoundDrawableTintList(ColorStateList.valueOf(DarkPalette.ICON));
    }
  }

  private void background(View view, int color) {
    ColorStateList current = view.getBackgroundTintList();
    if (current == null || current.isStateful() || current.getDefaultColor() != color)
      view.setBackgroundTintList(ColorStateList.valueOf(color));
  }
}
