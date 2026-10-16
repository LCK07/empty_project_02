package com.lck07.lumen.ui.setup

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lck07.lumen.R
import com.lck07.lumen.core.model.XtreamAccount
import com.lck07.lumen.data.explain
import com.lck07.lumen.design.Glass
import com.lck07.lumen.design.LumenButton
import com.lck07.lumen.design.LumenColors
import com.lck07.lumen.design.LumenRadii
import com.lck07.lumen.design.LumenSpacing
import com.lck07.lumen.design.LumenType
import com.lck07.lumen.design.TextEntryRow
import com.lck07.lumen.design.Txt
import com.lck07.lumen.design.generatedArt
import com.lck07.lumen.setup.PhoneForm
import com.lck07.lumen.setup.PhonePageText
import com.lck07.lumen.setup.PhoneSetupServer
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Add Account: server, username and password typed with the remote, or sent from a phone by
 * scanning the QR code. Signing in checks the login and explains what to fix when it fails.
 */
@Composable
fun SetupScreen(signIn: suspend (XtreamAccount) -> Unit) {
    val context = LocalContext.current
    val res = context.resources
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstRow = remember { FocusRequester() }
    val userRow = remember { FocusRequester() }
    val passwordRow = remember { FocusRequester() }
    val signInButton = remember { FocusRequester() }

    fun submit() {
        if (busy) return
        if (server.isBlank() || username.isBlank() || password.isEmpty()) { error = res.getString(R.string.setup_missing); return }
        val account = XtreamAccount(server, username.trim(), password)
        if (account.baseUrl.toHttpUrlOrNull() == null) { error = res.getString(R.string.error_bad_address); return }
        busy = true; error = null
        scope.launch {
            try { signIn(account) } catch (e: Exception) { error = res.explain(e) } finally { busy = false }
        }
    }

    // The phone form: a one-time local web server, shown as a QR code.
    val text = PhonePageText(
        title = stringResource(R.string.setup_title), subtitle = stringResource(R.string.setup_subtitle),
        server = stringResource(R.string.setup_server), serverHint = stringResource(R.string.setup_server_hint),
        username = stringResource(R.string.setup_username), password = stringResource(R.string.setup_password),
        send = stringResource(R.string.phone_send), sent = stringResource(R.string.phone_sent),
    )
    var qr by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var phoneForm by remember { mutableStateOf<PhoneForm?>(null) }
    DisposableEffect(Unit) {
        val ip = PhoneSetupServer.localIp(context)
        val srv = PhoneSetupServer(text) { form -> phoneForm = form }
        if (ip != null) runCatching {
            srv.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            val url = srv.url(ip)
            qr = url to PhoneSetupServer.qr(url)
        }
        onDispose { runCatching { srv.stop() } }
    }
    LaunchedEffect(phoneForm) {
        val f = phoneForm ?: return@LaunchedEffect
        server = f.server; username = f.username; password = f.password
        notice = res.getString(R.string.setup_qr_received)
        submit()
    }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { firstRow.requestFocus() } }

    Box(Modifier.fillMaxSize().background(LumenColors.Background).testTag("setup")) {
        Box(Modifier.fillMaxSize().generatedArt("Lumen", 3).background(LumenColors.Scrim.copy(alpha = 0.72f)))
        Row(
            Modifier.fillMaxSize().padding(horizontal = LumenSpacing.SafeX, vertical = LumenSpacing.SafeY),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(stringResource(R.string.setup_title), LumenType.Title2)
                Txt(stringResource(R.string.setup_subtitle), LumenType.Body, color = LumenColors.Label2, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                TextEntryRow(
                    stringResource(R.string.setup_server), server, { server = it },
                    placeholder = stringResource(R.string.setup_server_hint), keyboardType = KeyboardType.Uri,
                    rowRequester = firstRow, tag = "field:server", onImeAction = { runCatching { userRow.requestFocus() } },
                )
                TextEntryRow(
                    stringResource(R.string.setup_username), username, { username = it }, keyboardType = KeyboardType.Email,
                    rowRequester = userRow, tag = "field:username", onImeAction = { runCatching { passwordRow.requestFocus() } },
                )
                TextEntryRow(
                    stringResource(R.string.setup_password), password, { password = it }, secret = true,
                    imeAction = ImeAction.Done, onImeAction = { runCatching { signInButton.requestFocus() } }, tag = "field:password",
                    rowRequester = passwordRow,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LumenButton(
                        stringResource(if (busy) R.string.setup_signing_in else R.string.action_sign_in),
                        ::submit, focusRequester = signInButton, enabled = !busy,
                    )
                }
                val message = error ?: notice
                if (message != null) Txt(
                    message, LumenType.Callout, color = if (error != null) LumenColors.Live else LumenColors.Label2, maxLines = 3,
                    modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Glass(LumenRadii.Banner, Modifier.width(300.dp), strong = true) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt(stringResource(R.string.setup_qr_title), LumenType.Headline)
                    val q = qr
                    if (q != null) {
                        Image(
                            q.second.asImageBitmap(), contentDescription = stringResource(R.string.setup_qr_title),
                            modifier = Modifier.size(170.dp).clip(LumenRadii.Cell),
                        )
                        Txt(stringResource(R.string.setup_qr_body), LumenType.Caption1, color = LumenColors.Label2, maxLines = 4)
                        Txt(q.first.substringBefore("/s/"), LumenType.Caption2, color = LumenColors.Label3)
                    } else {
                        Txt(stringResource(R.string.setup_qr_unavailable), LumenType.Caption1, color = LumenColors.Label2, maxLines = 4)
                    }
                }
            }
        }
    }
}
