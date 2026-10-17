package com.lck07.lumen.ui.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lck07.lumen.BuildConfig
import com.lck07.lumen.Locales
import com.lck07.lumen.R
import com.lck07.lumen.container
import com.lck07.lumen.data.BufferSize
import com.lck07.lumen.data.CatalogRepository
import com.lck07.lumen.data.LiveFormat
import com.lck07.lumen.data.SettingsStore
import com.lck07.lumen.design.Icon
import com.lck07.lumen.design.TextEntryRow
import com.lck07.lumen.ui.profile.AvatarColours
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.sync.withLock
import com.lck07.lumen.design.ListRow
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenIcons
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.Txt
import com.lck07.lumen.ui.LocalScreenActive
import com.lck07.lumen.ui.TabChrome
import com.lck07.lumen.ui.home.hm
import com.lck07.lumen.work.GuideWorker
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class Page { Main, Account, Profiles, ProfileNew, ProfileEdit, ProfileLanguages, Language, Guide, GuideHour, Parental, Categories, Player, Audio, Subtitles, Buffer, Format, About, Licences }

/** A PIN step: what the PIN pad is asking for. */
private enum class PinStep { None, Unlock, New, Confirm, Disable }

/**
 * Settings: Account, Language, Guide refresh, Parental lock, Player and About, as a list next
 * to a large glyph (tvOS settings). Sub-pages open in place; Back returns to the list.
 */
