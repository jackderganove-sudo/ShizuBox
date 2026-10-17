/*
 * Based on HiLight Studio's LightsBackend.java and ForcedBlackClear.java
 * (https://github.com/DhananjayBhosale/hilight-studio).
 * MIT License – Copyright (c) 2026 HiLight Studio contributors. Full licence text: NOTICE.
 */
package de.emil.shizubox.hilight

import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.os.Binder
import android.os.IBinder

/**
 * Runs in ShizuBox' shell service (uid 2000, which holds CONTROL_DEVICE_LIGHTS): the hidden
 * ILightsManager binder, reached by reflection. Based on HiLight Studio's LightsBackend
 * (MIT License, Copyright (c) 2026 HiLight Studio contributors, see NOTICE).
 *
 * Tested on the Pixel 11 Pro (Android 17): `service list` has `lights: [ILightsManager]`,
 * `dumpsys lights` shows id 0 (display) and ids 1–8 (type APPLICATION = HiLight).
 */
class LightsIo {
    private val service: Any
    private val openSession: java.lang.reflect.Method
    private val closeSession: java.lang.reflect.Method
    private val setLightStates: java.lang.reflect.Method
    private val getLightState: java.lang.reflect.Method?
    val lights: List<Light>
    private var token: IBinder? = null

    init {
        val b = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "lights") as IBinder?
            ?: error("lights service missing")
        val iface = Class.forName("android.hardware.lights.ILightsManager")
        service = Class.forName("android.hardware.lights.ILightsManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, b)!!
        openSession = iface.getMethod("openSession", IBinder::class.java, Int::class.javaPrimitiveType)
        closeSession = iface.getMethod("closeSession", IBinder::class.java)
        setLightStates = iface.getMethod("setLightStates", IBinder::class.java, IntArray::class.java, Array<LightState>::class.java)
        getLightState = runCatching { iface.getMethod("getLightState", Int::class.javaPrimitiveType) }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        val all = iface.getMethod("getLights").invoke(service) as List<Light>
        lights = all.filter { it.type == Light.LIGHT_TYPE_APPLICATION }.sortedBy { it.ordinal }
    }

    val ids: IntArray get() = lights.map { it.id }.toIntArray()
    val isOpen get() = token != null
    val minPeriodMs: Long get() = lights.maxOfOrNull { runCatching { it.minUpdatePeriodMillis }.getOrDefault(33L) } ?: 33L

    fun open(priority: Int) {
        if (token != null) return
        val t = Binder()
        openSession.invoke(service, t, priority)
        token = t
    }

    fun close() {
        val t = token ?: return
        closeSession.invoke(service, t)
        token = null
    }

    /** One colour per LED (a shorter array repeats). */
    fun set(colors: IntArray) {
        val t = token ?: return
        val ids = ids
        val states = Array(ids.size) { i -> LightState.Builder().setColor(colors[i % colors.size]).build() }
        setLightStates.invoke(service, t, ids, states)
    }

    fun get(id: Int): Int? = (getLightState?.invoke(service, id) as LightState?)?.color

    /**
     * HiLight Studio's "alpha black then zero" clear: LightsService skips writes of an unchanged
     * ARGB value and the LEDs ignore alpha, so 0x01000000 forces a real (dark) write before the
     * canonical 0; each held for the update period so the driver does not merge it with the close.
     * Pixel 11 Pro units were seen keeping one LED lit after a plain close.
     */
    fun clearAndClose() {
        if (token == null) return
        val wait = minPeriodMs.coerceIn(1, 250)
        runCatching { set(intArrayOf(0x01000000)); Thread.sleep(wait) }
        runCatching { set(intArrayOf(0)); Thread.sleep(wait) }
        close()
    }

    fun describe(): String = lights.joinToString("\n") {
        "id=${it.id} ordinal=${it.ordinal} rgb=${it.hasRgbControl()} brightness=${it.hasBrightnessControl()} " +
            "animation=${runCatching { it.hasAnimationControl() }.getOrDefault(false)} period=${runCatching { it.minUpdatePeriodMillis }.getOrNull()}"
    }
}
