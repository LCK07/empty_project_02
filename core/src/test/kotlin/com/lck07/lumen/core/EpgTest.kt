package com.lck07.lumen.core

import com.lck07.lumen.core.epg.EpgIndex
import com.lck07.lumen.core.epg.XmltvParser
import com.lck07.lumen.core.model.Programme
import org.kxml2.io.KXmlParser
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpgTest {
    private val parser = XmltvParser { KXmlParser().apply { setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) } }
    private fun guide() = javaClass.classLoader!!.getResourceAsStream("guide.xml")!!

    @Test fun parsesTimesWithAndWithoutOffsets() {
        assertEquals(Instant.parse("2026-10-15T17:30:00Z"), XmltvParser.parseTime("20261015193000 +0200"))
        assertEquals(Instant.parse("2026-10-15T19:00:00Z"), XmltvParser.parseTime("20261015190000Z"))
        assertEquals(Instant.parse("2026-10-15T19:00:00Z"), XmltvParser.parseTime("20261015190000"))
        assertNull(XmltvParser.parseTime("garbage"))
    }

    @Test fun readsChannelsAndWantedProgrammesInWindow() {
        val channels = mutableListOf<XmltvParser.Channel>()
        val progs = mutableListOf<Programme>()
        val window = Instant.parse("2026-10-15T17:00:00Z")..Instant.parse("2026-10-15T23:00:00Z")
        parser.parse(guide(), window, setOf("n1.de", "as.de"), { channels += it }) { progs += it }
        assertEquals(listOf("Nordlicht 1", "Arena Sports"), channels.map { it.name })
        assertEquals("http://img/n1.png", channels[0].icon)
        // "Old Match" ends before the window, "Ignored" is for an unwanted channel.
        assertEquals(listOf("Wetter", "World Report", "Tennis"), progs.map { it.title })
        assertEquals("Das Wetter.", progs[0].description)
        assertNull(progs[1].description)
    }

    @Test fun indexFindsNowAndNext() {
        val progs = mutableListOf<Programme>()
        parser.parse(guide()) { progs += it }
        val epg = EpgIndex(progs)
        val at = Instant.parse("2026-10-15T18:07:00Z") // 20:07 in Berlin
        assertEquals("Wetter", epg.on("n1.de", at)!!.title)
        assertEquals("World Report", epg.next("n1.de", at)!!.title)
        assertEquals(0.82f, epg.on("n1.de", at)!!.progress(at), 0.01f)
        assertNull(epg.on("as.de", at))
        assertNull(epg.on(null, at))
        assertEquals(2, epg.range("n1.de", Instant.parse("2026-10-15T17:00:00Z"), Instant.parse("2026-10-15T20:00:00Z")).size)
    }
}