@Composable
fun SettingsScreen(chrome: TabChrome, openProfiles: Boolean = false, onProfilesOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val c = context.container
    val res = context.resources
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState(initial = null)
    val info by c.account.info.collectAsState(initial = null)
    val guide by c.guide.state.collectAsState(initial = null)
    val guideRunning by remember { GuideWorker.running(context) }.collectAsState(initial = false)
    val unlocked by c.parental.unlocked.collectAsState()
    val profiles by c.profiles.profiles.collectAsState(initial = emptyList())
    val activeId by c.profiles.activeId.collectAsState(initial = 1)
    val untagged by remember { c.db.catalog().untaggedCount() }.collectAsState(initial = 0)
    var newName by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf(0) }
    var editName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    // Renames are written in the order they were typed.
    val renameLock = remember { kotlinx.coroutines.sync.Mutex() }
    val spare = remember { FocusRequester() }
    val stack = remember { mutableStateListOf(Page.Main) }
    val page = stack.last()
    var pin by remember { mutableStateOf(PinStep.None) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf("") }
    val s = settings ?: return

    var mainFocus by rememberSaveable { mutableStateOf("account") }
    var cameBack by remember { mutableStateOf(false) }
    fun open(p: Page) {
        if (stack.last() == Page.Main) mainFocus = when (p) {
            Page.Account -> "account"; Page.Profiles -> "profiles"; Page.Language -> "language"; Page.Guide -> "guide"; Page.Parental -> "parental"; Page.Player -> "player"; else -> "about"
        }
        stack.add(p)
    }
    fun back() { if (stack.size > 1) { stack.removeAt(stack.lastIndex); cameBack = true } }
    BackHandler(enabled = stack.size > 1 || pin != PinStep.None) {
        if (pin != PinStep.None) { pin = PinStep.None; pinError = null } else back()
    }
    LaunchedEffect(Unit) { chrome.contentAtTop = true }
    // "Manage Profiles" in the profile switcher lands here.
    LaunchedEffect(openProfiles) {
        if (openProfiles) { if (stack.last() != Page.Profiles) { mainFocus = "profiles"; stack.add(Page.Profiles) }; onProfilesOpened() }
    }

    val languageValue = when (Locales.chosen(context)) { "en" -> stringResource(R.string.language_english); "de" -> stringResource(R.string.language_german); else -> stringResource(R.string.language_system) }
    fun langName(tag: String?): String = when (tag) {
        null -> res.getString(R.string.lang_device)
        "orig" -> res.getString(R.string.lang_original)
        else -> Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    val hourText = "%02d:00".format(s.guideRefreshHour)

    Row(Modifier.fillMaxSize().testTag("settings")) {
        // The glyph on the left, as in tvOS Settings.
        Box(Modifier.width(380.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(170.dp)
                    .shadow(30.dp, RoundedCornerShape(40.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E))), RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(if (page == Page.Parental || page == Page.Categories) LumenIcons.Lock else LumenIcons.Gear, tint = Color(0xFF8E8E93), size = 85.dp) }
        }
        key(page, pin) {
            Column(Modifier.weight(1f).padding(top = 100.dp, end = LumenSpacing.SafeX)) {
                if (pin != PinStep.None) {
                    PinPad(
                        title = stringResource(
                            when (pin) { PinStep.New -> R.string.parental_new_pin; PinStep.Confirm -> R.string.parental_confirm_pin; else -> R.string.parental_enter_pin },
                        ),
                        error = pinError,
                    ) { entered ->
                        scope.launch {
                            when (pin) {
                                PinStep.Unlock -> if (c.parental.unlock(entered)) { pin = PinStep.None; pinError = null; if (page == Page.Main) open(Page.Parental) } else pinError = res.getString(R.string.parental_wrong_pin)
                                PinStep.Disable -> if (c.settings.checkPin(entered)) { c.settings.setPin(null); c.parental.lock(); pin = PinStep.None; pinError = null } else pinError = res.getString(R.string.parental_wrong_pin)
                                PinStep.New -> { newPin = entered; pin = PinStep.Confirm; pinError = null }
                                PinStep.Confirm -> if (entered == newPin) {
                                    c.settings.setPin(entered); c.parental.unlock(entered); pin = PinStep.None; pinError = null
                                    if (page != Page.Parental) open(Page.Parental)
                                } else { pin = PinStep.New; pinError = res.getString(R.string.parental_pin_mismatch) }
                                PinStep.None -> Unit
                            }
                        }
                    }
                    return@Column
                }
                val title = when (page) {
                    Page.Main -> R.string.tab_settings; Page.Account -> R.string.settings_account
                    Page.Profiles, Page.ProfileEdit -> R.string.settings_profiles; Page.ProfileNew -> R.string.profile_add; Page.ProfileLanguages -> R.string.content_languages; Page.Language -> R.string.settings_language
                    Page.Guide, Page.GuideHour -> R.string.settings_guide_refresh; Page.Parental, Page.Categories -> R.string.settings_parental_lock
                    Page.Player -> R.string.settings_player; Page.Audio -> R.string.player_audio_language; Page.Subtitles -> R.string.player_subtitle_language
                    Page.Buffer -> R.string.player_buffer; Page.Format -> R.string.player_live_format; Page.About -> R.string.settings_about; Page.Licences -> R.string.about_licences
                }
                Txt(stringResource(title), LumenType.Title2, Modifier.padding(bottom = 12.dp))
                val first = remember { FocusRequester() }
                val list = rememberLazyListState()
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)) {
                    fun row(k: String, title: String, value: String?, chevron: Boolean = true, selected: Boolean? = null, onClick: () -> Unit) = item(key = k) {
                        ListRow(
                            title, value, onClick,
                            trailingIcon = when { selected == true -> LumenIcons.Check; selected == false -> null; chevron -> LumenIcons.Chevron; else -> null },
                            selected = selected, focusRequester = if (k == firstKey(page, s, Locales.chosen(context), mainFocus, activeId, confirmDelete, profiles.firstOrNull { it.id == editId }?.contentLanguages.orEmpty())) first else null,
                            modifier = Modifier.testTag("setting:$k"),
                        )
                    }
                    fun text(k: String, t: String) = item(key = k) { Txt(t, LumenType.Caption1, Modifier.padding(horizontal = 17.dp, vertical = 6.dp), color = LumenColors.Label2, maxLines = 6) }
                    when (page) {
                        Page.Main -> {
                            row("account", res.getString(R.string.settings_account), info?.let { if (it.expired()) res.getString(R.string.expired_banner).substringBefore('.') else res.getString(R.string.account_active) }) { open(Page.Account) }
                            row("profiles", res.getString(R.string.settings_profiles), profiles.firstOrNull { it.id == activeId }?.name?.ifBlank { null } ?: res.getString(R.string.profile_default_name)) { open(Page.Profiles) }
                            row("language", res.getString(R.string.settings_language), languageValue) { open(Page.Language) }
                            row("guide", res.getString(R.string.settings_guide_refresh), res.getString(R.string.daily_at, hourText)) { open(Page.Guide) }
                            row("parental", res.getString(R.string.settings_parental_lock),
                                if (!s.parentalLockOn) res.getString(R.string.parental_off) else res.getQuantityString(R.plurals.parental_locked, s.lockedCategories.size, s.lockedCategories.size)) {
                                if (s.parentalLockOn && !unlocked) pin = PinStep.Unlock else open(Page.Parental)
                            }
                            row("player", res.getString(R.string.settings_player), listOf(res.getString(bufferName(s.buffer)), res.getString(if (s.liveFormat == LiveFormat.Ts) R.string.format_ts else R.string.format_hls)).joinToString(" · ")) { open(Page.Player) }
                            row("about", res.getString(R.string.settings_about), res.getString(R.string.about_version, BuildConfig.VERSION_NAME)) { open(Page.About) }
                        }
                        Page.Account -> {
                            row("server", res.getString(R.string.account_server), (c.account.state.value as? com.lck07.lumen.data.AccountState.SignedIn)?.account?.baseUrl, chevron = false) {}
                            row("user", res.getString(R.string.account_user), (c.account.state.value as? com.lck07.lumen.data.AccountState.SignedIn)?.account?.username, chevron = false) {}
                            row("status", res.getString(R.string.account_status), info?.let { i ->
                                listOfNotNull(
                                    i.status,
                                    i.expiresAt?.let { res.getString(R.string.account_expires, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it)) }
                                        ?: res.getString(R.string.account_no_expiry),
                                ).joinToString(" · ")
                            }, chevron = false) { scope.launch { c.account.refreshStatus() } }
                            info?.maxConnections?.let { max -> row("cons", res.getString(R.string.account_connections, info?.activeConnections ?: 0, max), null, chevron = false) {} }
                            row("reimport", res.getString(R.string.account_reimport), null) { c.session.reimport() }
                            row("signout", res.getString(R.string.action_sign_out), null, chevron = false) { scope.launch { c.session.signOut() } }
                        }
                        Page.Profiles -> {
                            text("hint", res.getString(R.string.profiles_hint))
                            profiles.forEach { p ->
                                row("p:${p.id}", p.name.ifBlank { res.getString(R.string.profile_default_name) }, if (p.id == activeId) res.getString(R.string.account_active) else null) {
                                    editId = p.id; editName = p.name; confirmDelete = false; open(Page.ProfileEdit)
                                }
                            }
                            row("padd", res.getString(R.string.profile_add), null) { newName = ""; open(Page.ProfileNew) }
                        }
                        Page.ProfileNew -> {
                            item(key = "pname") {
                                TextEntryRow(
                                    res.getString(R.string.profile_name), newName, { newName = it.take(24) },
                                    placeholder = res.getString(R.string.profile_name), imeAction = ImeAction.Done, rowRequester = first, tag = "profile:name",
                                )
                            }
                            item(key = "pcreate") {
                                ListRow(
                                    res.getString(R.string.profile_create), null,
                                    { scope.launch { c.profiles.create(newName, profiles.size); back() } },
                                    trailingIcon = null, enabled = newName.isNotBlank(), modifier = Modifier.testTag("setting:pcreate"),
                                )
                            }
                        }
                        Page.ProfileEdit -> {
                            val p = profiles.firstOrNull { it.id == editId }
                            if (p != null) {
                                item(key = "rname") {
                                    TextEntryRow(
                                        res.getString(R.string.profile_name), editName,
                                        { v -> editName = v.take(24); scope.launch { renameLock.withLock { c.profiles.rename(p.id, v.take(24)) } } },
                                        placeholder = res.getString(R.string.profile_default_name), imeAction = ImeAction.Done, rowRequester = if (confirmDelete) spare else first, tag = "profile:rename",
                                    )
                                }
                                item(key = "rcolour") { AvatarColours(p.avatar, { i -> scope.launch { c.profiles.setAvatar(p.id, i) } }) }
                                row("rlangs", res.getString(R.string.content_languages), res.getString(languagesName(p.contentLanguages))) { open(Page.ProfileLanguages) }
                                val name = p.name.ifBlank { res.getString(R.string.profile_default_name) }
                                when {
                                    confirmDelete -> {
                                        text("rconfirm", res.getString(R.string.profile_delete_confirm, name))
                                        row("rdelno", res.getString(R.string.action_cancel), null, chevron = false) { confirmDelete = false }
                                        row("rdelyes", res.getString(R.string.profile_delete_yes), null, chevron = false) {
                                            scope.launch { if (c.profiles.delete(p.id)) { confirmDelete = false; back() } }
                                        }
                                    }
                                    profiles.size > 1 -> row("rdelete", res.getString(R.string.profile_delete), null, chevron = false) { confirmDelete = true }
                                    else -> text("rlast", res.getString(R.string.profile_last_hint))
                                }
                            }
                        }
                        Page.ProfileLanguages -> {
                            val p = profiles.firstOrNull { it.id == editId }
                            if (p != null) {
                                listOf("", "de", "en", "de,en").forEach { v ->
                                    row("cl:$v", res.getString(languagesName(v)), null, selected = languagesName(p.contentLanguages) == languagesName(v)) {
                                        scope.launch { c.profiles.setContentLanguages(p.id, v.split(',').filter { it.isNotEmpty() }) }
                                    }
                                }
                                text("clhint", res.getString(R.string.content_lang_hint))
                                text("clcount", res.getQuantityString(R.plurals.untagged_titles, untagged, untagged))
                            }
                        }
                        Page.Language -> listOf(null to R.string.language_system, "en" to R.string.language_english, "de" to R.string.language_german).forEach { (tag, name) ->
                            row("lang:${tag ?: "system"}", res.getString(name), null, selected = Locales.chosen(context) == tag) {
                                scope.launch {
                                    c.settings.setLanguage(when (tag) { "en" -> com.lck07.lumen.data.AppLanguage.English; "de" -> com.lck07.lumen.data.AppLanguage.German; else -> com.lck07.lumen.data.AppLanguage.System })
                                    Locales.choose(context, tag)
                                    if (android.os.Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
                                }
                            }
                        }
                        Page.Guide -> {
                            row("hour", res.getString(R.string.settings_guide_refresh), res.getString(R.string.daily_at, hourText)) { open(Page.GuideHour) }
                            row("now", res.getString(R.string.refresh_now), when {
                                guideRunning -> res.getString(R.string.guide_updating)
                                guide != null -> res.getString(R.string.guide_updated, hm(guide!!.importedAt))
                                else -> res.getString(R.string.guide_never)
                            }, chevron = false) { GuideWorker.refreshNow(context) }
                        }
                        Page.GuideHour -> (0..23).forEach { h ->
                            row("h$h", "%02d:00".format(h), null, selected = h == s.guideRefreshHour) {
                                scope.launch { c.settings.setGuideHour(h); GuideWorker.scheduleDaily(context, h); back() }
                            }
                        }
                        Page.Parental -> {
                            if (!s.parentalLockOn) {
                                text("hint", res.getString(R.string.parental_locked_hint))
                                row("set", res.getString(R.string.parental_set_pin), null) { pin = PinStep.New }
                            } else {
                                row("cats", res.getString(R.string.settings_parental_lock), res.getQuantityString(R.plurals.parental_locked, s.lockedCategories.size, s.lockedCategories.size)) { open(Page.Categories) }
                                row("change", res.getString(R.string.parental_change_pin), null) { pin = PinStep.New }
                                row("off", res.getString(R.string.parental_turn_off), null, chevron = false) { pin = PinStep.Disable }
                                text("hint", res.getString(R.string.parental_locked_hint))
                            }
                        }
                        Page.Categories -> {
                            item(key = "cats") { CategoryLocks(s.lockedCategories) { k, locked -> scope.launch { c.settings.setCategoryLocked(k, locked) } } }
                        }
                        Page.Player -> {
                            row("audio", res.getString(R.string.player_audio_language), langName(s.audioLanguage)) { open(Page.Audio) }
                            row("subs", res.getString(R.string.player_subtitle_language), s.subtitleLanguage?.let(::langName) ?: res.getString(R.string.off)) { open(Page.Subtitles) }
                            row("buffer", res.getString(R.string.player_buffer), res.getString(bufferName(s.buffer))) { open(Page.Buffer) }
                            row("format", res.getString(R.string.player_live_format), res.getString(if (s.liveFormat == LiveFormat.Ts) R.string.format_ts else R.string.format_hls)) { open(Page.Format) }
                        }
                        Page.Audio -> listOf(null, "de", "en", "fr", "es", "it", "tr").forEach { tag ->
                            row("a:${tag ?: "device"}", langName(tag), null, selected = s.audioLanguage == tag) { scope.launch { c.settings.setAudioLanguage(tag); back() } }
                        }
                        Page.Subtitles -> listOf("off", "de", "en", "fr", "es", "it", "tr").forEach { tag ->
                            val v = tag.takeIf { it != "off" }
                            row("s:$tag", if (v == null) res.getString(R.string.off) else langName(v), null, selected = s.subtitleLanguage == v) { scope.launch { c.settings.setSubtitleLanguage(v); back() } }
                        }
                        Page.Buffer -> BufferSize.entries.forEach { b ->
                            row("b:${b.name}", res.getString(bufferName(b)), "${b.minMs / 1000}–${b.maxMs / 1000} s", selected = s.buffer == b) { scope.launch { c.settings.setBuffer(b); back() } }
                        }
                        Page.Format -> LiveFormat.entries.forEach { f ->
                            row("f:${f.name}", res.getString(if (f == LiveFormat.Ts) R.string.format_ts else R.string.format_hls), null, selected = s.liveFormat == f) { scope.launch { c.settings.setLiveFormat(f); back() } }
                        }
                        Page.About -> {
                            row("version", "Lumen", res.getString(R.string.about_version, BuildConfig.VERSION_NAME), chevron = false) {}
                            text("privacy", res.getString(R.string.about_privacy))
                            row("licences", res.getString(R.string.about_licences), null) { open(Page.Licences) }
                        }
                        Page.Licences -> LICENCES.forEachIndexed { i, (name, licence) ->
                            row("lic$i", name, licence, chevron = false) {}
                        }
                    }
                }
                val active = LocalScreenActive.current
                val firstK = firstKey(page, s, Locales.chosen(context), mainFocus, activeId, confirmDelete, profiles.firstOrNull { it.id == editId }?.contentLanguages.orEmpty())
                LaunchedEffect(page, active, firstK) {
                    // Sub-pages focus their first (or current) row; the list gets focus back when returning to it.
                    // After a PIN step the page's rows can change a moment later, so try for a few frames.
                    if (active && (page != Page.Main || cameBack)) {
                        for (i in 0 until 10) { awaitFrame(); if (runCatching { first.requestFocus() }.isSuccess) break }
                        cameBack = false
                    }
                }
            }
        }
    }
}

