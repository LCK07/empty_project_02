package com.lck07.lumen.data

import androidx.room.withTransaction
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Profiles keep favourites, watchlist and progress apart. The active one is stored in DataStore. */
class ProfileRepository(private val db: LumenDatabase, private val settings: SettingsStore) {
    private val dao = db.profiles()

    val profiles: Flow<List<ProfileEntity>> = dao.profiles()

    /** The stored profile, or the lowest id when the stored one no longer exists. */
    val activeProfile: Flow<ProfileEntity?> =
        combine(dao.profiles(), settings.activeProfileId) { all, id -> all.firstOrNull { it.id == id } ?: all.firstOrNull() }
            .distinctUntilChanged()

    val activeId: Flow<Int> = activeProfile.map { it?.id ?: 1 }.distinctUntilChanged()

    /** Creates a profile and returns its id. New profiles see German and English titles. */
    suspend fun create(name: String, avatar: Int): Int =
        dao.insert(ProfileEntity(name = name.trim(), avatar = avatar, contentLanguages = "de,en", createdAt = System.currentTimeMillis())).toInt()

    suspend fun rename(id: Int, name: String) = dao.rename(id, name.trim())
    suspend fun setAvatar(id: Int, avatar: Int) = dao.setAvatar(id, avatar)

    /** Empty list = all languages. */
    suspend fun setContentLanguages(id: Int, languages: List<String>) = dao.setContentLanguages(id, languages.joinToString(","))

    suspend fun select(id: Int) = settings.setActiveProfileId(id)

    /** Deletes the profile and only its own rows. Returns false for the last profile. */
    suspend fun delete(id: Int): Boolean {
        val active = activeProfile.first()?.id
        val next = db.withTransaction {
            val others = dao.profilesNow().filter { it.id != id }
            if (others.size == dao.profilesNow().size || others.isEmpty()) return@withTransaction null
            dao.deleteFavourites(id)
            dao.deleteWatchlist(id)
            dao.deleteProgress(id)
            dao.deleteProfile(id)
            others.first().id
        } ?: return false
        if (active == id) select(next)
        return true
    }
}
