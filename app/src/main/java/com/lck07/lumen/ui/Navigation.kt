package com.lck07.lumen.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

enum class TopTab(val key: String) {
    Home("home"), Series("series"), Movies("movies"), Live("live"), Guide("guide"), Search("search"), Settings("settings");

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
}

/** Screens that open on top of a tab. */
sealed interface Screen {
    val key: String

    data class SeriesDetail(val seriesId: Int) : Screen { override val key = "series:$seriesId" }
    data class MovieDetail(val movieId: Int) : Screen { override val key = "movie:$movieId" }
    /** Full-screen playback of an episode or a film. */
    data class Vod(val request: VodRequest) : Screen { override val key = "vod:${request.key}" }
    /** Full-screen live TV. */
    data class Live(val channelId: Int) : Screen { override val key = "live" }

    companion object {
        /** Detail screens restore on relaunch; players never do (the owner may be elsewhere). */
        fun restorable(s: Screen) = s is SeriesDetail || s is MovieDetail
        fun encode(s: Screen): String? = when (s) {
            is SeriesDetail -> "series:${s.seriesId}"
            is MovieDetail -> "movie:${s.movieId}"
            else -> null
        }
        fun decode(v: String): Screen? {
            val (kind, id) = v.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return when (kind) {
                "series" -> id.toIntOrNull()?.let(::SeriesDetail)
                "movie" -> id.toIntOrNull()?.let(::MovieDetail)
                else -> null
            }
        }
    }
}

sealed interface VodRequest {
    val key: String
    val startOver: Boolean

    data class Episode(val seriesId: Int, val episodeId: String, override val startOver: Boolean = false) : VodRequest {
        override val key = "episode:$seriesId:$episodeId"
    }

    data class Movie(val movieId: Int, override val startOver: Boolean = false) : VodRequest {
        override val key = "movie:$movieId"
    }
}

/**
 * The navigation state: the current tab and the screens stacked on top of it. Back always
 * returns to the parent screen. Screens below the top stay composed (hidden), so returning
 * restores focus and scroll exactly where they were.
 */
@Stable
class Navigator(initialTab: TopTab = TopTab.Home) {
    var tab by mutableStateOf(initialTab)
        private set
    val stack = mutableStateListOf<Screen>()
    val top: Screen? get() = stack.lastOrNull()

    /** Focus target for content under the tab bar (moving down from the tab bar lands here). */
    val contentRequester = FocusRequester()

    fun selectTab(t: TopTab) {
        stack.clear()
        tab = t
    }

    fun push(s: Screen) {
        // Opening the same screen again (e.g. the same live player) replaces it.
        stack.removeAll { it.key == s.key }
        stack.add(s)
    }

    /** Replaces the top screen (next episode, channel change). */
    fun replaceTop(s: Screen) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        push(s)
    }

    /** Returns false when there is nothing to go back to (the caller decides what Back does on a tab). */
    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