private fun firstKey(page: Page, s: com.lck07.lumen.data.Settings, lang: String?, main: String, activeProfile: Int, confirmDelete: Boolean, contentLanguages: String): String = when (page) {
    Page.Main -> main; Page.Account -> "status"
    Page.Profiles -> "p:$activeProfile"; Page.ProfileNew -> "pname"; Page.ProfileEdit -> if (confirmDelete) "rdelno" else "rname"
    Page.Language -> "lang:${lang ?: "system"}"
    Page.ProfileLanguages -> "cl:" + languagesKey(contentLanguages)
    Page.Guide -> "hour"; Page.GuideHour -> "h${s.guideRefreshHour}"; Page.Parental -> if (s.parentalLockOn) "cats" else "set"; Page.Categories -> ""
    Page.Player -> "audio"; Page.Audio -> "a:${s.audioLanguage ?: "device"}"; Page.Subtitles -> "s:${s.subtitleLanguage ?: "off"}"
    Page.Buffer -> "b:${s.buffer.name}"; Page.Format -> "f:${s.liveFormat.name}"; Page.About -> "version"; Page.Licences -> "lic0"
}

/** One of the offered choices ("", "de", "en", "de,en") for a stored value; anything else counts as all. */
private fun languagesKey(v: String) = v.split(',').filter { it.isNotBlank() }.sorted().joinToString(",").takeIf { it in setOf("de", "en", "de,en") }.orEmpty()

