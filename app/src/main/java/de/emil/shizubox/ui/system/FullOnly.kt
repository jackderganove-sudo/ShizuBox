package de.emil.shizubox.ui.system

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.emil.shizubox.BuildConfig
import de.emil.shizubox.R
import de.emil.shizubox.t

/**
 * The "lite" variant has no notification listener (see build.gradle.kts): everything that needs it
 * is hidden there and replaced by [FullOnlyNote].
 */
val hasNotificationListener get() = BuildConfig.NOTIFICATION_LISTENER

/** "Only in Full" in place of a feature that needs the notification listener. */
@Composable
fun FullOnlyNote(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Top, modifier = modifier.padding(top = 4.dp)) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
        Text(
            t(R.string.lite_full_only) + " – " + t(R.string.lite_full_only_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
