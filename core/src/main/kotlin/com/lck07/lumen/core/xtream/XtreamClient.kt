package com.lck07.lumen.core.xtream

import com.lck07.lumen.core.model.AccountInfo
import com.lck07.lumen.core.model.Category
import com.lck07.lumen.core.model.Episode
import com.lck07.lumen.core.model.LiveChannel
import com.lck07.lumen.core.model.Movie
import com.lck07.lumen.core.model.MovieDetail
import com.lck07.lumen.core.model.Programme
import com.lck07.lumen.core.model.Series
import com.lck07.lumen.core.model.SeriesDetail
import com.lck07.lumen.core.model.XtreamAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Base64

/**
 * The Xtream Codes player API (player_api.php): login, catalogue and short EPG,
 * plus the stream URLs the player opens.
 */
class XtreamClient(
    val account: XtreamAccount,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val base: HttpUrl = account.baseUrl.toHttpUrl()

    /** Logs in. Throws [XtreamException.AuthFailed] or [XtreamException.Inactive] when the account can't be used. */
    suspend fun login(): AccountInfo {
        val root = get() as? JsonObject ?: throw XtreamException.BadResponse()
        val user = root.obj("user_info") ?: throw XtreamException.AuthFailed()
        if (user.int("auth") == 0) throw XtreamException.AuthFailed()
        val status = user.str("status") ?: "Active"
        val info = AccountInfo(
            active = status.equals("Active", ignoreCase = true),
            status = status,
            expiresAt = user.epochSeconds("exp_date"),
            maxConnections = user.int("max_connections"),
            activeConnections = user.int("active_cons"),
            serverTimezone = root.obj("server_info")?.str("timezone"),
        )
        if (!info.active) throw XtreamException.Inactive(status)
        return info
    }

    suspend fun liveCategories(): List<Category> = categories("get_live_categories")
    suspend fun movieCategories(): List<Category> = categories("get_vod_categories")
    suspend fun seriesCategories(): List<Category> = categories("get_series_categories")

    suspend fun liveChannels(categoryId: String? = null): List<LiveChannel> =
        get("get_live_streams", "category_id" to categoryId).objects().mapIndexedNotNull { i, o -> channelOf(o, i) }

    /**
     * The whole channel list, delivered in batches while it downloads: big accounts send tens of
     * thousands of entries, which should never sit in memory all at once. Returns the count.
     */
    suspend fun streamLiveChannels(batchSize: Int = 500, onBatch: suspend (List<LiveChannel>) -> Unit): Int =
        stream("get_live_streams", batchSize, onBatch) { i, o -> channelOf(o, i) }

    suspend fun streamMovies(batchSize: Int = 500, onBatch: suspend (List<Movie>) -> Unit): Int =
        stream("get_vod_streams", batchSize, onBatch) { _, o -> movieOf(o) }

    suspend fun streamSeries(batchSize: Int = 500, onBatch: suspend (List<Series>) -> Unit): Int =
        stream("get_series", batchSize, onBatch) { _, o -> seriesOf(o) }

    suspend fun movies(categoryId: String? = null): List<Movie> =
        get("get_vod_streams", "category_id" to categoryId).objects().mapNotNull(::movieOf)

    suspend fun movieDetail(movie: Movie): MovieDetail {
        val root = get("get_vod_info", "vod_id" to movie.id.toString()) as? JsonObject ?: throw XtreamException.BadResponse()
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val data = root.obj("movie_data")
        return MovieDetail(
            movie = movie.copy(extension = data?.str("container_extension") ?: movie.extension),
            plot = info.str("plot") ?: info.str("description"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            durationSeconds = info.int("duration_secs"),
            backdrops = info.strings("backdrop_path"),
        )
    }

    suspend fun series(categoryId: String? = null): List<Series> =
        get("get_series", "category_id" to categoryId).objects().mapNotNull(::seriesOf)

    suspend fun seriesDetail(series: Series): SeriesDetail {
        val root = get("get_series_info", "series_id" to series.id.toString()) as? JsonObject ?: throw XtreamException.BadResponse()
        val info = root.obj("info")
        val merged = if (info != null) series.copy(
            plot = info.str("plot") ?: series.plot,
            genre = info.str("genre") ?: series.genre,
            backdrops = info.strings("backdrop_path").ifEmpty { series.backdrops },
        ) else series
        val bySeason = (root["episodes"] ?: JsonObject(emptyMap())).let { eps ->
            when (eps) {
                is JsonObject -> eps.entries.flatMap { (season, list) -> list.objects().map { season.toIntOrNull() to it } }
                else -> eps.objects().map { null to it }
            }
        }.mapNotNull { (seasonKey, o) -> episodeOf(o, seasonKey, series.name) }
            .groupBy { it.season }
            .mapValues { (_, list) -> list.sortedBy { it.number } }
            .toSortedMap()
        return SeriesDetail(merged, bySeason.keys.toList(), bySeason)
    }

    /** Now and next for one channel, from the server's short EPG (titles arrive base64-encoded). */
    suspend fun shortEpg(channel: LiveChannel, limit: Int = 4): List<Programme> {
        val root = get("get_short_epg", "stream_id" to channel.id.toString(), "limit" to limit.toString()) as? JsonObject
            ?: return emptyList()
        return root["epg_listings"]?.objects().orEmpty().mapNotNull { o ->
            val start = o.epochSeconds("start_timestamp") ?: return@mapNotNull null
            val stop = o.epochSeconds("stop_timestamp") ?: return@mapNotNull null
            Programme(channel.epgId ?: channel.id.toString(), start, stop, decode(o.str("title")) ?: "", decode(o.str("description")))
        }
    }

    // ---- Stream URLs ---------------------------------------------------------------------

    /** Live streams as MPEG-TS by default; "m3u8" gives HLS on servers that offer it. */
    fun liveUrl(channel: LiveChannel, extension: String = "ts"): String = streamUrl("live", "${channel.id}.$extension")
    fun movieUrl(movie: Movie): String = streamUrl("movie", "${movie.id}.${movie.extension}")
    fun episodeUrl(episode: Episode): String = streamUrl("series", "${episode.id}.${episode.extension}")
    /** The full XMLTV guide for this account. */
    fun xmltvUrl(): String = base.newBuilder().addPathSegment("xmltv.php")
        .addQueryParameter("username", account.username).addQueryParameter("password", account.password).build().toString()

    private fun streamUrl(kind: String, file: String): String =
        base.newBuilder().addPathSegment(kind).addPathSegment(account.username).addPathSegment(account.password).addPathSegment(file).build().toString()

    // ---- Plumbing ------------------------------------------------------------------------

    private suspend fun categories(action: String): List<Category> = get(action).objects().mapNotNull { o ->
        val id = o.str("category_id") ?: return@mapNotNull null
        Category(id, o.str("category_name") ?: id)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> stream(
        action: String,
        batchSize: Int,
        onBatch: suspend (List<T>) -> Unit,
        map: (index: Int, JsonObject) -> T?,
    ): Int = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(Request.Builder().url(apiUrl(action)).header("User-Agent", USER_AGENT).build()).execute()
        } catch (e: IOException) {
            throw XtreamException.Network(e)
        }
        response.use { r ->
            if (r.code == 401 || r.code == 403) throw XtreamException.AuthFailed()
            if (!r.isSuccessful) throw XtreamException.Http(r.code)
            var index = 0
            var count = 0
            val batch = ArrayList<T>(batchSize)
            try {
                r.body.byteStream().buffered().use { input ->
                    // Lenient parsing would quietly accept an HTML error page: insist on JSON.
                    input.mark(8192)
                    var first = input.read()
                    while (first != -1 && first.toChar().isWhitespace()) first = input.read()
                    if (first == -1) return@withContext 0
                    if (first != '['.code && first != '{'.code) throw XtreamException.BadResponse()
                    input.reset()
                    // A top-level array arrives element by element; a top-level object (some panels
                    // key the list by index) arrives whole and is expanded below.
                    for (element in json.decodeToSequence<JsonElement>(input, DecodeSequenceMode.AUTO_DETECT)) {
                        val items: List<JsonObject> = when (element) {
                            is JsonObject -> if (element.isNotEmpty() && element.values.all { it is JsonObject }) element.values.map { it as JsonObject } else listOf(element)
                            is JsonArray -> element.objects()
                            else -> emptyList()
                        }
                        for (o in items) {
                            map(index++, o)?.let { batch += it; count++ }
                            if (batch.size >= batchSize) { onBatch(batch.toList()); batch.clear() }
                        }
                    }
                }
            } catch (e: XtreamException) {
                throw e
            } catch (e: IOException) {
                throw XtreamException.Network(e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw XtreamException.BadResponse(e)
            }
            if (batch.isNotEmpty()) onBatch(batch.toList())
            count
        }
    }

    private fun apiUrl(action: String? = null, vararg params: Pair<String, String?>): HttpUrl =
        base.newBuilder().addPathSegment("player_api.php")
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
            }.build()

    private suspend fun get(action: String? = null, vararg params: Pair<String, String?>): JsonElement = withContext(Dispatchers.IO) {
        val url = apiUrl(action, *params)
        val body = try {
            http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                if (r.code == 401 || r.code == 403) throw XtreamException.AuthFailed()
                if (!r.isSuccessful) throw XtreamException.Http(r.code)
                r.body.string()
            }
        } catch (e: IOException) {
            throw XtreamException.Network(e)
        }
        if (body.isBlank()) return@withContext JsonObject(emptyMap())
        try { json.parseToJsonElement(body) } catch (e: Exception) { throw XtreamException.BadResponse(e) }
    }

    private fun channelOf(o: JsonObject, index: Int): LiveChannel? {
        val id = o.int("stream_id") ?: return null
        return LiveChannel(
            id = id,
            number = o.int("num") ?: (index + 1),
            name = o.str("name") ?: "Channel $id",
            logo = o.str("stream_icon"),
            epgId = o.str("epg_channel_id"),
            categoryId = o.str("category_id"),
            archiveDays = if (o.int("tv_archive") == 1) o.int("tv_archive_duration") ?: 0 else 0,
        )
    }

    private fun movieOf(o: JsonObject): Movie? {
        val id = o.int("stream_id") ?: return null
        return Movie(
            id = id,
            name = o.str("name") ?: "Movie $id",
            poster = o.str("stream_icon"),
            rating = o.double("rating"),
            categoryId = o.str("category_id"),
            added = o.epochSeconds("added"),
            extension = o.str("container_extension") ?: "mp4",
        )
    }

    private fun seriesOf(o: JsonObject): Series? {
        val id = o.int("series_id") ?: return null
        return Series(
            id = id,
            name = o.str("name") ?: "Series $id",
            cover = o.str("cover"),
            plot = o.str("plot"),
            genre = o.str("genre"),
            releaseDate = o.str("releaseDate") ?: o.str("release_date"),
            rating = o.double("rating"),
            categoryId = o.str("category_id"),
            lastModified = o.epochSeconds("last_modified"),
            backdrops = o.strings("backdrop_path"),
        )
    }

    private fun episodeOf(o: JsonObject, seasonKey: Int?, seriesName: String): Episode? {
        val id = o.str("id") ?: return null
        val season = o.int("season") ?: seasonKey ?: 1
        val number = o.int("episode_num") ?: return null
        val info = o.obj("info")
        return Episode(
            id = id,
            season = season,
            number = number,
            title = cleanEpisodeTitle(o.str("title"), seriesName, number),
            extension = o.str("container_extension") ?: "mp4",
            durationSeconds = info?.int("duration_secs"),
            plot = info?.str("plot"),
            image = info?.str("movie_image"),
        )
    }

    private fun decode(s: String?): String? = s?.let {
        try { String(Base64.getDecoder().decode(it), Charsets.UTF_8) } catch (_: IllegalArgumentException) { it }
    }?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val USER_AGENT = "Lumen/0.1 (Android TV)"

        private val EPISODE_TAG = Regex("""^.*?\bS\d{1,2}\s*E\d{1,3}\b\s*[-:–]?\s*""", RegexOption.IGNORE_CASE)

        /** "Northbound - S02E05 - Tide Line" → "Tide Line"; nothing left → "Episode 5". */
        fun cleanEpisodeTitle(raw: String?, seriesName: String, number: Int): String {
            var t = raw?.trim().orEmpty()
            if (EPISODE_TAG.containsMatchIn(t)) t = t.replace(EPISODE_TAG, "")
            else if (t.startsWith(seriesName, ignoreCase = true)) t = t.substring(seriesName.length).trimStart(' ', '-', ':', '–')
            return t.trim().ifEmpty { "Episode $number" }
        }
    }
}
