package dev.ryuya.sesameui;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Presentation-only hooks, confined to the Sesame main process. */
public final class HookEntry extends XposedModule {
  private HostAppearance appearance;
  private boolean installed;

  public void onPackageReady(PackageReadyParam param) {
    if (installed || !ConfigProvider.TARGET.equals(param.getPackageName())) return;
    installed = true;
    try {
      intercept(
          Application.class.getDeclaredMethod("attach", Context.class),
          chain -> {
            Object result = chain.proceed();
            Context context = (Context) chain.getArg(0);
            if (ConfigProvider.TARGET.equals(context.getPackageName())
                && ConfigProvider.TARGET.equals(Application.getProcessName())) {
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
    } catch (ReflectiveOperationException unsupported) {
      log(
          6,
          "SesameUITools",
          "Platform contract unavailable: " + unsupported.getClass().getSimpleName());
    }
  }

  private void initialize(Context context) {
    if (appearance != null || !ConfigProvider.TARGET.equals(context.getPackageName())) return;
    try {
      appearance = new HostAppearance(context);
    } catch (Exception unavailable) {
      Intent request =
          new Intent()
              .setComponent(
                  new android.content.ComponentName(
                      BuildConfig.APPLICATION_ID,
                      BuildConfig.APPLICATION_ID + ".ConfigurationAccessReceiver"));
      context.sendBroadcast(
          request,
          null,
          android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
      log(
          5,
          "SesameUITools",
          "Configuration access requested: " + unavailable.getClass().getSimpleName());
    }
  }

  private void intercept(Method method, XposedInterface.Hooker hooker) {
    hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
  }
}
