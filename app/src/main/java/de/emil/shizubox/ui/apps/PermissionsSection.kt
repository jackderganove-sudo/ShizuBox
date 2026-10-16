@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package de.emil.shizubox.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emil.shizubox.R
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.PermGroup
import de.emil.shizubox.apps.PermHolder
import de.emil.shizubox.t
import de.emil.shizubox.ui.glass.GlassCard

/** State of the permission overview tab, kept by the caller. */
data class PermTabState(
    val data: Map<PermGroup, List<PermHolder>>? = null,
    val expanded: PermGroup? = null,
    val includeSystem: Boolean = false,
)

fun LazyListScope.permissionsSection(
    state: PermTabState,
    shizukuReady: Boolean,
    onState: (PermTabState) -> Unit,
    onRevoke: (PermGroup, PermHolder) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(t(R.string.pg_title), style = MaterialTheme.typography.titleMedium)
            Text(t(R.string.pg_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = state.includeSystem,
                    onClick = { onState(state.copy(includeSystem = !state.includeSystem)) },
                    label = { Text(t(R.string.pg_include_system)) },
                )
            }
        }
    }
    val data = state.data
    if (data == null) {
        item { Text(t(R.string.loading), modifier = Modifier.padding(8.dp)) }
        return
    }
    PermGroup.entries.forEach { g ->
        val holders = data[g].orEmpty().filter { state.includeSystem || !it.isSystem }
        val open = state.expanded == g
        item(key = "pg_${g.name}") {
            GlassCard(Modifier.fillMaxWidth(), onClick = { onState(state.copy(expanded = if (open) null else g)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (g.appOp != null && !shizukuReady) t(R.string.shizuku_must) else tpApps(holders.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (open) {
                    holders.forEach { h -> HolderRow(g, h, shizukuReady, onRevoke, onOpenApp) }
                }
            }
        }
    }
}

@Composable
private fun HolderRow(g: PermGroup, h: PermHolder, shizukuReady: Boolean, onRevoke: (PermGroup, PermHolder) -> Unit, onOpenApp: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onOpenApp(h.pkg) },
    ) {
        AppIcon(h.pkg, 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.appOp == null) {
                Text(
                    h.granted.joinToString(", ") { it.substringAfterLast('.').lowercase().replace('_', ' ') },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { onRevoke(g, h) }, enabled = shizukuReady) { Text(t(R.string.pg_revoke)) }
    }
}

private fun tpApps(n: Int) = if (n == 0) t(R.string.pg_none) else de.emil.shizubox.tp(R.plurals.pg_n_apps, n, n)
