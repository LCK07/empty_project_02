package com.lck07.lumen.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.data.db.LumenDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProfileDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: LumenDatabase? = null

    @After fun close() { db?.close() }

    /** Builds a real v1 database from the exported schema, then opens it with the v2 migration. */
    @Test fun migrationMovesExistingRowsToProfileOne() = runTest {
        val v1 = JSONObject(File("schemas/com.lck07.lumen.data.db.LumenDatabase/1.json").readText()).getJSONObject("database")
        val path = context.getDatabasePath("migrate.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
            val entities = v1.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { idx ->
                    for (j in 0 until idx.length()) {
                        val sql = idx.getJSONObject(j).getString("createSql")
                        raw.execSQL(sql.replace("\${TABLE_NAME}", e.getString("tableName")))
                    }
                }
            }
            val setup = v1.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.execSQL("INSERT INTO favourite (kind, itemId, addedAt) VALUES ('series', '7', 100)")
            raw.execSQL("INSERT INTO progress (`key`, seriesId, movieId, episodeId, positionMs, durationMs, updatedAt) VALUES ('m:3', NULL, 3, NULL, 5000, 10000, 200)")
            raw.execSQL("INSERT INTO series (id, name, searchKey, position, gen) VALUES (1, 'x', 'x', 0, 1)")
            raw.version = 1
        }

        val migrated = Room.databaseBuilder(context, LumenDatabase::class.java, "migrate.db")
            .addMigrations(LumenDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build().also { db = it }

        val profiles = migrated.profiles().profilesNow()
        assertEquals(1, profiles.size)
        assertEquals(1, profiles[0].id)
        assertEquals("", profiles[0].name)
        assertEquals("de,en", profiles[0].contentLanguages)
        assertEquals(listOf("7"), migrated.library().favouriteIdsNow(1, "series"))
        assertEquals(emptyList<String>(), migrated.library().favouriteIdsNow(2, "series"))
        assertEquals(5000L, migrated.library().progress(1, "m:3")?.positionMs)
        assertEquals(1, migrated.library().progressNow(1).size)
        assertEquals(null, migrated.catalog().series(1)?.language)
    }

    private fun repos(): Triple<ProfileRepository, LibraryRepository, LumenDatabase> {
        val database = Room.inMemoryDatabaseBuilder(context, LumenDatabase::class.java)
            .addCallback(LumenDatabase.SEED_PROFILE).allowMainThreadQueries().build().also { db = it }
        val settings = SettingsStore(context)
        val profiles = ProfileRepository(database, settings)
        return Triple(profiles, LibraryRepository(database, CatalogRepository(database), profiles), database)
    }

    @Test fun profilesAreIsolatedAndSwitchingReEmits() = runTest(UnconfinedTestDispatcher()) {
        val (profiles, library, _) = repos()
        val a = profiles.create("A", 0)
        val b = profiles.create("B", 1)

        profiles.select(a)
        library.toggleFavourite(FavouriteKind.Movie, "1")
        library.toggleWatchlist(FavouriteKind.Series, "2")
        library.saveMovieProgress(1, 1000, 5000)
        assertEquals(listOf("1"), library.favourites(FavouriteKind.Movie).first())
        assertEquals(listOf(WatchProgress.movieKey(1)), library.progress.first().map { it.key })

        // Room re-emits on its own executor, so wait (in real time) for the switch to arrive.
        val seen = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { library.favourites(FavouriteKind.Movie).collect { seen.send(it) } }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                assertEquals(listOf("1"), seen.receive())
                profiles.select(b)
                assertEquals(emptyList<String>(), seen.receive())
            }
        }
        job.cancel()

        assertEquals(emptyList<String>(), library.favourites(FavouriteKind.Movie).first())
        assertEquals(emptyList<String>(), library.watchlist(FavouriteKind.Series).first())
        assertTrue(library.progress.first().isEmpty())
        assertFalse(library.isInWatchlist(FavouriteKind.Series, "2").first())
        library.toggleFavourite(FavouriteKind.Movie, "9")

        profiles.select(a)
        assertEquals(listOf("1"), library.favourites(FavouriteKind.Movie).first())
        assertEquals(listOf("2"), library.watchlist(FavouriteKind.Series).first())
        assertTrue(library.isInWatchlist(FavouriteKind.Series, "2").first())
    }

    @Test fun deleteRemovesOnlyThatProfilesRows() = runTest(UnconfinedTestDispatcher()) {
        val (profiles, library, _) = repos()
        val first = profiles.profiles.first().single().id
        val other = profiles.create("Other", 2)

        profiles.select(other)
        library.toggleFavourite(FavouriteKind.Movie, "5")
        library.toggleWatchlist(FavouriteKind.Movie, "5")
        library.saveMovieProgress(5, 1, 10)
        profiles.select(first)
        library.toggleFavourite(FavouriteKind.Movie, "6")

        profiles.select(other)
        assertTrue(profiles.delete(other))
        // The active profile was deleted, so it switched to the remaining one.
        assertEquals(first, profiles.activeProfile.first()?.id)
        assertEquals(listOf("6"), library.favourites(FavouriteKind.Movie).first())
        assertEquals(1, profiles.profiles.first().size)

        assertFalse("last profile cannot be deleted", profiles.delete(first))
        assertEquals(1, profiles.profiles.first().size)
    }

    @Test fun missingStoredProfileFallsBackToLowestId() = runTest(UnconfinedTestDispatcher()) {
        val (profiles, _, _) = repos()
        profiles.create("Second", 1)
        profiles.select(99)
        assertEquals(profiles.profiles.first().minOf { it.id }, profiles.activeProfile.first()?.id)
    }
}
