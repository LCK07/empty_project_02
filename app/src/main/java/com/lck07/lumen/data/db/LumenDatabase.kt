package com.lck07.lumen.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class, ChannelEntity::class, MovieEntity::class, MovieDetailEntity::class,
        SeriesEntity::class, SeriesDetailEntity::class, EpisodeEntity::class, ProgrammeEntity::class,
        GuideStateEntity::class, ProgressEntity::class, FavouriteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun guide(): GuideDao
    abstract fun library(): LibraryDao

    companion object {
        fun create(context: Context, inMemory: Boolean = false): LumenDatabase =
            (if (inMemory) Room.inMemoryDatabaseBuilder(context, LumenDatabase::class.java)
            else Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db"))
                // The catalogue and guide are caches; progress and favourites are worth keeping,
                // so real schema changes add migrations. Until then a schema change rebuilds.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
