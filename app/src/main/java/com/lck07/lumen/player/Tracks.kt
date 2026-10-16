package com.lck07.lumen.player

import android.content.res.Resources
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.lck07.lumen.R
import java.util.Locale

/** One choice in the Audio or Subtitles menu; [group] null means "Off". */
data class TrackChoice(val label: String, val selected: Boolean, val group: Tracks.Group?, val index: Int)

private fun languageName(lang: String?): String? {
    if (lang.isNullOrBlank() || lang == C.LANGUAGE_UNDETERMINED || lang == "und") return null
    val name = Locale.forLanguageTag(lang).getDisplayLanguage(Locale.getDefault())
    return name.takeIf { it.isNotBlank() && it != lang }?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: lang.uppercase()
}

fun trackChoices(res: Resources, tracks: Tracks, type: Int, disabled: Boolean): List<TrackChoice> {
    val out = ArrayList<TrackChoice>()
    var n = 0
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            n++
            val f = g.getTrackFormat(i)
            val extra = when (f.channelCount) { 1 -> null; 2 -> null; 6 -> "5.1"; 8 -> "7.1"; else -> null }
            val base = f.label ?: languageName(f.language) ?: res.getString(R.string.track_unknown, n)
            val label = listOfNotNull(base, extra, f.codecs?.let { codecName(f.sampleMimeType) }).distinct().joinToString(" · ")
            out += TrackChoice(label, !disabled && g.isTrackSelected(i), g, i)
        }
    }
    if (type == C.TRACK_TYPE_TEXT) out.add(0, TrackChoice(res.getString(R.string.off), disabled || out.none { it.selected }, null, -1))
    return out
}

private fun codecName(mime: String?): String? = when (mime) {
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3", "audio/eac3-joc" -> "Dolby Digital Plus"
    "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
    "audio/true-hd" -> "TrueHD"
    else -> null
}

fun selectTrack(player: Player, type: Int, choice: TrackChoice) {
    val b = player.trackSelectionParameters.buildUpon()
    if (choice.group == null) {
        b.setTrackTypeDisabled(type, true)
    } else {
        b.setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .addOverride(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index))
    }
    player.trackSelectionParameters = b.build()
}

/** The selected track's short name for the button ("German", "Off"). */
fun selectedLabel(choices: List<TrackChoice>): String? = choices.firstOrNull { it.selected }?.label?.substringBefore(" · ")
