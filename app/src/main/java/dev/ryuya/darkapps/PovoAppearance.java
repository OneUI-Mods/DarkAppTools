package dev.ryuya.darkapps;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.ImageView;
import java.lang.reflect.*;
import java.util.*;

/** Reviewed povo 1.71.0-JP adapters; preserve host geometry and rasterize only known UI glyphs. */
final class PovoAppearance {
  private record Card(
      Method get, Method set, Method strokeColor, Method strokeWidth, Method getStrokeColor) {
    static final Card NONE = new Card(null, null, null, null, null);
  }

  private static final ClassValue<Card> CARDS =
      new ClassValue<>() {
        protected Card computeValue(Class<?> type) {
          for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            if (!parent.getName().equals("androidx.cardview.widget.CardView")) continue;
            try {
              Method strokeColor = null, strokeWidth = null, getStrokeColor = null;
              try {
                strokeColor = type.getMethod("setStrokeColor", int.class);
                strokeWidth = type.getMethod("setStrokeWidth", int.class);
                getStrokeColor = type.getMethod("getStrokeColor");
              } catch (NoSuchMethodException ordinaryCard) {
                /* Surface contrast is sufficient for plain CardView. */
              }
              return new Card(
                  type.getMethod("getCardBackgroundColor"),
                  type.getMethod("setCardBackgroundColor", ColorStateList.class),
                  strokeColor,
                  strokeWidth,
                  getStrokeColor);
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
    profileSurface(view);
    Card card = CARDS.get(view.getClass());
    if (card == Card.NONE) return;
    ColorStateList old = (ColorStateList) card.get.invoke(view);
    ColorStateList mapped = ColorLists.card(old);
    if (mapped != old) card.set.invoke(view, mapped);
    if (card.strokeColor != null && card.strokeWidth != null && card.getStrokeColor != null) {
      int oldStroke = (int) card.getStrokeColor.invoke(view);
      if (oldStroke == 0 || DarkPalette.neutral(oldStroke)) {
        if (oldStroke != DarkPalette.CARD_OUTLINE)
          card.strokeColor.invoke(view, DarkPalette.CARD_OUTLINE);
        if (outlined.add(view))
          card.strokeWidth.invoke(
              view, Math.max(1, Math.round(view.getResources().getDisplayMetrics().density)));
      }
    }
  }

  private void profileSurface(View view) {
    // The host's profile is a fragment over the home screen. Its #faf7f7f7 background
    // is almost opaque in light mode, but exposes home content after dark conversion.
    if (name(view).equals("profile_list")) {
      // Data binding clears the XML tag. Find the fragment's actual root by its container.
      for (View root = view; root.getParent() instanceof View parent; root = parent) {
        if (name(parent).equals("help_root_container")) {
          solidColor(root, DarkPalette.BACKGROUND);
          break;
        }
      }
      return;
    }
    if (navigationRow(view) && insideProfile(view))
      // Only the reviewed navigation rows, preserving their padding and click handlers.
      solidColor(view, DarkPalette.CARD);
  }

  boolean profileContent(View view) {
    return name(view).equals("profile_list") || (navigationRow(view) && insideProfile(view));
  }

  private boolean navigationRow(View view) {
    if (!(view instanceof android.view.ViewGroup row)) return false;
    boolean arrow = false, actionText = false;
    for (int i = 0; i < row.getChildCount(); i++) {
      String child = name(row.getChildAt(i));
      arrow |= child.equals("ivNavArrow");
      actionText |=
          child.equals("tvUnderlinedHelpText")
              || child.equals("tvAddress")
              || child.equals("tvCardNo");
    }
    return arrow && actionText;
  }

  private boolean insideProfile(View view) {
    for (View parent = view; parent != null; ) {
      if (name(parent).equals("profile_list")) {
        return true;
      }
      parent = parent.getParent() instanceof View v ? v : null;
    }
    return false;
  }

  private void solidColor(View view, int color) {
    if (view.getBackground() instanceof ColorDrawable background
        && background.getColor() != color) {
      background.mutate();
      background.setColor(color);
    }
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

  private final Set<View> outlined = Collections.newSetFromMap(new WeakHashMap<>());
  private final Map<Drawable, Map<Long, Bitmap>> pictures = new WeakHashMap<>();

  private void rasterizePicture(ImageView image) {
    Drawable source = image.getDrawable();
    if (!(source instanceof PictureDrawable)) return;
    int width = Math.max(image.getWidth(), source.getIntrinsicWidth());
    int height = Math.max(image.getHeight(), source.getIntrinsicHeight());
    if (width <= 0 || height <= 0 || width > 512 || height > 512) return;
    long size = ((long) width << 32) | height;
    Map<Long, Bitmap> sizes = pictures.computeIfAbsent(source, ignored -> new HashMap<>());
    Bitmap bitmap = sizes.get(size);
    if (bitmap == null) {
      bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      bitmap.setDensity(image.getResources().getDisplayMetrics().densityDpi);
      Picture picture = ((PictureDrawable) source).getPicture();
      if (picture == null) return;
      new Canvas(bitmap).drawPicture(picture, new Rect(0, 0, width, height));
      sizes.put(size, bitmap);
    }
    image.setImageDrawable(new BitmapDrawable(image.getResources(), bitmap));
  }

  void icon(ImageView image, boolean brightAccent) {
    String name = name(image);
    boolean reviewed = MONOCHROME_VIEWS.contains(name);
    // The account header reuses ivNavArrow for a yellow info image. Only tint the reviewed
    // navigation chevrons and the address/payment pencils, preserving that colored indicator.
    if (name.equals("ivNavArrow") && image.getParent() instanceof View parent)
      reviewed = navigationRow(parent);
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
    rasterizePicture(image);
    int color = brightAccent ? 0xff172327 : DarkPalette.ICON;
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
