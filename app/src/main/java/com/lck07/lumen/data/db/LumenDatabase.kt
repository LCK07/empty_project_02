package com.lck07.lumen.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class, ChannelEntity::class, MovieEntity::class, MovieDetailEntity::class,
        SeriesEntity::class, SeriesDetailEntity::class, EpisodeEntity::class, ProgrammeEntity::class,
        GuideStateEntity::class, ProgressEntity::class, FavouriteEntity::class,
        WatchlistEntity::class, ProfileEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun guide(): GuideDao
    abstract fun library(): LibraryDao
    abstract fun profiles(): ProfileDao

    companion object {
        /**
         * Adds profiles. Existing favourites and progress move to profile 1 (empty name = the
         * localised default). favourite and progress are rebuilt because the primary key changes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `avatar` INTEGER NOT NULL, `contentLanguages` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `profile` (`id`, `name`, `avatar`, `contentLanguages`, `createdAt`) VALUES (1, '', 0, 'de,en', ${System.currentTimeMillis()})")

                db.execSQL("CREATE TABLE `favourite_new` (`profileId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `itemId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `kind`, `itemId`))")
                db.execSQL("INSERT INTO `favourite_new` (`profileId`, `kind`, `itemId`, `addedAt`) SELECT 1, `kind`, `itemId`, `addedAt` FROM `favourite`")
                db.execSQL("DROP TABLE `favourite`")
                db.execSQL("ALTER TABLE `favourite_new` RENAME TO `favourite`")

                db.execSQL("CREATE TABLE `progress_new` (`profileId` INTEGER NOT NULL, `key` TEXT NOT NULL, `seriesId` INTEGER, `movieId` INTEGER, `episodeId` TEXT, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `watchNextId` INTEGER DEFAULT NULL, PRIMARY KEY(`profileId`, `key`))")
                db.execSQL("INSERT INTO `progress_new` (`profileId`, `key`, `seriesId`, `movieId`, `episodeId`, `positionMs`, `durationMs`, `updatedAt`, `watchNextId`) SELECT 1, `key`, `seriesId`, `movieId`, `episodeId`, `positionMs`, `durationMs`, `updatedAt`, `watchNextId` FROM `progress`")
                db.execSQL("DROP TABLE `progress`")
                db.execSQL("ALTER TABLE `progress_new` RENAME TO `progress`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_updatedAt` ON `progress` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_seriesId` ON `progress` (`seriesId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `watchlist` (`profileId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `itemId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `kind`, `itemId`))")

                db.execSQL("ALTER TABLE `category` ADD COLUMN `language` TEXT")
                db.execSQL("ALTER TABLE `movie` ADD COLUMN `language` TEXT")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `language` TEXT")
            }
        }

        /** A fresh install has no migration, so it gets the same default profile 1. */
        val SEED_PROFILE = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO `profile` (`id`, `name`, `avatar`, `contentLanguages`, `createdAt`) VALUES (1, '', 0, 'de,en', ${System.currentTimeMillis()})")
            }
        }

        fun create(context: Context, inMemory: Boolean = false): LumenDatabase =
            (if (inMemory) Room.inMemoryDatabaseBuilder(context, LumenDatabase::class.java)
            else Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db"))
                // The catalogue and guide are caches; progress, favourites and profiles are worth
                // keeping, so every upgrade needs an explicit migration. Only downgrades rebuild.
                .addMigrations(MIGRATION_1_2)
                .addCallback(SEED_PROFILE)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
