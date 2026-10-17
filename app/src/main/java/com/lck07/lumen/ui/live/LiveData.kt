package com.lck07.lumen.ui.live

import androidx.compose.runtime.Immutable
import com.lck07.lumen.AppContainer
import com.lck07.lumen.data.Show
import com.lck07.lumen.data.db.ChannelEntity
import com.lck07.lumen.data.toModel

/** What's on a channel now and next. */
@Immutable
data class NowNext(val now: Show?, val next: Show?)

/**
 * Now and next for one channel: from the imported guide, or the server's short EPG for
 * channels the guide doesn't cover.
 */
suspend fun nowNext(c: AppContainer, channel: ChannelEntity, at: Long = System.currentTimeMillis()): NowNext {
    val list = c.guide.upcoming(channel.epgId, at, 3)
    if (list.isNotEmpty()) return NowNext(list.firstOrNull { it.isOn(at) }, list.firstOrNull { it.start >= at })
    val client = c.account.client ?: return NowNext(null, null)
    val short = runCatching { client.shortEpg(channel.toModel(), 3) }.getOrDefault(emptyList())
        .map { Show(it.channelId, it.start.toEpochMilli(), it.stop.toEpochMilli(), it.title, it.description) }
    return NowNext(short.firstOrNull { it.isOn(at) }, short.firstOrNull { it.start >= at })
}

/** A group of channels for the strip and the guide: favourites or a provider category. */
@Immutable
data class ChannelGroup(val key: String, val title: String)

const val FAVOURITES_GROUP = "fav"

/** The channels of a group, in the provider's order (favourites in the order they were added). */
suspend fun channelsOf(c: AppContainer, group: String): List<ChannelEntity> {
    val dao = c.db.catalog()
    return if (group == FAVOURITES_GROUP) {
        val ids = c.library.favouriteIdsNow(com.lck07.lumen.data.FavouriteKind.Channel).mapNotNull { it.toIntOrNull() }
        val byId = dao.channelsByIdNow(ids).associateBy { it.id }
        ids.mapNotNull { byId[it] }
    } else dao.channelsInNow(group)
}
