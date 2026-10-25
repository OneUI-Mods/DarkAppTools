package dev.ryuya.darkapps;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Presentation-only hooks, confined to each supported app’s main process. */
public final class HookEntry extends XposedModule {
  private HostAppearance appearance;
  private boolean installed;
  private SupportedApp app;

  public void onPackageReady(PackageReadyParam param) {
    if (installed || (app = SupportedApp.find(param.getPackageName())) == null) return;
    installed = true;
    try {
      intercept(
          Application.class.getDeclaredMethod("attach", Context.class),
          chain -> {
            Object result = chain.proceed();
            Context context = (Context) chain.getArg(0);
            if (app.packageName.equals(context.getPackageName())
                && app.packageName.equals(Application.getProcessName())) {
              initialize(context);
            }
            return result;
          });
      intercept(
          Instrumentation.class.getDeclaredMethod(
              "callActivityOnCreate", Activity.class, Bundle.class),
          chain -> {
            Activity activity = (Activity) chain.getArg(0);
            if (appearance == null) initialize(activity);
            if (appearance != null) appearance.prepare(activity);
            Object result = chain.proceed();
            if (appearance != null) appearance.window(activity.getWindow());
            return result;
          });
      intercept(
          Instrumentation.class.getDeclaredMethod("callActivityOnResume", Activity.class),
          chain -> {
            Object result = chain.proceed();
            Activity activity = (Activity) chain.getArg(0);
            if (appearance == null) initialize(activity);
            if (appearance != null) appearance.window(activity.getWindow());
            return result;
          });
      intercept(
          Dialog.class.getDeclaredMethod("show"),
          chain -> {
            Object result = chain.proceed();
            if (appearance != null) appearance.window(((Dialog) chain.getThisObject()).getWindow());
            return result;
          });
      intercept(
          Resources.class.getDeclaredMethod("getDrawable", int.class, Resources.Theme.class),
          chain -> {
            Drawable result = (Drawable) chain.proceed();
            if (appearance == null || !appearance.dark()) return result;
            return appearance.icons.map(
                (Resources) chain.getThisObject(), (int) chain.getArg(0), result);
          });
      installDrawableHook();
      // Binding code can recolor a recycled row without a layout pass.
      for (Class<?> arg : new Class<?>[] {int.class, android.content.res.ColorStateList.class}) {
        intercept(
            TextView.class.getDeclaredMethod("setTextColor", arg),
            chain -> {
              Object result = chain.proceed();
              if (appearance != null) appearance.style((View) chain.getThisObject());
              return result;
            });
      }
      // A list adapter can replace backgrounds or glyphs after layout without changing text.
      // Only attached povo views are revisited; partially constructed views stay untouched.
      intercept(
          View.class.getDeclaredMethod("onAttachedToWindow"),
          chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            if (appearance != null && appearance.nativeViews.attachedContent(view))
              appearance.style(view);
            return result;
          });
      intercept(
          View.class.getDeclaredMethod("setBackgroundColor", int.class),
          chain -> {
            Object result = chain.proceed();
            restyleAttached((View) chain.getThisObject());
            return result;
          });
      intercept(
          View.class.getDeclaredMethod("setBackgroundDrawable", Drawable.class),
          chain -> {
            Object result = chain.proceed();
            restyleAttached((View) chain.getThisObject());
            return result;
          });
      intercept(
          View.class.getDeclaredMethod(
              "setBackgroundTintList", android.content.res.ColorStateList.class),
          chain -> {
            Object result = chain.proceed();
            restyleAttached((View) chain.getThisObject());
            return result;
          });
      intercept(
          ImageView.class.getDeclaredMethod("setImageDrawable", Drawable.class),
          chain -> {
            Object result = chain.proceed();
            restyleAttached((View) chain.getThisObject());
            return result;
          });
    } catch (ReflectiveOperationException unsupported) {
      log(
          6,
          "DarkAppTools",
          "Platform contract unavailable: " + unsupported.getClass().getSimpleName());
    }
  }

  private void restyleAttached(View view) {
    if (app == SupportedApp.POVO && appearance != null && view.isAttachedToWindow())
      appearance.style(view);
  }

  /**
   * Vector permits hidden-API access in the scoped host; this is never called by the settings app.
   */
  @android.annotation.SuppressLint("SoonBlockedPrivateApi")
  private void installDrawableHook() {
    try {
      Class<?> implementation = Class.forName("android.content.res.ResourcesImpl");
      intercept(
          implementation.getDeclaredMethod(
              "loadDrawable",
              Resources.class,
              android.util.TypedValue.class,
              int.class,
              int.class,
              Resources.Theme.class),
          chain -> {
            Drawable result = (Drawable) chain.proceed();
            if (appearance == null || !appearance.dark()) return result;
            Resources resources = (Resources) chain.getArg(0);
            int id = (int) chain.getArg(2);
            result = appearance.artwork.map(resources, id, result);
            return appearance.icons.map(resources, id, result);
          });
    } catch (ReflectiveOperationException unavailable) {
      log(5, "DarkAppTools", "Using native view fallback for drawable colors");
    }
  }

  private void initialize(Context context) {
    if (appearance != null || !app.packageName.equals(context.getPackageName())) return;
    try {
      appearance = new HostAppearance(context, app);
    } catch (Exception unavailable) {
      Intent request =
          new Intent()
              .setComponent(
                  new android.content.ComponentName(
                      BuildConfig.APPLICATION_ID, ConfigurationAccessReceiver.class.getName()));
      context.sendBroadcast(
          request,
          null,
          android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
      log(
          5,
          "DarkAppTools",
          "Configuration access requested: " + unavailable.getClass().getSimpleName());
    }
  }

  private void intercept(Method method, XposedInterface.Hooker hooker) {
    hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
  }
}
