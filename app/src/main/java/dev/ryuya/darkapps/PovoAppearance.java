package dev.ryuya.darkapps;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import java.lang.reflect.*;
import java.util.Set;

/** Reviewed rendering adapters for povo 1.71.0-JP; never substitute host views or drawables. */
final class PovoAppearance {
  private record Card(Method get, Method set) {
    static final Card NONE = new Card(null, null);
  }

  private static final ClassValue<Card> CARDS =
      new ClassValue<>() {
        protected Card computeValue(Class<?> type) {
          for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            if (!parent.getName().equals("androidx.cardview.widget.CardView")) continue;
            try {
              return new Card(
                  type.getMethod("getCardBackgroundColor"),
                  type.getMethod("setCardBackgroundColor", ColorStateList.class));
            } catch (NoSuchMethodException unavailable) {
              return Card.NONE;
            }
          }
          return Card.NONE;
        }
      };

  private record Shape(Field fill, Method set) {
    static final Shape NONE = new Shape(null, null);
  }

  private static final ClassValue<Shape> SHAPES =
      new ClassValue<>() {
        protected Shape computeValue(Class<?> type) {
          // This bundled MaterialShapeDrawable was minified by the reviewed povo build.
          // Its constant-state field c is the fill; method m updates it and invalidates the
          // drawable.
          // HostAppearance's exact version gate must remain in place for this contract.
          for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            if (!parent.getName().equals("Md.g")) continue;
            try {
              Class<?> state = Class.forName("Md.g$b", false, type.getClassLoader());
              Field fill = state.getField("c");
              if (fill.getType() != ColorStateList.class) return Shape.NONE;
              return new Shape(fill, parent.getMethod("m", ColorStateList.class));
            } catch (ReflectiveOperationException unavailable) {
              return Shape.NONE;
            }
          }
          return Shape.NONE;
        }
      };

  private static final Set<String> MONOCHROME_VIEWS =
      Set.of("planDetailsIcon", "planHistoryIcon", "navigation_bar_item_icon_view");

  void surface(View view) throws ReflectiveOperationException {
    Card card = CARDS.get(view.getClass());
    if (card == Card.NONE) return;
    ColorStateList old = (ColorStateList) card.get.invoke(view);
    ColorStateList mapped = ColorLists.map(old, false);
    if (mapped != old) card.set.invoke(view, mapped);
  }

  void shape(Drawable drawable) throws ReflectiveOperationException {
    Shape shape = SHAPES.get(drawable.getClass());
    if (shape == Shape.NONE || drawable.getConstantState() == null) return;
    ColorStateList old = (ColorStateList) shape.fill.get(drawable.getConstantState());
    ColorStateList mapped = ColorLists.map(old, false);
    if (mapped != old) shape.set.invoke(drawable, mapped);
  }

  Integer backgroundColor(View view) throws ReflectiveOperationException {
    Card card = CARDS.get(view.getClass());
    ColorStateList colors = null;
    if (card != Card.NONE) colors = (ColorStateList) card.get.invoke(view);
    else if (view.getBackground() != null) {
      Drawable drawable = view.getBackground();
      Shape shape = SHAPES.get(drawable.getClass());
      if (shape != Shape.NONE && drawable.getConstantState() != null)
        colors = (ColorStateList) shape.fill.get(drawable.getConstantState());
    }
    return colors == null
        ? null
        : colors.getColorForState(view.getDrawableState(), colors.getDefaultColor());
  }

  void icon(ImageView image, boolean brightAccent) {
    String name = name(image);
    boolean reviewed = MONOCHROME_VIEWS.contains(name);
    if (name.equals("icon")) {
      // Only the SMS/call glyphs inside the reviewed usage cards, not arbitrary ImageViews.
      for (View parent = image; parent != null; ) {
        if (name(parent).equals("telcoDetail")) {
          reviewed = true;
          break;
        }
        parent = parent.getParent() instanceof View v ? v : null;
      }
    }
    if (!reviewed) return;
    int color = brightAccent ? 0xff172327 : DarkPalette.TEXT;
    ColorStateList current = image.getImageTintList();
    if (current == null || current.isStateful() || current.getDefaultColor() != color)
      image.setImageTintList(ColorStateList.valueOf(color));
  }

  private String name(View view) {
    if (view.getId() == View.NO_ID) return "";
    try {
      return view.getResources().getResourceEntryName(view.getId());
    } catch (android.content.res.Resources.NotFoundException missing) {
      return "";
    }
  }
}
