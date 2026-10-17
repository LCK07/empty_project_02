package com.lck07.lumen.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.MovieEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class LanguageFilterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: LumenDatabase? = null

    @After fun close() { db?.close() }

    private fun movie(id: Int, language: String?) =
        MovieEntity(id, "m$id", "m$id", null, null, "c", null, "mp4", id, 1, language)

    private suspend fun shown(profiles: ProfileRepository, dao: com.lck07.lumen.data.db.CatalogDao): List<Int> {
        val f = profiles.languageFilter.first()
        return dao.recentMovies(100, f.all, f.langs).first().map { it.id }.sorted()
    }

    @Test fun filterHonoursActiveProfileLanguages() = runTest(UnconfinedTestDispatcher()) {
        val database = Room.inMemoryDatabaseBuilder(context, LumenDatabase::class.java)
            .addCallback(LumenDatabase.SEED_PROFILE).allowMainThreadQueries().build().also { db = it }
        val profiles = ProfileRepository(database, SettingsStore(context))
        val dao = database.catalog()
        dao.upsertMovies(listOf(movie(1, "de"), movie(2, "en"), movie(3, "fr"), movie(4, null)))

        val first = profiles.profiles.first().single().id
        val other = profiles.create("Other", 1)

        // Seeded and new profiles default to "de,en": German, English and untagged.
        profiles.select(first)
        assertEquals(listOf(1, 2, 4), shown(profiles, dao))
        assertEquals(listOf(1, 2, 4), dao.searchMovies("m", 100, false, listOf("de", "en")).map { it.id }.sorted())

        // Another profile with "all languages" sees everything.
        profiles.setContentLanguages(other, emptyList())
        profiles.select(other)
        assertEquals(listOf(1, 2, 3, 4), shown(profiles, dao))

        // German only, then back to the first profile.
        profiles.setContentLanguages(other, listOf("de"))
        assertEquals(listOf(1, 4), shown(profiles, dao))
        profiles.select(first)
        assertEquals(listOf(1, 2, 4), shown(profiles, dao))
    }
}
