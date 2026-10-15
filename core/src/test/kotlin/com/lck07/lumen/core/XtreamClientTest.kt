package com.lck07.lumen.core

import com.lck07.lumen.core.model.XtreamAccount
import com.lck07.lumen.core.xtream.XtreamClient
import com.lck07.lumen.core.xtream.XtreamException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XtreamClientTest {
    private val server = MockWebServer()
    private lateinit var client: XtreamClient

    @Before fun start() {
        server.start()
        client = XtreamClient(XtreamAccount(server.url("/").toString(), "user", "p@ss/word"))
    }
    @After fun stop() = server.shutdown()

    private fun respond(body: String) = server.enqueue(MockResponse().setBody(body))
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun loginReadsAccount() = runTest {
        respond("""{"user_info":{"auth":1,"status":"Active","exp_date":"1803945600","max_connections":"2","active_cons":0},"server_info":{"timezone":"Europe/Berlin"}}""")
        val info = client.login()
        assertTrue(info.active)
        assertEquals(Instant.ofEpochSecond(1803945600), info.expiresAt)
        assertEquals(2, info.maxConnections)
        assertEquals("Europe/Berlin", info.serverTimezone)
        val req = server.takeRequest()
        assertEquals("/player_api.php", req.requestUrl!!.encodedPath)
        assertEquals("p@ss/word", req.requestUrl!!.queryParameter("password"))
    }

    @Test fun loginRejectsWrongPassword() = runTest {
        respond("""{"user_info":{"auth":0}}""")
        assertFailsWith<XtreamException.AuthFailed> { client.login() }
    }

    @Test fun loginRejectsExpiredAccount() = runTest {
        respond("""{"user_info":{"auth":1,"status":"Expired","exp_date":null}}""")
        val e = assertFailsWith<XtreamException.Inactive> { client.login() }
        assertEquals("Expired", e.status)
    }

    @Test fun httpErrorsAreReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val e = assertFailsWith<XtreamException.Http> { client.liveChannels() }
        assertEquals(503, e.code)
    }

    @Test fun liveChannelsTolerateMixedTypes() = runTest {
        respond("""[{"num":1,"name":"Nordlicht 1","stream_id":"11","stream_icon":"","epg_channel_id":"n1.de","category_id":"3","tv_archive":1,"tv_archive_duration":"7"},
                   {"num":"2","name":"Arena","stream_id":12,"stream_icon":"http://img/a.png","epg_channel_id":null,"category_id":3,"tv_archive":0},
                   {"name":"broken, no id"}]""")
        val list = client.liveChannels("3")
        assertEquals(2, list.size)
        assertEquals(11, list[0].id); assertNull(list[0].logo); assertEquals(7, list[0].archiveDays)
        assertEquals(2, list[1].number); assertNull(list[1].epgId); assertEquals(0, list[1].archiveDays)
        assertEquals("get_live_streams", server.takeRequest().requestUrl!!.queryParameter("action"))
    }

    @Test fun seriesDetailGroupsEpisodesAndCleansTitles() = runTest {
        respond("""[{"series_id":"77","name":"Northbound","cover":"http://img/c.jpg","rating":"8,4","backdrop_path":[]}]""")
        val series = client.series().single()
        assertEquals(8.4, series.rating)
        respond(resource("series_info.json"))
        val d = client.seriesDetail(series)
        assertEquals(listOf(1, 2), d.seasons)
        assertEquals(listOf("Arrival", "Episode 2"), d.episodes[1]!!.map { it.title })
        assertEquals(listOf("Low Water", "Tide Line"), d.episodes[2]!!.map { it.title })
        assertEquals(2700, d.episodes[1]!![0].durationSeconds)
        assertEquals("A ferry captain returns.", d.series.plot)
        // The next episode crosses into the next season.
        assertEquals("2001", d.next(d.episodes[1]!![1])!!.id)
        assertNull(d.next(d.episodes[2]!![1]))
    }

    @Test fun streamUrlsEscapeCredentials() {
        val ep = com.lck07.lumen.core.model.Episode("2002", 2, 2, "Tide Line", "mp4", null, null, null)
        val url = client.episodeUrl(ep)
        assertTrue(url.endsWith("/series/user/p@ss%2Fword/2002.mp4"), url)
        assertTrue(client.xmltvUrl().contains("xmltv.php?username=user&password=p%40ss%2Fword"), client.xmltvUrl())
    }

    @Test fun shortEpgDecodesBase64() = runTest {
        respond("""{"epg_listings":[{"title":"V2V0dGVy","description":"RGFzIFdldHRlci4=","start_timestamp":"1792094400","stop_timestamp":"1792097100"}]}""")
        val ch = com.lck07.lumen.core.model.LiveChannel(11, 1, "N1", null, "n1.de", null, 0)
        val p = client.shortEpg(ch).single()
        assertEquals("Wetter", p.title); assertEquals("Das Wetter.", p.description); assertEquals("n1.de", p.channelId)
    }

    @Test fun cleansEpisodeTitles() {
        assertEquals("Tide Line", XtreamClient.cleanEpisodeTitle("Northbound - S02E05 - Tide Line", "Northbound", 5))
        assertEquals("Tide Line", XtreamClient.cleanEpisodeTitle("Northbound: Tide Line", "Northbound", 5))
        assertEquals("Episode 5", XtreamClient.cleanEpisodeTitle("Northbound S02E05", "Northbound", 5))
        assertEquals("Episode 3", XtreamClient.cleanEpisodeTitle(null, "X", 3))
    }

    @Test fun streamsBigListsInBatches() = runTest {
        val items = (1..1203).joinToString(",") { """{"stream_id":$it,"name":"Film $it","container_extension":"mkv","rating":"7,5","added":"1790000000"}""" }
        respond("""[$items,{"name":"no id"}]""")
        val batches = mutableListOf<Int>()
        val ids = mutableListOf<Int>()
        val count = client.streamMovies(batchSize = 500) { b -> batches += b.size; ids += b.map { it.id } }
        assertEquals(1203, count)
        assertEquals(listOf(500, 500, 203), batches)
        assertEquals((1..1203).toList(), ids)
        assertEquals("get_vod_streams", server.takeRequest().requestUrl!!.queryParameter("action"))
    }

    @Test fun streamsListsKeyedByIndex() = runTest {
        respond("""{"0":{"series_id":"5","name":"A"},"1":{"series_id":6,"name":"B","last_modified":"1790000000"}}""")
        val got = mutableListOf<String>()
        assertEquals(2, client.streamSeries { b -> got += b.map { it.name } })
        assertEquals(listOf("A", "B"), got)
    }

    @Test fun streamingAnEmptyBodyGivesNothing() = runTest {
        respond("")
        assertEquals(0, client.streamLiveChannels { error("no batches expected") })
        respond("[]")
        assertEquals(0, client.streamLiveChannels { error("no batches expected") })
    }

    @Test fun streamingGarbageIsABadResponse() = runTest {
        respond("<html>Not found</html>")
        assertFailsWith<XtreamException.BadResponse> { client.streamMovies { } }
    }

    @Test fun accountAddsSchemeAndDropsSlash() {
        assertEquals("http://tv.example.net:8080", XtreamAccount("tv.example.net:8080/", "u", "p").baseUrl)
        assertEquals("https://tv.example.net", XtreamAccount(" https://tv.example.net ", "u", "p").baseUrl)
    }
}
