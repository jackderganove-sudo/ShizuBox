package de.emil.shizubox.automation

import android.content.Context
import android.content.SharedPreferences
import de.emil.shizubox.R
import de.emil.shizubox.customization.RefreshMode
import de.emil.shizubox.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TriggerKind(val labelRes: Int, /** Has a start and an end, so it can be reverted. */ val isState: Boolean) {
    AppOpen(R.string.au_t_app_open, true),
    AppClose(R.string.au_t_app_close, false),
    Time(R.string.au_t_time, false),
    Headphones(R.string.au_t_headphones, true),
    Bluetooth(R.string.au_t_bluetooth, true),
    Charger(R.string.au_t_charger, true),
    Battery(R.string.au_t_battery, true),
    Wifi(R.string.au_t_wifi, true),
    ;
    val label get() = t(labelRes)
}

data class Trigger(
    val kind: TriggerKind = TriggerKind.AppOpen,
    val pkg: String = "",
    /** Time: minutes after midnight. */
    val minute: Int = 8 * 60,
    /** Time: bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = 0b1111111,
    /** Headphones/Bluetooth/Charger/Wi-Fi: connected (true) or disconnected (false). */
    val connected: Boolean = true,
    /** Bluetooth device or Wi-Fi network; empty = any. */
    val name: String = "",
    /** Battery: below this level. */
    val percent: Int = 15,
)

enum class ActionKind(val labelRes: Int, val revertible: Boolean = true) {
    Gaming(R.string.gaming),
    Volume(R.string.au_a_volume),
    AppVolume(R.string.av_title),
    Boost(R.string.vb_title),
    Refresh(R.string.cust_refresh),
    Animations(R.string.tile_animations),
    Grayscale(R.string.au_a_grayscale),
    Dnd(R.string.au_a_dnd),
    StayAwake(R.string.tile_stay_awake),
    NetBlock(R.string.au_a_netblock),
    Clean(R.string.qa_clean, false),
    StartApp(R.string.au_a_start_app, false),
    StartActivity(R.string.au_a_start_activity, false),
    Shell(R.string.au_a_shell, false),
    ;
    val label get() = t(labelRes)
}

data class Action(
    val kind: ActionKind,
    val on: Boolean = true,
    /** Volume: percent · Refresh: RefreshMode ordinal · Animations: scale × 100. */
    val value: Int = 0,
    val pkg: String = "",
    /** StartActivity: "pkg/activity" · Shell: the command. */
    val text: String = "",
)

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger(),
    val actions: List<Action> = emptyList(),
    /** Undo the actions when the trigger ends (only state triggers). */
    val revert: Boolean = true,
) {
    val canRevert get() = trigger.kind.isState && actions.any { it.kind.revertible }
}

/** Stored rules (device-protected storage, part of the backup). */
object Rules {
    private lateinit var prefs: SharedPreferences
    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("automations", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        _rules.value = runCatching { decode(prefs.getString("rules", "[]")!!) }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Rule>) {
        _rules.value = list
        prefs.edit().putString("rules", encode(list)).apply()
    }

    fun save(rule: Rule) = persist(_rules.value.let { l -> if (l.any { it.id == rule.id }) l.map { if (it.id == rule.id) rule else it } else l + rule })
    fun delete(id: String) = persist(_rules.value.filterNot { it.id == id })
    fun setEnabled(id: String, on: Boolean) = persist(_rules.value.map { if (it.id == id) it.copy(enabled = on) else it })

    // ------------------------------------------------------------------ JSON

    fun encode(list: List<Rule>): String = JSONArray(list.map { r ->
        JSONObject().put("id", r.id).put("name", r.name).put("enabled", r.enabled).put("revert", r.revert)
            .put("trigger", r.trigger.let { tr ->
                JSONObject().put("kind", tr.kind.name).put("pkg", tr.pkg).put("minute", tr.minute).put("days", tr.days)
                    .put("connected", tr.connected).put("name", tr.name).put("percent", tr.percent)
            })
            .put("actions", JSONArray(r.actions.map(::encodeAction)))
    }).toString()

    fun encodeAction(a: Action): JSONObject =
        JSONObject().put("kind", a.kind.name).put("on", a.on).put("value", a.value).put("pkg", a.pkg).put("text", a.text)

    fun decodeAction(o: JSONObject) = Action(
        kind = ActionKind.valueOf(o.getString("kind")), on = o.optBoolean("on", true), value = o.optInt("value"),
        pkg = o.optString("pkg"), text = o.optString("text"),
    )

    fun decode(json: String): List<Rule> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val tr = o.getJSONObject("trigger")
                val acts = o.getJSONArray("actions")
                Rule(
                    id = o.getString("id"), name = o.optString("name"), enabled = o.optBoolean("enabled", true), revert = o.optBoolean("revert", true),
                    trigger = Trigger(
                        kind = TriggerKind.valueOf(tr.getString("kind")), pkg = tr.optString("pkg"), minute = tr.optInt("minute", 480),
                        days = tr.optInt("days", 0b1111111), connected = tr.optBoolean("connected", true), name = tr.optString("name"),
                        percent = tr.optInt("percent", 15),
                    ),
                    actions = (0 until acts.length()).map { decodeAction(acts.getJSONObject(it)) },
                )
            }.getOrNull()
        }
    }

    // ------------------------------------------------------------------ templates

    fun templates(): List<Rule> = listOf(
        Rule(name = t(R.string.au_tpl_brawl), trigger = Trigger(TriggerKind.AppOpen, pkg = "com.supercell.brawlstars"), actions = listOf(Action(ActionKind.Gaming, on = true))),
        Rule(name = t(R.string.au_tpl_headphones), trigger = Trigger(TriggerKind.Headphones), actions = listOf(Action(ActionKind.Boost, on = false))),
        Rule(name = t(R.string.au_tpl_charging), trigger = Trigger(TriggerKind.Charger), actions = listOf(Action(ActionKind.StayAwake, on = true))),
        Rule(
            name = t(R.string.au_tpl_battery), trigger = Trigger(TriggerKind.Battery, percent = 15),
            actions = listOf(Action(ActionKind.Refresh, value = RefreshMode.Fixed60.ordinal), Action(ActionKind.Animations, value = 0)),
        ),
        Rule(
            name = t(R.string.au_tpl_night), trigger = Trigger(TriggerKind.Time, minute = 22 * 60 + 30), revert = false,
            actions = listOf(Action(ActionKind.Dnd, on = true), Action(ActionKind.Grayscale, on = true)),
        ),
        Rule(
            name = t(R.string.au_tpl_morning), trigger = Trigger(TriggerKind.Time, minute = 7 * 60), revert = false,
            actions = listOf(Action(ActionKind.Dnd, on = false), Action(ActionKind.Grayscale, on = false)),
        ),
    )
}
