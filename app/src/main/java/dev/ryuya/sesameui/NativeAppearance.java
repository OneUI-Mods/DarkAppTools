package dev.ryuya.sesameui;

import android.graphics.drawable.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.util.*;

/** Styles existing native views without replacing listeners, content or layout parameters. */
final class NativeAppearance {
  private final Set<Drawable> processed = Collections.newSetFromMap(new WeakHashMap<>());
  private boolean applying;

  void tree(View view) {
    if (applying) return;
    applying = true;
    try {
      visit(view);
    } finally {
      applying = false;
    }
  }

  private void visit(View view) {
    if (view == null) return;
    // Bitmaps, QR codes, camera previews and embedded Chromium surfaces are never recolored.
    background(view.getBackground());
    if (view.getBackgroundTintList() != null) {
      var old = view.getBackgroundTintList();
      var mapped = ColorLists.map(old, false);
      if (mapped != old) view.setBackgroundTintList(mapped);
    }
    if (view instanceof TextView text) {
      var old = text.getTextColors();
      var mapped = ColorLists.map(old, true);
      if (mapped != old) text.setTextColor(mapped);
      old = text.getHintTextColors();
      mapped = ColorLists.map(old, true);
      if (mapped != old) text.setHintTextColor(mapped);
      old = text.getLinkTextColors();
      mapped = ColorLists.map(old, true);
      if (mapped != old) text.setLinkTextColor(mapped);
    }
    if (view instanceof WebView web) {
      web.setBackgroundColor(DarkPalette.BACKGROUND);
      if (!web.getSettings().isAlgorithmicDarkeningAllowed())
        web.getSettings().setAlgorithmicDarkeningAllowed(true);
      return;
    }
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i));
  }

  private void background(Drawable drawable) {
    if (drawable == null || !processed.add(drawable)) return;
    drawable.mutate();
    if (drawable instanceof ColorDrawable color)
      color.setColor(DarkPalette.background(color.getColor()));
    else if (drawable instanceof GradientDrawable gradient) {
      if (gradient.getColor() != null)
        gradient.setColor(ColorLists.map(gradient.getColor(), false));
      int[] colors = gradient.getColors();
      if (colors != null) {
        for (int i = 0; i < colors.length; i++) colors[i] = DarkPalette.background(colors[i]);
        gradient.setColors(colors);
      }
    } else if (drawable instanceof LayerDrawable layers) {
      for (int i = 0; i < layers.getNumberOfLayers(); i++) background(layers.getDrawable(i));
    } else if (drawable instanceof StateListDrawable states) {
      for (int i = 0; i < states.getStateCount(); i++) background(states.getStateDrawable(i));
    } else if (drawable instanceof InsetDrawable inset) background(inset.getDrawable());
    else if (drawable instanceof DrawableWrapper wrapper) background(wrapper.getDrawable());
  }
}
