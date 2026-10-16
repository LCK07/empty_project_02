package com.lck07.lumen.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
  The cached catalogue. Every import writes a new generation ("gen") and then deletes older
  rows, so the library never appears empty while it refreshes and removed titles disappear.
*/

@Entity(tableName = "category", primaryKeys = ["type", "id"])
data class CategoryEntity(
    /** "live", "movie" or "series". */
    val type: String,
    val id: String,
    val name: String,
    val position: Int,
    val gen: Int,
)

@Entity(tableName = "channel", indices = [Index("categoryId", "position"), Index("epgId"), Index("searchKey"), Index("position")])
data class ChannelEntity(
    @PrimaryKey val id: Int,
    val number: Int,
    val name: String,
    val searchKey: String,
    val logo: String?,
    val epgId: String?,
    val categoryId: String?,
    val archiveDays: Int,
    val position: Int,
    val gen: Int,
)

@Entity(tableName = "movie", indices = [Index("categoryId", "position"), Index("added"), Index("searchKey")])
data class MovieEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val searchKey: String,
    val poster: String?,
    val rating: Double?,
    val categoryId: String?,
    /** Unix seconds. */
    val added: Long?,
    val extension: String,
    val position: Int,
    val gen: Int,
)

/** Fetched when a film is opened (get_vod_info). Kept across catalogue refreshes. */
@Entity(tableName = "movie_detail")
data class MovieDetailEntity(
    @PrimaryKey val movieId: Int,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val backdrop: String?,
    val extension: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "series", indices = [Index("categoryId", "position"), Index("lastModified"), Index("searchKey")])
data class SeriesEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val searchKey: String,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    val categoryId: String?,
    /** Unix seconds; when the provider last changed the series (new episodes). */
    val lastModified: Long?,
    val backdrop: String?,
    val position: Int,
    val gen: Int,
)

/** Fetched when a series is opened or played (get_series_info). */
@Entity(tableName = "series_detail")
data class SeriesDetailEntity(
    @PrimaryKey val seriesId: Int,
    val plot: String?,
    val genre: String?,
    val backdrop: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "episode", indices = [Index("seriesId", "season", "number")])
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val seriesId: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val durationSecs: Int?,
    val plot: String?,
    val image: String?,
)

@Entity(tableName = "programme", indices = [Index("gen", "channelKey", "start"), Index("gen", "stop")])
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    /** The import this row belongs to; only the active import is shown. */
    val gen: Int,
    /** The XMLTV channel id (a channel's epg_channel_id). */
    val channelKey: String,
    /** Epoch milliseconds. */
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/** Where playback stopped; see core WatchProgress for the key format. */
@Entity(tableName = "progress", indices = [Index("updatedAt"), Index("seriesId")])
data class ProgressEntity(
    @PrimaryKey val key: String,
    val seriesId: Int?,
    val movieId: Int?,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** The row id of the matching Watch Next programme on Google TV, if published. */
    @ColumnInfo(defaultValue = "NULL") val watchNextId: Long? = null,
)

@Entity(tableName = "favourite", primaryKeys = ["kind", "itemId"])
data class FavouriteEntity(
    /** "series", "movie" or "channel". */
    val kind: String,
    val itemId: String,
    val addedAt: Long,
)

/** A programme joined with nothing else; used for now/next lookups. */
data class NowRow(
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)
