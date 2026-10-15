package com.lck07.lumen.core

import com.lck07.lumen.core.library.Library
import com.lck07.lumen.core.library.WatchProgress
import com.lck07.lumen.core.model.Episode
import com.lck07.lumen.core.model.Series
import com.lck07.lumen.core.model.SeriesDetail
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryTest {
    private fun ep(id: String, s: Int, n: Int) = Episode(id, s, n, "E$n", "mp4", 2700, null, null)
    private fun series(id: Int, name: String) = SeriesDetail(
        Series(id, name, null, null, null, null, null, null, null, emptyList()),
        listOf(1, 2),
        mapOf(1 to listOf(ep("$id-11", 1, 1), ep("$id-12", 1, 2)), 2 to listOf(ep("$id-21", 2, 1))),
    )
    private val details = mapOf(1 to series(1, "Northbound"), 2 to series(2, "Glass Harbor"), 3 to series(3, "Done"))
    private val min = 60_000L
    private fun t(m: Long) = Instant.parse("2026-10-15T18:00:00Z").plusSeconds(m * 60)

    @Test fun ordersByLastWatchedAndPicksTheRightEpisode() {
        val progress = listOf(
            // Northbound: episode 1x2 half watched, most recent.
            WatchProgress(WatchProgress.episodeKey(1, "1-12"), 27 * min, 45 * min, t(30)),
            WatchProgress(WatchProgress.episodeKey(1, "1-11"), 45 * min, 45 * min, t(5)),
            // Glass Harbor: finished 1x2, so 2x1 is next, starting from the beginning.
            WatchProgress(WatchProgress.episodeKey(2, "2-12"), 44 * min, 45 * min, t(20)),
            // Done: finished the last episode, so it drops out.
            WatchProgress(WatchProgress.episodeKey(3, "3-21"), 45 * min, 45 * min, t(40)),
        )
        val up = Library.upNext(progress) { details[it] }
        assertEquals(listOf("Northbound", "Glass Harbor"), up.map { it.detail.series.name })
        assertEquals("1-12", up[0].episode.id); assertTrue(up[0].resumes)
        assertEquals("2-21", up[1].episode.id); assertFalse(up[1].resumes)
    }

    @Test fun thresholds() {
        assertFalse(WatchProgress("movie:1", 30_000, 6_000_000, t(0)).inProgress)       // under a minute
        assertTrue(WatchProgress("movie:1", 3_000_000, 6_000_000, t(0)).inProgress)
        assertTrue(WatchProgress("movie:1", 5_950_000, 6_000_000, t(0)).finished)       // in the credits
        assertEquals(listOf(7), Library.moviesInProgress(listOf(
            WatchProgress("movie:7", 3_000_000, 6_000_000, t(1)),
            WatchProgress("movie:8", 5_990_000, 6_000_000, t(2)),
        )).map { it.movieId })
    }
}
