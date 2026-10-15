package com.lck07.lumen.ui.model

import androidx.compose.runtime.Immutable

/** Where selecting something leads. */
sealed interface Target {
    data class SeriesDetail(val seriesId: Int) : Target
    data class MovieDetail(val movieId: Int) : Target
    /** Play an episode; [startOver] ignores the saved position. */
    data class PlayEpisode(val seriesId: Int, val episodeId: String, val startOver: Boolean = false) : Target
    data class PlayMovie(val movieId: Int, val startOver: Boolean = false) : Target
    data class PlayChannel(val channelId: Int) : Target
}

/** A 2:3 poster (series or film). */
@Immutable
data class PosterItem(
    val key: String,
    val title: String,
    /** Shown under the poster while it has focus ("Northbound · Drama"). */
    val caption: String,
    val image: String?,
    val target: Target,
    val isNew: Boolean = false,
    val favouriteKey: String? = null,
)

/** A 16:9 tile for an episode or a film in progress. */
@Immutable
data class EpisodeTileItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val image: String?,
    val seed: String,
    val target: Target,
    val progress: Float? = null,
    val watched: Boolean = false,
)

/** A live channel with what's on now. */
@Immutable
data class ChannelItem(
    val id: Int,
    val number: Int,
    val name: String,
    val logo: String?,
    val nowTitle: String?,
    val nowProgress: Float?,
    /** "20:00–21:00", for the caption under the tile. */
    val nowTime: String?,
    val favourite: Boolean = false,
)
