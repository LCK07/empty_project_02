package com.lck07.lumen.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.data.ImportProgress
import com.lck07.lumen.data.ImportUi
import com.lck07.lumen.data.explain
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.LumenType.tabular
import com.lck07.lumen.design.Spinner
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.generatedArt
import kotlinx.coroutines.android.awaitFrame

/** The first catalogue import, with visible progress; errors offer Try Again and Sign Out. */
@Composable
fun ImportScreen(state: ImportUi, onRetry: () -> Unit, onSignOut: () -> Unit) {
    val res = LocalContext.current.resources
    val retry = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(LumenColors.Background).testTag("import")) {
        Box(Modifier.fillMaxSize().generatedArt("Lumen", 3).background(LumenColors.Scrim.copy(alpha = 0.72f)))
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt(stringResource(R.string.import_title), LumenType.Title2, align = TextAlign.Center)
            when (state) {
                is ImportUi.Failed -> {
                    Txt(res.explain(state.error), LumenType.Body, color = LumenColors.Label2, maxLines = 4, align = TextAlign.Center)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LumenButton(stringResource(R.string.action_retry), onRetry, focusRequester = retry)
                        LumenButton(stringResource(R.string.action_sign_out), onSignOut)
                    }
                    LaunchedEffect(Unit) { awaitFrame(); runCatching { retry.requestFocus() } }
                }
                else -> {
                    val p = (state as? ImportUi.Running)?.progress ?: ImportProgress(ImportProgress.Step.Categories)
                    Spinner(Modifier.padding(vertical = 8.dp))
                    Txt(
                        stringResource(
                            when (p.step) {
                                ImportProgress.Step.Categories -> R.string.import_categories
                                ImportProgress.Step.Channels -> R.string.import_channels
                                ImportProgress.Step.Movies -> R.string.import_movies
                                else -> R.string.import_series
                            },
                        ),
                        LumenType.Callout,
                    )
                    Txt(stringResource(R.string.import_counts, p.channels, p.movies, p.series), LumenType.Caption1.tabular(), color = LumenColors.Label2)
                    Txt(stringResource(R.string.import_guide_later), LumenType.Caption1, color = LumenColors.Label3)
                }
            }
        }
    }
}
