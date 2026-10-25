package dev.ryuya.darkapps;

import android.graphics.drawable.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.util.*;

/** Styles existing native views without replacing listeners, content or layout parameters. */
final class NativeAppearance {
  private final Set<Drawable> detached = Collections.newSetFromMap(new WeakHashMap<>());
  private final Set<Drawable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
  private final PovoAppearance povo;
  private final SesameAppearance sesame;
  private boolean applying;
  private final NeutralIcons icons;

  NativeAppearance(NeutralIcons icons, SupportedApp app) {
    this.icons = icons;
    povo = app == SupportedApp.POVO ? new PovoAppearance() : null;
    sesame = app == SupportedApp.SESAME ? new SesameAppearance() : null;
  }

  void tree(View view) throws ReflectiveOperationException {
    if (applying) return;
    applying = true;
    visited.clear();
    try {
      visit(view);
    } finally {
      visited.clear();
      applying = false;
    }
  }

  boolean profileContent(View view) {
    return povo != null && povo.profileContent(view);
  }

  private void visit(View view) throws ReflectiveOperationException {
    if (view == null) return;
    // Bitmaps, QR codes, camera previews and embedded Chromium surfaces are never recolored.
    if (povo != null) povo.surface(view);
    if (sesame != null) sesame.surface(view);
    background(view.getBackground());
    if (view.getBackgroundTintList() != null) {
      var old = view.getBackgroundTintList();
      var mapped = ColorLists.map(old, false);
      if (mapped != old) view.setBackgroundTintList(mapped);
    }
    boolean brightAccent = brightAccent(view);
    if (view instanceof ImageView image) {
      icons.contrast(image.getDrawable(), brightAccent);
      var tint = image.getImageTintList();
      if (tint != null && !brightAccent) {
        var mapped = povo == null ? ColorLists.map(tint, true) : ColorLists.icon(tint);
        if (mapped != tint) image.setImageTintList(mapped);
      }
      if (povo != null) povo.icon(image, brightAccent);
    }
    if (view instanceof TextView text) {
      for (Drawable icon : text.getCompoundDrawables()) icons.contrast(icon, brightAccent);
      var old = text.getTextColors();
      var mapped = brightAccent ? old : ColorLists.map(old, true);
      if (mapped != old) text.setTextColor(mapped);
      old = text.getHintTextColors();
      mapped = brightAccent ? old : ColorLists.map(old, true);
      if (mapped != old) text.setHintTextColor(mapped);
      old = text.getLinkTextColors();
      mapped = brightAccent ? old : ColorLists.map(old, true);
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

  private boolean brightAccent(View view) throws ReflectiveOperationException {
    // Dark text on a yellow call-to-action must remain readable; photographs are not sampled.
    for (int depth = 0; view != null && depth < 5; depth++) {
      var tint = view.getBackgroundTintList();
      if (tint != null) {
        int color = tint.getColorForState(view.getDrawableState(), tint.getDefaultColor());
        if ((color >>> 24) > 230)
          return !DarkPalette.neutral(color)
              && androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5;
      }
      Drawable d = view.getBackground();
      Integer hostColor = povo == null ? null : povo.backgroundColor(view);
      Integer color =
          hostColor != null
              ? hostColor
              : d instanceof ColorDrawable c
                  ? c.getColor()
                  : d instanceof GradientDrawable g && g.getColor() != null
                      ? g.getColor().getDefaultColor()
                      : null;
      if (color != null && (color >>> 24) > 230) {
        if (DarkPalette.neutral(color)) return false;
        return androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5;
      }
      view = view.getParent() instanceof View parent ? parent : null;
    }
    return false;
  }

  private void background(Drawable drawable) throws ReflectiveOperationException {
    if (drawable == null || !visited.add(drawable)) return;
    if (detached.add(drawable)) drawable.mutate();
    if (povo != null) povo.shape(drawable);
    if (drawable instanceof ColorDrawable color) {
      int mapped = DarkPalette.background(color.getColor());
      if (mapped != color.getColor()) color.setColor(mapped);
    } else if (drawable instanceof GradientDrawable gradient) {
      if (gradient.getColor() != null) {
        var old = gradient.getColor();
        var mapped = ColorLists.map(old, false);
        if (mapped != old) gradient.setColor(mapped);
      }
      int[] colors = gradient.getColors();
      if (colors != null) {
        boolean changed = false;
        for (int i = 0; i < colors.length; i++) {
          int mapped = DarkPalette.background(colors[i]);
          changed |= mapped != colors[i];
          colors[i] = mapped;
        }
        if (changed) gradient.setColors(colors);
      }
    } else if (drawable instanceof LayerDrawable layers) {
      for (int i = 0; i < layers.getNumberOfLayers(); i++) background(layers.getDrawable(i));
    } else if (drawable instanceof StateListDrawable states) {
      for (int i = 0; i < states.getStateCount(); i++) background(states.getStateDrawable(i));
    } else if (drawable instanceof InsetDrawable inset) background(inset.getDrawable());
    else if (drawable instanceof DrawableWrapper wrapper) background(wrapper.getDrawable());
  }
}
