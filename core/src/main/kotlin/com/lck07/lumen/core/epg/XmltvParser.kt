package com.lck07.lumen.core.epg

import com.lck07.lumen.core.model.Programme
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Streams an XMLTV guide (often 50-200 MB) without loading it into memory:
 * each programme inside [window] for a wanted channel goes to [onProgramme].
 * The parser comes from the platform (Android) or kxml2 (JVM tests).
 */
class XmltvParser(private val newParser: () -> XmlPullParser) {

    data class Channel(val id: String, val name: String?, val icon: String?)

    fun parse(
        input: InputStream,
        window: ClosedRange<Instant>? = null,
        wanted: Set<String>? = null,
        onChannel: (Channel) -> Unit = {},
        onProgramme: (Programme) -> Unit,
    ) {
        val p = newParser()
        p.setInput(input, null)
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (p.name) {
                "channel" -> readChannel(p)?.let(onChannel)
                "programme" -> readProgramme(p, window, wanted)?.let(onProgramme)
            }
            event = p.next()
        }
    }

    private fun readChannel(p: XmlPullParser): Channel? {
        val id = p.getAttributeValue(null, "id") ?: return null
        var name: String? = null
        var icon: String? = null
        forChildren(p, "channel") { tag ->
            when (tag) {
                "display-name" -> { val t = p.nextText().trim(); if (name == null && t.isNotEmpty()) name = t }
                "icon" -> icon = p.getAttributeValue(null, "src")
            }
        }
        return Channel(id, name, icon)
    }

    private fun readProgramme(p: XmlPullParser, window: ClosedRange<Instant>?, wanted: Set<String>?): Programme? {
        val channel = p.getAttributeValue(null, "channel")
        val start = parseTime(p.getAttributeValue(null, "start"))
        val stop = parseTime(p.getAttributeValue(null, "stop"))
        // A programme that ends exactly when the window opens does not overlap it.
        if (channel == null || start == null || stop == null ||
            (wanted != null && channel !in wanted) ||
            (window != null && (!stop.isAfter(window.start) || start.isAfter(window.endInclusive)))
        ) { skipElement(p); return null }
        var title: String? = null
        var desc: String? = null
        forChildren(p, "programme") { tag ->
            when (tag) {
                "title" -> { val t = p.nextText().trim(); if (title == null && t.isNotEmpty()) title = t }
                "desc" -> { val t = p.nextText().trim(); if (desc == null && t.isNotEmpty()) desc = t }
            }
        }
        return Programme(channel, start, stop, title ?: "", desc)
    }

    /** Calls [onTag] for each direct child start tag until [parent] closes. */
    private inline fun forChildren(p: XmlPullParser, parent: String, onTag: (String) -> Unit) {
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) return
            if (e == XmlPullParser.END_TAG && p.depth == depth && p.name == parent) return
            if (e == XmlPullParser.START_TAG && p.depth == depth + 1) {
                val before = p.depth
                onTag(p.name)
                // nextText() leaves us on the END_TAG; anything else, skip to the end of the child.
                if (p.eventType == XmlPullParser.START_TAG && p.depth == before) skipElement(p)
            }
        }
    }

    private fun skipElement(p: XmlPullParser) {
        if (p.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        /** XMLTV times: "20261015200000 +0200", "20261015200000Z", or without an offset (UTC). */
        fun parseTime(raw: String?): Instant? {
            val s = raw?.trim() ?: return null
            if (s.length < 14) return null
            return try {
                val local = LocalDateTime.parse(s.substring(0, 14), LOCAL)
                val rest = s.substring(14).trim()
                val offset = when {
                    rest.isEmpty() || rest == "Z" -> ZoneOffset.UTC
                    else -> ZoneOffset.of(rest.let { if (it.length == 5) it.substring(0, 3) + ":" + it.substring(3) else it })
                }
                local.toInstant(offset)
            } catch (_: Exception) {
                null
            }
        }
    }
}
