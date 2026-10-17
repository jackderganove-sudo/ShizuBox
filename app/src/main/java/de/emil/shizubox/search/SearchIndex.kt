package de.emil.shizubox.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import de.emil.shizubox.R
import de.emil.shizubox.nav.NavTarget
import de.emil.shizubox.tiles.QuickAction
import de.emil.shizubox.ui.home.Section

/**
 * One findable thing. [titleRes] is shown; [keysRes] holds comma-separated keywords and synonyms
 * (one string resource, translated into all 5 languages – the search matches every language).
 */
data class SearchEntry(
    val id: String,
    val titleRes: Int,
    val keysRes: Int,
    val icon: ImageVector,
    val target: NavTarget,
    /** Can be switched right in the result. */
    val quick: QuickAction? = null,
    /** Only on some devices (HiLight). */
    val available: () -> Boolean = { true },
)

/**
 * The search index – **every new feature, page, card or setting gets an entry here** (rule in
 * CLAUDE.md). Keywords: `sk_<id>` via tools/i18n (comma-separated, all 5 languages). Highlight keys
 * must match a `Modifier.highlight("…")` on the target card.
 */
object SearchIndex {
    private fun sec(s: Section, page: String? = null, hl: String? = null) = NavTarget(s, page, highlight = hl)
    private fun settings(hl: String) = NavTarget(settings = true, highlight = hl)
    private val sys = Section.System
    private val cl = Section.Cleaner

