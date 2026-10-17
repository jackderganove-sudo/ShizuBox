/*
 * Camera/microphone activity for the HiLight privacy hint, running in ShizuBox' shell service.
 * Ported from HiLight Studio's AppOpsWatcher.java
 * (MIT License, Copyright (c) 2026 HiLight Studio contributors, see NOTICE).
 */
package de.emil.shizubox.hilight

import android.app.AppOpsManager
import android.content.Context
import android.os.IBinder
import de.emil.shizubox.IHiLightCallback
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * AppOpsManager.startWatchingActive needs WATCH_APPOPS, which the shell user holds. There is no
 * Context in this plain process, so the manager is built around the IAppOpsService binder by
 * reflection (as HiLight Studio does). Callbacks only say "something changed"; the current set of
 * running camera/microphone ops is then read with getPackagesForOps and sent to the app.
 */
object PrivacyWatcher {
    private val OPS = arrayOf(AppOpsManager.OPSTR_CAMERA, AppOpsManager.OPSTR_RECORD_AUDIO)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "hilight-appops").apply { isDaemon = true } }
    private var manager: AppOpsManager? = null
    private var listener: AppOpsManager.OnOpActiveChangedListener? = null
    @Volatile private var callback: IHiLightCallback? = null

    @Synchronized
    fun watch(cb: IHiLightCallback) {
        callback = cb
        if (manager != null) { worker.execute(::refresh); return }
        val binder = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String::class.java)
            .invoke(null, Context.APP_OPS_SERVICE) as IBinder
        val service = Class.forName("com.android.internal.app.IAppOpsService\$Stub").getDeclaredMethod("asInterface", IBinder::class.java)
            .apply { isAccessible = true }.invoke(null, binder)!!
        val ctor = AppOpsManager::class.java.declaredConstructors.first { c ->
            c.parameterTypes.size == 2 && c.parameterTypes[0] == Context::class.java && c.parameterTypes[1].isInstance(service)
        }.apply { isAccessible = true }
        val m = ctor.newInstance(null, service) as AppOpsManager
        val l = AppOpsManager.OnOpActiveChangedListener { _, _, _, _ -> worker.execute(::refresh) }
        m.startWatchingActive(OPS, worker, l)
        manager = m
        listener = l
        worker.execute(::refresh)
    }

    @Synchronized
    fun unwatch() {
        callback = null
        val m = manager ?: return
        runCatching { listener?.let { m.stopWatchingActive(it) } }
        manager = null
        listener = null
    }

    private fun refresh() {
        val m = manager ?: return
        val out = JSONArray()
        runCatching {
            val raw = AppOpsManager::class.java.getDeclaredMethod("getPackagesForOps", Array<String>::class.java)
                .apply { isAccessible = true }.invoke(m, OPS as Any) as List<*>?
            raw.orEmpty().forEach { po ->
                val t = po!!.javaClass
                val pkg = t.getMethod("getPackageName").invoke(po) as String
                val uid = t.getMethod("getUid").invoke(po) as Int
                (t.getMethod("getOps").invoke(po) as List<*>).forEach { e ->
                    val et = e!!.javaClass
                    if (et.getMethod("isRunning").invoke(e) as Boolean) {
                        val op = when (et.getMethod("getOpStr").invoke(e) as String) {
                            AppOpsManager.OPSTR_CAMERA -> "camera"
                            AppOpsManager.OPSTR_RECORD_AUDIO -> "mic"
                            else -> null
                        }
                        if (op != null) out.put(JSONObject().put("op", op).put("pkg", pkg).put("uid", uid))
                    }
                }
            }
        }
        runCatching { callback?.onPrivacy(out.toString()) }.onFailure { callback = null }
    }
}
