package de.emil.shizubox.ui.apps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.apps.ActivityEntry
import de.emil.shizubox.apps.AppTech
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard
import java.util.Locale

/** Used when an app declares no localeConfig. */
private val COMMON_LANGUAGES = listOf("en", "de", "tr", "es", "fr", "it", "pt", "nl", "pl", "ru", "uk", "ar", "ja", "ko", "zh")

private fun nativeName(tag: String): String {
    val l = Locale.forLanguageTag(tag)
    return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLanguageCard(tech: AppTech, shizukuReady: Boolean, onSet: (String) -> Unit) {
    var all by remember { mutableStateOf(false) }
    val declared = tech.supportedLocales.isNotEmpty()
    val options = (if (declared) tech.supportedLocales else COMMON_LANGUAGES).distinct()
        .sortedBy { nativeName(it).lowercase() }
    val shown = if (all || options.size <= 12) options else options.take(12).let { list ->
        // Keep the current language visible even when collapsed.
        if (tech.locale.isNullOrEmpty() || list.any { it.equals(tech.locale, true) }) list else list + tech.locale
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.app_lang), style = MaterialTheme.typography.titleMedium)
        Text(
            if (declared) t(R.string.app_lang_sub, options.size) else t(R.string.app_lang_undeclared),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(
                selected = tech.locale.isNullOrEmpty(),
                enabled = shizukuReady,
                onClick = { if (!tech.locale.isNullOrEmpty()) onSet("") },
                label = { Text(t(R.string.lang_system)) },
            )
            shown.forEach { tag ->
                val selected = tech.locale.equals(tag, true)
                FilterChip(
                    selected = selected,
                    enabled = shizukuReady,
                    onClick = { if (!selected) onSet(tag) },
                    label = { Text(nativeName(tag)) },
                )
            }
        }
        if (options.size > 12) {
            TextButton(onClick = { all = !all }) { Text(if (all) t(R.string.less) else t(R.string.all_n, options.size)) }
        }
    }
}

@Composable
fun ActivitiesCard(pkg: String, appLabel: String, activities: List<ActivityEntry>) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val exported = activities.count { it.exported }
    GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }) {
        Text(t(R.string.act_title, activities.size), style = MaterialTheme.typography.titleMedium)
        Text(
            t(R.string.act_sub, exported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!open) return@GlassCard
        activities.forEach { a ->
            val short = a.name.removePrefix(pkg).ifEmpty { a.name }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    a.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(
                        short,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (a.exported) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (a.exported) {
                    val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, a.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    IconButton(onClick = {
                        runCatching { context.startActivity(intent) }
                            .onFailure { Toast.makeText(context, it.message ?: t(R.string.error), Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Rounded.PlayArrow, t(R.string.open)) }
                    IconButton(onClick = {
                        val sm = context.getSystemService(ShortcutManager::class.java)
                        val icon = Apps.icon(pkg)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
                        val info = ShortcutInfo.Builder(context, "act:$pkg/${a.name}")
                            .setShortLabel(a.label ?: appLabel)
                            .setLongLabel("${a.label ?: appLabel} · ${short.substringAfterLast('.')}")
                            .setIcon(icon)
                            .setIntent(intent)
                            .build()
                        if (sm.isRequestPinShortcutSupported) sm.requestPinShortcut(info, null)
                    }) { Icon(Icons.Rounded.AddToHomeScreen, t(R.string.act_shortcut)) }
                }
            }
        }
    }
}

@Composable
fun TechCard(tech: AppTech) {
    val context = LocalContext.current
    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("info", text))
        Toast.makeText(context, t(R.string.copied), Toast.LENGTH_SHORT).show()
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(t(R.string.tech_title), style = MaterialTheme.typography.titleMedium)
        listOfNotNull(
            t(R.string.tech_version_code) to tech.versionCode.toString(),
            "Target SDK" to tech.targetSdk.toString(),
            "Min SDK" to tech.minSdk.toString(),
            "UID" to tech.uid.toString(),
            tech.installerLabel?.let { t(R.string.tech_installer) to it },
            "APK" to tech.apkPath,
            tech.dataDir?.let { t(R.string.tech_data) to it },
            tech.signatureSha256?.let { t(R.string.tech_signature) to it },
        ).forEach { (k, v) ->
            Column(Modifier.padding(top = 6.dp).clickable { copy(v) }) {
                Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
        Text(t(R.string.tech_copy_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
    }
}
