package com.lck07.lumen.core.model

import java.time.Instant

/** An Xtream Codes login: the server address plus the user's credentials. */
data class XtreamAccount(val server: String, val username: String, val password: String) {
    /** The server as "scheme://host[:port]", without a trailing slash; "http://" is assumed when missing. */
    val baseUrl: String = server.trim().trimEnd('/').let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) it else "http://$it"
    }
}

/** What the server says about the account after logging in. */
data class AccountInfo(
    val active: Boolean,
    val status: String,
    val expiresAt: Instant?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val serverTimezone: String?,
)

data class Category(val id: String, val name: String)

data class LiveChannel(
    val id: Int,
    val number: Int,
    val name: String,
    val logo: String?,
    /** Matches the channel id in the XMLTV guide. */
    val epgId: String?,
    val categoryId: String?,
    /** Days of catch-up the server keeps, 0 when there is none. */
    val archiveDays: Int,
)

data class Movie(
    val id: Int,
    val name: String,
    val poster: String?,
    val rating: Double?,
    val categoryId: String?,
    val added: Instant?,
    val extension: String,
)

data class MovieDetail(
    val movie: Movie,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSeconds: Int?,
    val backdrops: List<String>,
)

data class Series(
    val id: Int,
    val name: String,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    val categoryId: String?,
    val lastModified: Instant?,
    val backdrops: List<String>,
)

data class Episode(
    /** The stream id used in the playback URL (a string on most servers). */
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val durationSeconds: Int?,
    val plot: String?,
    val image: String?,
)

data class SeriesDetail(val series: Series, val seasons: List<Int>, val episodes: Map<Int, List<Episode>>) {
    fun episode(season: Int, number: Int): Episode? = episodes[season]?.firstOrNull { it.number == number }

    /** The episode after this one, crossing into the next season when needed. */
    fun next(after: Episode): Episode? {
        val list = episodes[after.season].orEmpty()
        val i = list.indexOfFirst { it.id == after.id }
        if (i >= 0 && i + 1 < list.size) return list[i + 1]
        val nextSeason = seasons.filter { it > after.season }.minOrNull() ?: return null
        return episodes[nextSeason]?.firstOrNull()
    }
}

/** One programme in the guide. */
data class Programme(
    val channelId: String,
    val start: Instant,
    val stop: Instant,
    val title: String,
    val description: String?,
) {
    fun isOn(at: Instant) = !at.isBefore(start) && at.isBefore(stop)

    /** 0..1 through the programme at [at]. */
    fun progress(at: Instant): Float {
        val total = stop.toEpochMilli() - start.toEpochMilli()
        if (total <= 0) return 0f
        return ((at.toEpochMilli() - start.toEpochMilli()).toFloat() / total).coerceIn(0f, 1f)
    }
}
