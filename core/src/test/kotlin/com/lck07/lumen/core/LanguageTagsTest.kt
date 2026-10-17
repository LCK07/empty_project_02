package com.lck07.lumen.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageTagsTest {
    @Test fun categoryNames() {
        val expected = mapOf(
            "DE | Action" to "de",
            "DE - Action" to "de",
            "DE: Action" to "de",
            "|DE| Action" to "de",
            "[DE] Action" to "de",
            "(DE)" to "de",
            "Action [GER]" to "de",
            "GERMANY" to "de",
            "GERMAN MOVIES" to "de",
            "DEUTSCH" to "de",
            "Deutsch Filme" to "de",
            "AT | Serien" to "de",
            "Movies DE" to "de",
            "Action [GERMAN]" to "de",
            "DE | Documentaries" to "de",
            "4K-DE | Movies" to "de",
            "DE 4K" to "de",
            "EN - Netflix" to "en",
            "EN| 4K" to "en",
            "UK | Drama" to "en",
            "US: Comedy" to "en",
            "ENGLISH" to "en",
            "Movies English" to "en",
            "CA | Kids" to "en",
            "GB Drama" to "en",
            "FR | Films" to "fr",
            "FRANCE" to "fr",
            "VOSTFR" to "fr",
            "TR | Yerli" to "tr",
            "AR | Movies" to "ar",
            "NL" to "nl",
            "PL" to "pl",
            "ES" to "es",
            "IT" to "it",
            "IT | Serie" to "it",
            "PT | Filmes" to "pt",
            "RU | Cinema" to "ru",
            "MULTI" to null,
            "MULTI | Sports" to null,
            "BE | Movies" to null,
            "Action" to null,
            "4K UHD" to null,
            "Sports" to null,
            "Netflix" to null,
            "Top Movies 2025" to null,
            "Die Hard Collection" to null,
            "" to null,
        )
        expected.forEach { (name, code) -> assertEquals(code, LanguageTags.fromText(name), "category '$name'") }
        assertNull(LanguageTags.fromText(null))
    }

    @Test fun titlesOnlyTagWithSeparatorOrBrackets() {
        val expected = mapOf(
            "DE - Der Pate" to "de",
            "[EN] Inception" to "en",
            "Inception (EN)" to "en",
            "FR: Amelie" to "fr",
            "|DE| Action Film" to "de",
            "EN| 4K Nature" to "en",
            "4K-DE | Movies" to "de",
            "It Follows" to null,
            "Die Hard" to null,
            "Des Teufels Bad" to null,
            "Es war einmal" to null,
            "Action" to null,
            "4K UHD" to null,
            "MULTI | Sports" to null,
            "Germany Calling" to null,
            "English Lessons" to null,
            "The IT Crowd" to null,
            "Top Gun" to null,
        )
        expected.forEach { (title, code) -> assertEquals(code, LanguageTags.tag(null, title), "title '$title'") }
    }

    @Test fun titleOverridesCategory() {
        assertEquals("en", LanguageTags.tag("DE | Action", "EN - Title"))
        assertEquals("de", LanguageTags.tag("DE | Action", "Some Title"))
        assertEquals("de", LanguageTags.tag("EN - Netflix", "[DE] Dark"))
        assertEquals("fr", LanguageTags.tag("FR | Films", "Plain Title"))
        assertNull(LanguageTags.tag("Action", "It Follows"))
        assertNull(LanguageTags.tag(null, null))
    }
}
