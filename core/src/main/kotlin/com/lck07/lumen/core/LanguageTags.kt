package com.lck07.lumen.core

/**
 * Language from provider naming: `DE | Action`, `[EN] Title`, `GERMAN MOVIES`, `Action [GER]`.
 * Returns a lowercase ISO 639-1 code, or null when nothing is recognised.
 *
 * Categories are lenient: a known token must lead or trail the name (quality words like 4K are skipped).
 * Titles are strict: a short code must start the title and be followed by a separator (`|`, `:`, `-`),
 * or any code may sit in brackets at the start or end. Plain words in titles never tag.
 */
object LanguageTags {
    // Order matters only for readability; every code is matched as a whole token.
    private val codes: Map<String, String> = listOf(
        "de" to listOf("DE", "GER", "GERMAN", "GERMANY", "DEUTSCH", "DEUTSCHLAND", "AT", "CH"),
        "en" to listOf("EN", "ENG", "ENGLISH", "UK", "GB", "US", "USA", "AU", "CA", "NZ"),
        "fr" to listOf("FR", "FRA", "FRENCH", "FRANCE", "VOSTFR"),
        "es" to listOf("ES", "ESP", "SPANISH", "SPAIN"),
        "it" to listOf("IT", "ITA", "ITALIAN", "ITALY"),
        "tr" to listOf("TR", "TUR", "TURKISH", "TURKEY", "TURKIYE"),
        "ar" to listOf("AR", "ARA", "ARABIC", "ARAB"),
        "pl" to listOf("PL", "POL", "POLISH", "POLAND"),
        "nl" to listOf("NL", "NLD", "DUTCH", "NETHERLANDS"),
        "pt" to listOf("PT", "POR", "PORTUGUESE", "PORTUGAL", "BR", "BRAZIL"),
        "ru" to listOf("RU", "RUS", "RUSSIAN", "RUSSIA"),
        "el" to listOf("GR", "GRE", "GREEK", "GREECE"),
        "sv" to listOf("SE", "SWE", "SWEDISH", "SWEDEN"),
        "da" to listOf("DK", "DAN", "DANISH", "DENMARK"),
        "fi" to listOf("FI", "FIN", "FINNISH", "FINLAND"),
        "cs" to listOf("CZ", "CZE", "CZECH"),
        "hu" to listOf("HU", "HUN", "HUNGARIAN", "HUNGARY"),
        "ro" to listOf("RO", "ROM", "ROMANIAN", "ROMANIA"),
        "hr" to listOf("HR", "CROATIAN", "CROATIA"),
        "sr" to listOf("RS", "SRB", "SERBIAN", "SERBIA"),
        "bg" to listOf("BG", "BULGARIAN", "BULGARIA"),
        "uk" to listOf("UA", "UKR", "UKRAINIAN", "UKRAINE"),
        "sq" to listOf("AL", "ALB", "ALBANIAN", "ALBANIA"),
    ).flatMap { (code, names) -> names.map { it to code } }.toMap()

    private val quality = setOf("4K", "8K", "UHD", "FHD", "HD", "SD")
    private val separators = Regex("[^A-Z0-9]+")

    private val anyCode = codes.keys.joinToString("|")
    private val shortCode = codes.keys.filter { it.length <= 3 }.joinToString("|")
    private val qualityPrefix = "(?:(?:4K|8K|UHD|FHD|HD|SD)[\\s-]*)?"

    // "DE | x", "|DE| x", "4K-DE: x", "EN - x". Words like GERMAN need brackets in titles.
    private val titlePrefix = Regex("^\\s*\\|?\\s*$qualityPrefix($shortCode)\\s*[|:\\-\\u2013\\u2014]")
    // "[DE] x", "(EN) x", "|DE| x"
    private val titleBracketPrefix = Regex("^\\s*[\\[(|]\\s*$qualityPrefix($anyCode)\\s*[\\])|]")
    // "x (DE)", "x [GER]"
    private val titleBracketSuffix = Regex("[\\[(]\\s*($anyCode)\\s*[\\])]\\s*$")

    /** Category tag first, title tag overrides it. */
    fun tag(categoryName: String?, title: String?): String? =
        titleTag(title) ?: fromText(categoryName)

    /** Category-style lookup: the first or last token of [text] (quality words skipped). Pass category names, not titles. */
    fun fromText(text: String?): String? {
        val tokens = text?.uppercase()?.split(separators)?.filter { it.isNotEmpty() && it !in quality } ?: return null
        return tokens.firstOrNull()?.let { codes[it] } ?: tokens.lastOrNull()?.let { codes[it] }
    }

    private fun titleTag(title: String?): String? {
        val t = title?.uppercase() ?: return null
        val match = titlePrefix.find(t) ?: titleBracketPrefix.find(t) ?: titleBracketSuffix.find(t) ?: return null
        return codes[match.groupValues[1]]
    }
}