/** Label of a profile's comma-separated content languages ("" = all). */
private fun languagesName(v: String) = when (languagesKey(v)) {
    "de" -> R.string.content_lang_de
    "en" -> R.string.content_lang_en
    "de,en" -> R.string.content_lang_de_en
    else -> R.string.content_lang_all
}

private fun bufferName(b: BufferSize) = when (b) { BufferSize.Small -> R.string.buffer_small; BufferSize.Medium -> R.string.buffer_medium; BufferSize.Large -> R.string.buffer_large }

/** Third-party software shipped in the app. */
private val LICENCES = listOf(
    "Inter typeface" to "SIL Open Font License 1.1",
    "AndroidX, Jetpack Compose, Media3" to "Apache License 2.0",
    "FFmpeg (audio decoders, via nextlib)" to "LGPL 2.1+",
    "OkHttp, Okio" to "Apache License 2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "NanoHTTPD" to "BSD 3-Clause",
    "ZXing" to "Apache License 2.0",
)

/** Every category with a Locked/Unlocked switch. */
@Composable
private fun CategoryLocks(locked: Set<String>, onToggle: (String, Boolean) -> Unit) {
    val c = LocalContext.current.container
    val res = LocalContext.current.resources
    val types = listOf(
        CatalogRepository.TYPE_LIVE to R.string.tab_live, CatalogRepository.TYPE_MOVIE to R.string.tab_movies, CatalogRepository.TYPE_SERIES to R.string.tab_series,
    )
    val all by remember {
        kotlinx.coroutines.flow.combine(types.map { (type, _) -> c.db.catalog().categories(type) }) { arr -> arr.toList() }
    }.collectAsState(initial = null)
    val lists = all ?: return
    val first = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var firstSet = false
        types.forEachIndexed { i, (type, label) ->
            val cats = lists[i]
            if (cats.isNotEmpty()) Txt(res.getString(label), LumenType.Caption1Strong, Modifier.padding(start = 17.dp, top = 8.dp), color = LumenColors.Label2)
            cats.forEach { cat ->
                val k = SettingsStore.categoryKey(type, cat.id)
                val isLocked = k in locked
                val req = if (!firstSet) { firstSet = true; first } else null
                ListRow(cat.name, res.getString(if (isLocked) R.string.locked else R.string.unlocked), { onToggle(k, !isLocked) },
                    trailingIcon = if (isLocked) LumenIcons.Lock else null, selected = isLocked, focusRequester = req)
            }
        }
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
}
