package com.lck07.lumen.ui.sample

import com.lck07.lumen.ui.home.HomeHero
import com.lck07.lumen.ui.home.HomeState
import com.lck07.lumen.ui.model.ChannelItem
import com.lck07.lumen.ui.model.EpisodeTileItem
import com.lck07.lumen.ui.model.PosterItem
import com.lck07.lumen.ui.model.Target

/** The mockup's fictional catalogue, for previews and UI tests. Artwork is generated. */
object SampleData {
    private val episodeTitles = listOf("Arrival", "Low Water", "The Ledger", "Fog Bell", "Tide Line", "Crossing", "Ballast", "Salt Roads", "Night Ferry", "Landfall", "Undertow", "Homeport")
    fun episodeTitle(season: Int, episode: Int) = episodeTitles[(episode - 1 + season * 3) % episodeTitles.size]

    data class S(val id: Int, val title: String, val genre: String, val age: Int)
    val series = listOf(
        S(1, "Northbound", "Drama", 16), S(2, "Glass Harbor", "Thriller", 16), S(3, "Orbit Kitchen", "Comedy", 6),
        S(4, "The Quiet Year", "Drama", 12), S(5, "Cinder & Salt", "Crime", 16), S(6, "Atlas Lane", "Mystery", 12),
        S(7, "Paper Moons", "Comedy", 0), S(8, "High Ridge", "Western", 16),
    )
    val movies = listOf(
        S(101, "Second Light", "Drama", 12), S(102, "Velocity Club", "Action", 16), S(103, "Deep Field", "Documentary", 0),
        S(104, "The Long Table", "Comedy", 6), S(105, "Ironbark", "Adventure", 12), S(106, "Paper Tigers", "Action", 16),
        S(107, "Coldwater", "Thriller", 16), S(108, "Lantern Bay", "Family", 0),
    )
    val channels = listOf(
        ChannelItem(1, 1, "Nordlicht 1", null, "Weather", 0.82f, "19:30–20:15", true),
        ChannelItem(2, 2, "Arena Sports", null, "Football: Live", 0.4f, "19:45–21:15", true),
        ChannelItem(3, 3, "Cine One", null, "Coldwater", 0.15f, "20:00–21:50", true),
        ChannelItem(6, 6, "News 24", null, "World Report", 0.55f, "19:50–20:20", true),
    )

    fun home(): HomeState = HomeState(
        hero = HomeHero(
            eyebrow = "Continue Watching", title = "Northbound", backdrop = null,
            episodeLine = "Season 2, Episode 5: ${episodeTitle(2, 5)}", genre = "Drama", rating = "16+",
            progress = 0.62f, minutesLeft = 18, resumes = true,
            play = Target.PlayEpisode(1, "1-2-5"), startOver = Target.PlayEpisode(1, "1-2-5", startOver = true),
            episodes = Target.SeriesDetail(1),
        ),
        continueWatching = listOf(
            EpisodeTileItem("cw-2", "Glass Harbor", "Season 3, Episode 2 · 31 min left", null, "Glass Harbor", Target.PlayEpisode(2, "2-3-2"), 0.3f),
            EpisodeTileItem("cw-3", "Orbit Kitchen", "Season 1, Episode 9 · 12 min left", null, "Orbit Kitchen", Target.PlayEpisode(3, "3-1-9"), 0.55f),
            EpisodeTileItem("cw-101", "Second Light", "71 min left", null, "Second Light", Target.PlayMovie(101), 0.4f),
        ),
        upNextTitle = "Northbound",
        upNext = (6..8).map { e ->
            EpisodeTileItem("un-$e", "$e. ${episodeTitle(2, e)}", "Season 2 · ${42 + (e % 4) * 3} min", null, "Northbound $e", Target.PlayEpisode(1, "1-2-$e"))
        },
        favouriteChannels = channels,
        newEpisodes = series.drop(3).map { poster(it, Target.SeriesDetail(it.id)) },
        recentMovies = movies.map { poster(it, Target.MovieDetail(it.id)) },
        loaded = true,
    )

    private fun poster(s: S, t: Target) = PosterItem("p-${s.id}", s.title, "${s.title} · ${s.genre}", null, t)
}