    val entries: List<SearchEntry> by lazy {
        listOf(
            // Sections
            SearchEntry("school", R.string.sec_school, R.string.sk_school, Icons.Rounded.School, sec(Section.School)),
            SearchEntry("custom", R.string.sec_custom, R.string.sk_custom, Icons.Rounded.Palette, sec(Section.Customization)),
            SearchEntry("auto", R.string.sec_auto, R.string.sk_auto, Icons.Rounded.AutoMode, sec(Section.Automations)),
            SearchEntry("apps", R.string.sec_apps, R.string.sk_apps, Icons.Rounded.Apps, sec(Section.Apps, "tab:0")),
            SearchEntry("updates", R.string.sec_updates, R.string.sk_updates, Icons.Rounded.SystemUpdate, sec(Section.Updates)),
            SearchEntry("system", R.string.sec_system, R.string.sk_system, Icons.Rounded.Terminal, sec(sys)),
            SearchEntry("downloader", R.string.sec_downloader, R.string.sk_downloader, Icons.Rounded.Download, sec(Section.Downloader, "0")),
            SearchEntry("cleaner", R.string.sec_cleaner, R.string.sk_cleaner, Icons.Rounded.CleaningServices, sec(cl)),
            // School mode
            SearchEntry("school_schedule", R.string.sch_title, R.string.sk_school_schedule, Icons.Rounded.School, sec(Section.School, hl = "school_schedule")),
            SearchEntry("school_stats", R.string.ss_title, R.string.sk_school_stats, Icons.Rounded.School, sec(Section.School, hl = "school_stats")),
            // Customization
            SearchEntry("anim", R.string.cust_anim, R.string.sk_anim, Icons.Rounded.Palette, sec(Section.Customization, hl = "cust_anim"), QuickAction.Animations),
            SearchEntry("refresh", R.string.cust_refresh, R.string.sk_refresh, Icons.Rounded.Palette, sec(Section.Customization, hl = "cust_refresh"), QuickAction.Refresh),
            SearchEntry("font", R.string.cust_font, R.string.sk_font, Icons.Rounded.Palette, sec(Section.Customization, hl = "cust_font")),
            SearchEntry("dpi", R.string.cust_dpi, R.string.sk_dpi, Icons.Rounded.Palette, sec(Section.Customization, hl = "cust_dpi")),
            SearchEntry("resolution", R.string.cust_resolution, R.string.sk_resolution, Icons.Rounded.Palette, sec(Section.Customization, hl = "cust_resolution")),
            SearchEntry("statusbar", R.string.cust_statusbar, R.string.sk_statusbar, Icons.Rounded.Palette, sec(Section.Customization, hl = "cust_statusbar")),
            // Apps
            SearchEntry("profiles", R.string.profiles, R.string.sk_profiles, Icons.Rounded.Apps, sec(Section.Apps, "tab:1")),
            SearchEntry("permissions", R.string.permissions, R.string.sk_permissions, Icons.Rounded.Security, sec(Section.Apps, "tab:2")),
            SearchEntry("debloat", R.string.db_title, R.string.sk_debloat, Icons.Rounded.Apps, sec(Section.Apps, de.emil.shizubox.ui.apps.DEBLOAT)),
            SearchEntry("launcher", R.string.al_title, R.string.sk_launcher, Icons.Rounded.RocketLaunch, sec(Section.Apps, de.emil.shizubox.ui.apps.LAUNCHER)),
            SearchEntry("netblock", R.string.sx_netblock, R.string.sk_netblock, Icons.Rounded.Wifi, sec(Section.Apps, "tab:0")),
            // Updates
            SearchEntry("self_update", R.string.set_self_update, R.string.sk_self_update, Icons.Rounded.SystemUpdate, settings("self_update")),
            SearchEntry("virustotal", R.string.vt_key_title, R.string.sk_virustotal, Icons.Rounded.Security, settings("vt")),
            // System pages
            SearchEntry("device", R.string.di_title, R.string.sk_device, Icons.Rounded.PhoneAndroid, sec(sys, "device")),
            SearchEntry("processes", R.string.pr_title, R.string.sk_processes, Icons.Rounded.Memory, sec(sys, "processes")),
            SearchEntry("terminal", R.string.term_title, R.string.sk_terminal, Icons.Rounded.Terminal, sec(sys, "terminal")),
            SearchEntry("logcat", R.string.sx_logcat, R.string.sk_logcat, Icons.AutoMirrored.Rounded.Notes, sec(sys, "logcat")),
            SearchEntry("screentime", R.string.st_title, R.string.sk_screentime, Icons.Rounded.HourglassBottom, sec(sys, "screentime")),
            SearchEntry("privacy", R.string.pv_title, R.string.sk_privacy, Icons.Rounded.PrivacyTip, sec(sys, "privacy")),
            SearchEntry("battery", R.string.br_title, R.string.sk_battery, Icons.Rounded.BatteryChargingFull, sec(sys, "battery")),
            SearchEntry("clipboard", R.string.cb_title, R.string.sk_clipboard, Icons.Rounded.ContentPaste, sec(sys, "clipboard")),
            SearchEntry("appvolume", R.string.av_title, R.string.sk_appvolume, Icons.Rounded.GraphicEq, sec(sys, "appvolume")),
            SearchEntry("hilight", R.string.sx_hilight, R.string.sk_hilight, Icons.Rounded.Lightbulb, sec(sys, "hilight"), available = { de.emil.shizubox.hilight.HiLight.supported }),
            SearchEntry("notifhistory", R.string.nh_title, R.string.sk_notifhistory, Icons.Rounded.Notifications, sec(sys, "notifhistory")),
            // System cards
            SearchEntry("gaming", R.string.gaming, R.string.sk_gaming, Icons.Rounded.SportsEsports, sec(sys, hl = "gaming"), QuickAction.Gaming),
            SearchEntry("boost", R.string.vb_title, R.string.sk_boost, Icons.Rounded.VolumeUp, sec(sys, hl = "boost"), QuickAction.Boost),
            SearchEntry("tweaks", R.string.tw_title, R.string.sk_tweaks, Icons.Rounded.ToggleOn, sec(sys, hl = "tweaks")),
            SearchEntry("stayawake", R.string.tile_stay_awake, R.string.sk_stayawake, Icons.Rounded.ToggleOn, sec(sys, hl = "tweaks"), QuickAction.StayAwake),
            SearchEntry("installer", R.string.sx_installer, R.string.sk_installer, Icons.Rounded.InstallMobile, sec(sys, hl = "installer")),
            // Cleaner
            SearchEntry("analysis", R.string.cl_analysis, R.string.sk_analysis, Icons.Rounded.PieChart, sec(cl, "analysis")),
            SearchEntry("syscleaner", R.string.cl_system, R.string.sk_syscleaner, Icons.Rounded.CleaningServices, sec(cl, "system")),
            SearchEntry("corpses", R.string.cl_corpses, R.string.sk_corpses, Icons.Rounded.FolderDelete, sec(cl, "corpses")),
            SearchEntry("appcache", R.string.cl_appcache, R.string.sk_appcache, Icons.Rounded.CleaningServices, sec(cl, "appcache"), QuickAction.Cache),
            SearchEntry("dupes", R.string.cl_dupes, R.string.sk_dupes, Icons.Rounded.ContentCopy, sec(cl, "dupes")),
            SearchEntry("trash", R.string.cl_trash, R.string.sk_trash, Icons.Rounded.Delete, sec(cl, "trash")),
            SearchEntry("clean", R.string.qa_clean, R.string.sk_clean, Icons.Rounded.CleaningServices, sec(cl), QuickAction.Clean),
            SearchEntry("cleaner_settings", R.string.cl_settings, R.string.sk_cleaner_settings, Icons.Rounded.Settings, sec(cl, "settings")),
            // Downloader
            SearchEntry("dl_downloads", R.string.dl_nav_downloads, R.string.sk_dl_downloads, Icons.Rounded.Download, sec(Section.Downloader, "1")),
            SearchEntry("dl_queue", R.string.dl_nav_queue, R.string.sk_dl_queue, Icons.Rounded.Download, sec(Section.Downloader, "2")),
            SearchEntry("dl_more", R.string.dl_nav_more, R.string.sk_dl_more, Icons.Rounded.Download, sec(Section.Downloader, "3")),
            // Settings
            SearchEntry("language", R.string.set_language, R.string.sk_language, Icons.Rounded.Language, settings("language")),
            SearchEntry("design", R.string.set_design, R.string.sk_design, Icons.Rounded.Palette, settings("design")),
            SearchEntry("glass", R.string.set_glass, R.string.sk_glass, Icons.Rounded.Palette, settings("glass")),
            SearchEntry("home", R.string.home_title, R.string.sk_home, Icons.Rounded.Settings, settings("home")),
            SearchEntry("backup", R.string.bk_title, R.string.sk_backup, Icons.Rounded.Backup, settings("backup")),
            SearchEntry("errorlog", R.string.el_title, R.string.sk_errorlog, Icons.Rounded.BugReport, settings("errorlog")),
            SearchEntry("installsettings", R.string.set_installer_security, R.string.sk_installsettings, Icons.Rounded.Security, settings("installer")),
        )
    }

    fun byId(id: String) = entries.firstOrNull { it.id == id }
}
