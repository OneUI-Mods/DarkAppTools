package dev.ryuya.darkapps

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.view.WindowInsetsController
import dev.ryuya.darkapps.compat.compatibleName
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.jvm.JvmField

/*
 * One per supported app process. Settings are sampled at launch, avoiding destructive screen
 * recreation.
 */
class HostAppearance(context: Context, app: SupportedApp) {
    @JvmField val context: Context
    @JvmField val app: SupportedApp
    @JvmField val icons: NeutralIcons = NeutralIcons()
    @JvmField val nativeViews: NativeAppearance
    @JvmField val artwork: DeviceArtwork
    private val watched = WeakHashMap<View, OnGlobalLayoutListener>()
    @Volatile private var dark: Boolean = false
    private var failures: Int = 0
    private var version: String? = null
    private var guard: CrashGuard? = null

    init {
        this.app = app
        if (app == SupportedApp.POVO) icons.foreground(DarkPalette.ICON)
        nativeViews = NativeAppearance(icons, app)
        artwork = DeviceArtwork(app)
        this.context =
            if (context.getApplicationContext() != null) context.getApplicationContext()
            else context
        initializeAppearance(context, app)
    }

    private fun initializeAppearance(context: Context, app: SupportedApp) {
        val packageInfo = context.getPackageManager().getPackageInfo(app.packageName, 0)
        version = packageInfo.versionName
        var enabled: Boolean = false
        var mode = ThemeChoice.SYSTEM
        var reset: Long = 0
        context.getContentResolver().query(app.uri()!!, null, null, null, null).use { cursor ->
            check(!(cursor == null || !cursor.moveToFirst())) { "Configuration not visible" }
            run {
                enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0
                mode = cursor.getString(cursor.getColumnIndexOrThrow("mode"))
                reset = cursor.getLong(cursor.getColumnIndexOrThrow("reset"))
            }
        }
        if (
            !app.accepts(packageInfo.getLongVersionCode(), version) &&
                !dev.ryuya.darkapps.compat.DexSymbols.provenTarget(context.classLoader)
        ) {
            report("unsupported")
            return
        }
        guard = CrashGuard(context, reset)
        if (!guard!!.allowed()) {
            report("blocked")
            return
        }
        val night: Boolean =
            ((context.getResources().getConfiguration().uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        dark = ThemeChoice.dark(enabled, mode, night)
        if (dark) {
            guard!!.arm()
            applyTheme(this.context)
        }
        report(if (!enabled) "disabled" else if (dark) "dark" else "light")
    }

    fun dark(): Boolean {
        return dark
    }

    fun prepare(activity: Activity) {
        if (!dark || !activity.javaClass.compatibleName().startsWith(app.activityPrefix)) return
        applyTheme(activity)
        activity.getWindow().setBackgroundDrawable(ColorDrawable(DarkPalette.BACKGROUND))
    }

    private fun applyTheme(themed: Context) {
        val theme =
            themed
                .getResources()
                .getIdentifier("ThemeOverlay.AppCompat.Dark", "style", app.packageName)
        if (theme == 0) {
            dark = false
            report("unsupported")
            return
        }
        themed.getTheme().applyStyle(android.R.style.Theme_Material_NoActionBar, true)
        themed.getTheme().applyStyle(theme, true)
    }

    fun window(window: Window?) {
        if (!dark || window == null) return
        window.setStatusBarColor(DarkPalette.BACKGROUND)
        window.setNavigationBarColor(DarkPalette.BACKGROUND)
        window.setStatusBarContrastEnforced(false)
        window.setNavigationBarContrastEnforced(false)
        val controller = window.getInsetsController()
        if (controller != null)
            controller.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        watch(window.getDecorView())
    }

    fun watch(root: View?) {
        if (!dark || root == null) return
        style(root)
        if (watched.containsKey(root)) return
        // Layout events cover navigation, recycled list entries and text updates.
        val owner = WeakReference(root)
        val listener = OnGlobalLayoutListener { owner.get()?.let(::style) }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        watched[root] = listener
    }

    internal fun exportRoots(): Array<WeakReference<View>> =
        watched.keys.map { WeakReference(it) }.toTypedArray()

    internal fun retire() {
        dark = false
        for ((root, listener) in watched) {
            val tree = root.viewTreeObserver
            if (tree.isAlive) tree.removeOnGlobalLayoutListener(listener)
        }
        watched.clear()
    }

    internal fun restoreRoots(roots: Array<*>) {
        for (entry in roots) {
            val root = (entry as? WeakReference<*>)?.get() as? View ?: continue
            if (root.isAttachedToWindow) watch(root)
        }
    }

    fun style(view: View?) {
        if (!dark) return
        try {
            guard!!.arm()
            nativeViews.tree(view)
            failures = 0
        } catch (failure: Throwable) {
            if (++failures >= 3) {
                dark = false
                report("error")
            }
        }
    }

    private fun report(state: String?) {
        try {
            val data = Bundle()
            data.putString("state", state)
            data.putString("version", version)
            context.getContentResolver().call(app.uri()!!, "status", app.packageName, data)
        } catch (unavailable: RuntimeException) {
            /* Optional status must never prevent the host from opening. */
        }
    }
}
