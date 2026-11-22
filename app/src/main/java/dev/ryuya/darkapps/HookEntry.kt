package dev.ryuya.darkapps

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.BroadcastOptions
import android.app.Dialog
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import dev.ryuya.darkapps.compat.DexSymbols
import dev.ryuya.darkapps.compat.compatibleDeclaredMethod
import dev.ryuya.darkapps.compat.compatibleField
import dev.ryuya.darkapps.compat.compatibleName
import dev.ryuya.darkapps.startup.FirstUnlockGate
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import kotlin.math.log

/* Presentation-only hooks, confined to each supported app’s main process. */
class HookEntry : XposedModule() {
    private var appearance: HostAppearance? = null
    private val firstUnlock = FirstUnlockGate()
    private var installed: Boolean = false
    private var app: SupportedApp? = null
    private var listBindingInstalled: Boolean = false
    private val main = Handler(Looper.getMainLooper())
    private val handles = ArrayList<HookHandle>()
    @Volatile private var retired = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (installed || (SupportedApp.find(param.getPackageName()).also { app = it }) == null)
            return
        installed = true
        installHooks(initial = true)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        return MainThreadReload.run(main) handOff@{
            val host = appearance ?: return@handOff false
            if (Application.getProcessName() != host.app.packageName) return@handOff false
            param.setSavedInstanceState(arrayOf(host.context, host.exportRoots()))
            retired = true
            host.retire()
            DexSymbols.detachObserver()
            true
        }
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val state = param.savedInstanceState as? Array<*> ?: return
        val host = state.getOrNull(0) as? Context ?: return
        if (param.processName != host.packageName) return
        val supported = SupportedApp.find(host.packageName) ?: return
        val roots = state.getOrNull(1) as? Array<*> ?: return
        val previous = param.oldHookHandles
        main.post {
            installed = true
            app = supported
            DexSymbols.whenPrepared(host) {
                if (!installHooks(initial = false)) return@whenPrepared
                initializePrepared(host)
                val ready = appearance
                if (ready == null) {
                    removeHooks()
                    return@whenPrepared
                }
                ready.restoreRoots(roots)
                previous.forEach { it.unhook() }
                log(4, "DarkAppTools", "ONEUI_MODS_HOT_RELOAD package=${BuildConfig.APPLICATION_ID} " +
                    "version=${BuildConfig.VERSION_CODE} pid=${Process.myPid()}")
            }
        }
    }

    private fun installHooks(initial: Boolean): Boolean {
        try {
            if (initial) intercept(
                Application::class.java.compatibleDeclaredMethod("attach", Context::class.java),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    val context: Context? = chain!!.getArg(0) as Context?
                    if (
                        app!!.packageName == context!!.getPackageName() &&
                            app!!.packageName == Application.getProcessName()
                    ) {
                        initialize(context!!)
                    }
                    result
                },
            )
            intercept(
                Instrumentation::class
                    .java
                    .compatibleDeclaredMethod(
                        "callActivityOnCreate",
                        Activity::class.java,
                        Bundle::class.java,
                    ),
                Hooker { chain: Chain? ->
                    val activity: Activity? = chain!!.getArg(0) as Activity?
                    if (appearance == null) initialize(activity!!)
                    if (appearance != null) appearance!!.prepare(activity!!)
                    val result = chain!!.proceed()
                    if (appearance != null) appearance!!.window(activity!!.getWindow())
                    result
                },
            )
            intercept(
                Instrumentation::class
                    .java
                    .compatibleDeclaredMethod("callActivityOnResume", Activity::class.java),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    val activity: Activity? = chain!!.getArg(0) as Activity?
                    if (appearance == null) initialize(activity!!)
                    if (appearance != null) appearance!!.window(activity!!.getWindow())
                    result
                },
            )
            intercept(
                Dialog::class.java.compatibleDeclaredMethod("show"),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    if (appearance != null)
                        appearance!!.window((chain!!.getThisObject() as Dialog).getWindow())
                    result
                },
            )
            intercept(
                Resources::class
                    .java
                    .compatibleDeclaredMethod(
                        "getDrawable",
                        Int::class.javaPrimitiveType,
                        Theme::class.java,
                    ),
                Hooker { chain: Chain? ->
                    val result: Drawable? = chain!!.proceed() as Drawable?
                    if (appearance == null || !appearance!!.dark()) return@Hooker result
                    appearance!!
                        .icons
                        .map(
                            chain!!.getThisObject() as Resources,
                            chain!!.getArg(0) as Int,
                            result,
                        )
                },
            )
            installDrawableHook()
            // Binding code can recolor a recycled row without a layout pass.
            for (arg in
                arrayOf<Class<*>?>(Int::class.javaPrimitiveType, ColorStateList::class.java)) {
                intercept(
                    TextView::class.java.compatibleDeclaredMethod("setTextColor", arg),
                    Hooker { chain: Chain? ->
                        val result = chain!!.proceed()
                        if (appearance != null) appearance!!.style(chain!!.getThisObject() as View?)
                        result
                    },
                )
            }
            // A list adapter can replace backgrounds or glyphs after layout without changing text.
            // Only attached povo views are revisited; partially constructed views stay untouched.
            intercept(
                View::class.java.compatibleDeclaredMethod("onAttachedToWindow"),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    val view: View? = chain!!.getThisObject() as View?
                    if (appearance != null && appearance!!.nativeViews.profileContent(view))
                        appearance!!.style(view)
                    result
                },
            )
            intercept(
                View::class
                    .java
                    .compatibleDeclaredMethod("setBackgroundColor", Int::class.javaPrimitiveType),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    restyleAttached(chain!!.getThisObject() as View)
                    result
                },
            )
            intercept(
                View::class
                    .java
                    .compatibleDeclaredMethod("setBackgroundDrawable", Drawable::class.java),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    restyleAttached(chain!!.getThisObject() as View)
                    result
                },
            )
            intercept(
                View::class
                    .java
                    .compatibleDeclaredMethod(
                        "setBackgroundTintList",
                        ColorStateList::class.java,
                    ),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    restyleAttached(chain!!.getThisObject() as View)
                    result
                },
            )
            intercept(
                ImageView::class
                    .java
                    .compatibleDeclaredMethod("setImageDrawable", Drawable::class.java),
                Hooker { chain: Chain? ->
                    val result = chain!!.proceed()
                    restyleAttached(chain!!.getThisObject() as View)
                    result
                },
            )
            return true
        } catch (unsupported: ReflectiveOperationException) {
            removeHooks()
            log(
                6,
                "DarkAppTools",
                "Platform contract unavailable: " + unsupported.javaClass.getSimpleName(),
            )
            return false
        }
    }

    private fun restyleAttached(view: View) {
        if (app == SupportedApp.POVO && appearance != null && view.isAttachedToWindow())
            appearance!!.style(view)
    }

    /*
     * Vector permits hidden-API access in the scoped host; this is never called by the settings app.
     */
    @SuppressLint("SoonBlockedPrivateApi")
    private fun installDrawableHook() {
        try {
            val implementation = DexSymbols.findClass("android.content.res.ResourcesImpl")
            intercept(
                implementation.compatibleDeclaredMethod(
                    "loadDrawable",
                    Resources::class.java,
                    TypedValue::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Theme::class.java,
                ),
                Hooker { chain: Chain? ->
                    var result: Drawable? = chain!!.proceed() as Drawable?
                    if (appearance == null || !appearance!!.dark()) return@Hooker result
                    val resources: Resources? = chain!!.getArg(0) as Resources?
                    val id: Int = chain!!.getArg(2) as Int
                    result = appearance!!.artwork.map(resources!!, id, result)
                    appearance!!.icons.map(resources!!, id, result)
                },
            )
        } catch (unavailable: ReflectiveOperationException) {
            log(5, "DarkAppTools", "Using native view fallback for drawable colors")
        }
    }

    private fun initialize(context: Context) {
        firstUnlock.run(context) {
            DexSymbols.whenPrepared(context) { initializePrepared(context) }
        }
    }

    private fun initializePrepared(context: Context) {
        if (retired || appearance != null || app!!.packageName != context.getPackageName()) return
        try {
            appearance = HostAppearance(context, app!!)
            if (app == SupportedApp.POVO && appearance!!.dark())
                installListBinding(context.getClassLoader())
        } catch (unavailable: Exception) {
            val request =
                Intent()
                    .setComponent(
                        ComponentName(
                            BuildConfig.APPLICATION_ID,
                            ConfigurationAccessReceiver::class.java.compatibleName(),
                        ),
                    )
            context.sendBroadcast(
                request,
                null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),
            )
            log(
                5,
                "DarkAppTools",
                "Configuration access requested: " + unavailable.javaClass.getSimpleName(),
            )
        }
    }

    private fun installListBinding(loader: ClassLoader?) {
        if (listBindingInstalled) return
        try {
            val adapter =
                DexSymbols.findClass(
                    "androidx.recyclerview.widget.RecyclerView\$Adapter",
                    false,
                    loader,
                )
            for (method in adapter.getDeclaredMethods()) {
                val parameters = method.getParameterTypes()
                if (
                    (method.compatibleName() != "bindViewHolder") ||
                        parameters.size != 2 ||
                        parameters[1] != Int::class.javaPrimitiveType
                )
                    continue
                val itemView = parameters[0].compatibleField("itemView")
                if (itemView.getType() != View::class.java) continue
                // Run after the complete adapter binding, including offscreen prefetch/recycled
                // rows.
                // The reviewed host retains this public final method; its holder type is minified.
                intercept(
                    method,
                    Hooker { chain: Chain? ->
                        val result = chain!!.proceed()
                        if (appearance != null)
                            appearance!!.style(itemView.get(chain!!.getArg(0)) as View?)
                        result
                    },
                )
                listBindingInstalled = true
                return
            }
            log(
                5,
                "DarkAppTools",
                "List binding contract unavailable; using attached-view fallback",
            )
        } catch (unavailable: ReflectiveOperationException) {
            log(
                5,
                "DarkAppTools",
                "List binding contract unavailable; using attached-view fallback",
            )
        }
    }

    private fun intercept(method: Method?, hooker: Hooker?) {
        handles += hook(method!!).setId("dark-app-tools").setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain -> if (retired) chain.proceed() else hooker!!.intercept(chain) }
    }

    private fun removeHooks() {
        handles.asReversed().forEach { it.unhook() }
        handles.clear()
    }
}
