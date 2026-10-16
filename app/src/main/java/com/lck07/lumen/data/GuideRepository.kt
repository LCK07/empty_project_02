package com.lck07.lumen.data

import android.util.Xml
import com.lck07.lumen.core.epg.XmltvParser
import com.lck07.lumen.core.xtream.XtreamClient
import com.lck07.lumen.data.db.LumenDatabase
import com.lck07.lumen.data.db.NowRow
import com.lck07.lumen.data.db.ProgrammeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.GZIPInputStream

/** One programme for the UI, with times in epoch milliseconds. */
data class Show(val channelKey: String, val start: Long, val stop: Long, val title: String, val description: String?) {
    fun progress(now: Long): Float = if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
    fun isOn(now: Long) = now in start until stop
    val minutes: Int get() = ((stop - start) / 60_000).toInt()
}

fun NowRow.toShow() = Show(channelKey, start, stop, title, description)

/**
 * The programme guide: imported from the account's XMLTV file (streamed, never held in memory),
 * keeping only the channels this account has and the next three days.
 */
class GuideRepository(private val db: LumenDatabase, private val http: OkHttpClient) {
    private val dao = db.guide()

    val state = dao.stateFlow()

    /** Ticks at the start of every minute, so "now" stays current. */
    val minuteTicker: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000 - now % 60_000 + 50)
        }
    }

    /** What is on now on every channel, keyed by XMLTV channel id; updates every minute and after imports. */
    val onNow: Flow<Map<String, Show>> = combine(minuteTicker, dao.stateFlow()) { now, _ -> now }
        .map { now -> dao.onNow(now).associate { it.channelKey to it.toShow() } }
        .flowOn(Dispatchers.IO)

    suspend fun upcoming(channelKey: String?, now: Long = System.currentTimeMillis(), limit: Int = 2): List<Show> =
        if (channelKey == null) emptyList() else withContext(Dispatchers.IO) { dao.upcoming(channelKey, now, limit).map { it.toShow() } }

    suspend fun range(channelKeys: List<String>, from: Long, to: Long): Map<String, List<Show>> =
        if (channelKeys.isEmpty()) emptyMap()
        else withContext(Dispatchers.IO) {
            channelKeys.chunked(500).flatMap { dao.range(it, from, to) }.map { it.toShow() }.groupBy { it.channelKey }
        }

    /**
     * Downloads and stores the guide. The new guide replaces the old one only once it is
     * complete; ended programmes are dropped. Returns the number of programmes stored.
     */
    suspend fun import(client: XtreamClient, now: Instant = Instant.now(), days: Long = 3): Int = withContext(Dispatchers.IO) {
        val wanted = db.catalog().epgIds().toSet()
        if (wanted.isEmpty()) return@withContext 0
        val window = now.minusSeconds(6 * 3600)..now.plusSeconds(days * 86_400)
        val gen = (dao.state()?.activeGen ?: 0) + 1
        dao.deleteGen(gen)
        val request = Request.Builder().url(client.xmltvUrl()).header("User-Agent", XtreamClient.USER_AGENT).build()
        var count = 0
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Guide download failed: HTTP ${r.code}")
            val parser = XmltvParser { Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) } }
            val batch = ArrayList<ProgrammeEntity>(1000)
            maybeGunzip(r.body.byteStream()).use { input ->
                parser.parse(input, window, wanted) { p ->
                    batch += ProgrammeEntity(gen = gen, channelKey = p.channelId, start = p.start.toEpochMilli(), stop = p.stop.toEpochMilli(), title = p.title, description = p.description)
                    if (batch.size >= 1000) { dao.insertBlocking(batch.toList()); count += batch.size; batch.clear() }
                }
            }
            if (batch.isNotEmpty()) { dao.insertBlocking(batch.toList()); count += batch.size }
        }
        if (count > 0) dao.activate(gen, System.currentTimeMillis()) else dao.deleteGen(gen)
        dao.deleteEndedBefore(now.toEpochMilli() - 6 * 3600_000)
        count
    }

    private fun maybeGunzip(raw: InputStream): InputStream {
        val input = BufferedInputStream(raw, 64 * 1024)
        input.mark(2)
        val b1 = input.read(); val b2 = input.read()
        input.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(input, 64 * 1024) else input
    }
}
