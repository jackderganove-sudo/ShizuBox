package de.emil.shizubox.school

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

data class SchoolState(
    /** Black screen + power detection are running. */
    val active: Boolean = false,
    /** Exit was requested but settings could not be restored yet (Shizuku missing). */
    val pendingRestore: Boolean = false,
    /** Settings/radio state from before activation, see [Snapshot]. */
    val snapshot: String? = null,
    /** Packages ShizuBox suspended (and must unsuspend again). */
    val suspended: Set<String> = emptySet(),
    /** Wall-clock time (ms) when the mode ends automatically, 0 = never. */
    val endAt: Long = 0,
    /** User preference: auto-end after this many minutes, 0 = off. */
    val autoEndMinutes: Int = 0,
    /**
     * User preference for a reboot during school mode: false (default) = black screen right
     * after boot; true = phone stays usable until Shizuku runs again, then the black screen returns.
     */
    val waitForShizukuAfterReboot: Boolean = false,
)

/**
 * Stored in device-protected storage, so it is readable right after a reboot,
 * before the user unlocks the phone for the first time.
 */
class SchoolStore(context: Context) {
    // Not preferencesDataStoreFile(): it resolves applicationContext.filesDir, which is the
    // credential-encrypted storage and stays locked until the first unlock after a reboot.
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        File(context.createDeviceProtectedStorageContext().filesDir, "datastore/school_mode.preferences_pb")
    }

    val state: Flow<SchoolState> = store.data.map { p ->
        SchoolState(
            active = p[ACTIVE] ?: false,
            pendingRestore = p[PENDING_RESTORE] ?: false,
            snapshot = p[SNAPSHOT],
            suspended = p[SUSPENDED] ?: emptySet(),
            endAt = p[END_AT] ?: 0,
            autoEndMinutes = p[AUTO_END_MINUTES] ?: 0,
            waitForShizukuAfterReboot = p[WAIT_FOR_SHIZUKU] ?: false,
        )
    }

    suspend fun current() = state.first()

    suspend fun saveSnapshot(snapshot: String, suspended: Set<String>) = store.edit {
        it[SNAPSHOT] = snapshot
        it[SUSPENDED] = suspended
    }

    suspend fun setActive(endAt: Long) = store.edit {
        it[ACTIVE] = true
        it[PENDING_RESTORE] = false
        it[END_AT] = endAt
    }

    /** Black screen off; restore still outstanding. */
    suspend fun setPendingRestore() = store.edit {
        it[ACTIVE] = false
        it[PENDING_RESTORE] = true
        it[END_AT] = 0
    }

    suspend fun clear() = store.edit {
        it[ACTIVE] = false
        it[PENDING_RESTORE] = false
        it[END_AT] = 0
        it.remove(SNAPSHOT)
        it.remove(SUSPENDED)
    }

    suspend fun setAutoEndMinutes(minutes: Int) = store.edit { it[AUTO_END_MINUTES] = minutes }

    suspend fun setWaitForShizukuAfterReboot(wait: Boolean) = store.edit { it[WAIT_FOR_SHIZUKU] = wait }

    private companion object {
        val ACTIVE = booleanPreferencesKey("active")
        val PENDING_RESTORE = booleanPreferencesKey("pending_restore")
        val SNAPSHOT = stringPreferencesKey("snapshot")
        val SUSPENDED = stringSetPreferencesKey("suspended")
        val END_AT = longPreferencesKey("end_at")
        val AUTO_END_MINUTES = intPreferencesKey("auto_end_minutes")
        val WAIT_FOR_SHIZUKU = booleanPreferencesKey("wait_for_shizuku_after_reboot")
    }
}
