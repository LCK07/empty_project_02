package com.lck07.lumen.core.epg

import com.lck07.lumen.core.model.Programme
import java.time.Instant

/** The guide in memory, sorted per channel, for "now / next" and the grid. */
class EpgIndex(programmes: Iterable<Programme>) {
    private val byChannel: Map<String, List<Programme>> =
        programmes.groupBy { it.channelId }.mapValues { (_, list) -> list.sortedBy { it.start }.dedupe() }

    val channelIds: Set<String> get() = byChannel.keys

    fun on(channelId: String?, at: Instant): Programme? = list(channelId).let { l ->
        val i = l.binarySearchBy(at) { it.start }.let { if (it >= 0) it else -it - 2 }
        l.getOrNull(i)?.takeIf { it.isOn(at) }
    }

    fun next(channelId: String?, at: Instant): Programme? = list(channelId).firstOrNull { it.start >= at && !it.isOn(at) }

    /** Programmes overlapping [from, to), for one row of the guide grid. */
    fun range(channelId: String?, from: Instant, to: Instant): List<Programme> =
        list(channelId).filter { it.stop > from && it.start < to }

    private fun list(channelId: String?): List<Programme> = channelId?.let { byChannel[it] }.orEmpty()

    /** Guides often repeat a programme across sources; keep the first per start time. */
    private fun List<Programme>.dedupe(): List<Programme> = distinctBy { it.start }
}
