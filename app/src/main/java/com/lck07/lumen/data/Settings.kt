package com.lck07.lumen.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

enum class AppLanguage(val tag: String?) { System(null), English("en"), German("de") }
enum class BufferSize(val minMs: Int, val maxMs: Int) { Small(8_000, 20_000), Medium(15_000, 50_000), Large(30_000, 120_000) }
enum class LiveFormat(val extension: String) { Ts("ts"), Hls("m3u8") }

/** Everything the owner can change in Settings, plus a little app state. */
data class Settings(
    val language: AppLanguage = AppLanguage.System,
    /** Hour of day (0-23) for the daily guide refresh. */
    val guideRefreshHour: Int = 4,
    /** SHA-256 of the parental PIN, or null when the lock is off. */
    val pinHash: String? = null,
    val lockedCategories: Set<String> = emptySet(),
    /** ISO 639 language for audio, or null for the stream's default. */
    val audioLanguage: String? = null,
    /** ISO 639 language for subtitles, or null for off. */
    val subtitleLanguage: String? = null,
    val buffer: BufferSize = BufferSize.Medium,
    val liveFormat: LiveFormat = LiveFormat.Ts,
    val lastChannelId: Int? = null,
    val lastTab: String? = null,
    val lastScreen: String? = null,
    val lastScreenAt: Long = 0,
    val catalogImportedAt: Long = 0,
) {
    val parentalLockOn: Boolean get() = pinHash != null
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val language = stringPreferencesKey("language")
        val guideHour = intPreferencesKey("guide_hour")
        val pinHash = stringPreferencesKey("pin_hash")
        val locked = stringSetPreferencesKey("locked_categories")
        val audio = stringPreferencesKey("audio_language")
        val subtitle = stringPreferencesKey("subtitle_language")
        val buffer = stringPreferencesKey("buffer")
        val liveFormat = stringPreferencesKey("live_format")
        val lastChannel = intPreferencesKey("last_channel")
        val lastTab = stringPreferencesKey("last_tab")
        val lastScreen = stringPreferencesKey("last_screen")
        val lastScreenAt = longPreferencesKey("last_screen_at")
        val importedAt = longPreferencesKey("catalog_imported_at")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            language = p[K.language]?.let { v -> AppLanguage.entries.firstOrNull { it.name == v } } ?: AppLanguage.System,
            guideRefreshHour = p[K.guideHour] ?: 4,
            pinHash = p[K.pinHash],
            lockedCategories = p[K.locked].orEmpty(),
            audioLanguage = p[K.audio],
            subtitleLanguage = p[K.subtitle],
            buffer = p[K.buffer]?.let { v -> BufferSize.entries.firstOrNull { it.name == v } } ?: BufferSize.Medium,
            liveFormat = p[K.liveFormat]?.let { v -> LiveFormat.entries.firstOrNull { it.name == v } } ?: LiveFormat.Ts,
            lastChannelId = p[K.lastChannel],
            lastTab = p[K.lastTab],
            lastScreen = p[K.lastScreen],
            lastScreenAt = p[K.lastScreenAt] ?: 0,
            catalogImportedAt = p[K.importedAt] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setLanguage(l: AppLanguage) = context.store.edit { it[K.language] = l.name }
    suspend fun setGuideHour(h: Int) = context.store.edit { it[K.guideHour] = h.coerceIn(0, 23) }
    suspend fun setAudioLanguage(l: String?) = context.store.edit { if (l == null) it.remove(K.audio) else it[K.audio] = l }
    suspend fun setSubtitleLanguage(l: String?) = context.store.edit { if (l == null) it.remove(K.subtitle) else it[K.subtitle] = l }
    suspend fun setBuffer(b: BufferSize) = context.store.edit { it[K.buffer] = b.name }
    suspend fun setLiveFormat(f: LiveFormat) = context.store.edit { it[K.liveFormat] = f.name }
    suspend fun setLastChannel(id: Int) = context.store.edit { it[K.lastChannel] = id }
    suspend fun setCatalogImportedAt(t: Long) = context.store.edit { it[K.importedAt] = t }

    suspend fun setLastScreen(tab: String, screen: String?, at: Long) = context.store.edit {
        it[K.lastTab] = tab
        if (screen == null) it.remove(K.lastScreen) else it[K.lastScreen] = screen
        it[K.lastScreenAt] = at
    }

    /** When the app went to the background, so a relaunch soon after returns to the same screen. */
    suspend fun touchLastScreen(at: Long) = context.store.edit { it[K.lastScreenAt] = at }

    suspend fun setPin(pin: String?) = context.store.edit {
        if (pin == null) { it.remove(K.pinHash); it.remove(K.locked) } else it[K.pinHash] = hashPin(pin)
    }

    suspend fun setCategoryLocked(key: String, locked: Boolean) = context.store.edit {
        val now = it[K.locked].orEmpty()
        it[K.locked] = if (locked) now + key else now - key
    }

    suspend fun checkPin(pin: String): Boolean = current().pinHash == hashPin(pin)

    /** Clears account-specific state when signing out. */
    suspend fun resetForSignOut() = context.store.edit {
        it.remove(K.lastChannel); it.remove(K.lastScreen); it.remove(K.lastTab); it.remove(K.importedAt); it.remove(K.locked)
    }

    companion object {
        fun hashPin(pin: String): String =
            MessageDigest.getInstance("SHA-256").digest("lumen:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

        /** Locked categories are stored as "type:id". */
        fun categoryKey(type: String, id: String) = "$type:$id"
    }
}
