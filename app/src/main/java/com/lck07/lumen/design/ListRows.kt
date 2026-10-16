package com.lck07.lumen.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame

/**
 * A settings-style row: title on the left, value and a chevron on the right. Focused it turns
 * white with black text and grows slightly.
 */
@Composable
fun ListRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = LumenIcons.Chevron,
    enabled: Boolean = true,
    selected: Boolean? = null,
    focusRequester: FocusRequester? = null,
    onKey: ((Key) -> Boolean)? = null,
    state: TvState = rememberTvState(),
) {
    val bg by animateColorAsState(if (state.focused) LumenColors.Focused else LumenColors.FillRow, tween(160), label = "row")
    val fg = if (state.focused) LumenColors.OnFocused else if (enabled) LumenColors.Label else LumenColors.Label3
    val fg2 = if (state.focused) LumenColors.OnFocused2 else LumenColors.Label2
    Row(
        modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .controlFocusEffect(state, LumenRadii.Row, LumenMotion.RowFocusScale, shadow = 10.dp)
            .background(bg, LumenRadii.Row)
            .tvClickable(state, enabled, selected = selected, onKey = onKey, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Txt(title, LumenType.Body, Modifier.weight(1f), color = fg)
        if (value != null) Txt(value, LumenType.Body, Modifier.widthIn(max = 300.dp), color = fg2, align = TextAlign.End)
        if (trailingIcon != null) Icon(trailingIcon, tint = fg2, size = 13.dp)
    }
}

/**
 * A text field for the remote: a row that shows the value; Select opens the on-screen keyboard
 * for it, and the keyboard's action key (Next / Done) closes it again.
 */
@Composable
fun TextEntryRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    /** Called after the keyboard's action key closed the field; by default focus returns to the row. */
    onImeAction: (() -> Unit)? = null,
    rowRequester: FocusRequester = remember { FocusRequester() },
    tag: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    var viaIme by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    var closing by remember { mutableStateOf(false) }
    // Hide the keyboard first and remove the field a moment later: tearing both down in the same
    // frame can leave some TV keyboards (Gboard) attached and swallowing the D-pad.
    fun close(ime: Boolean) {
        if (closing || !editing) return
        viaIme = ime
        keyboard?.hide()
        view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
        closing = true
    }
    LaunchedEffect(closing) {
        if (closing) { delay(120); editing = false; closing = false }
    }
    val shown = when {
        value.isEmpty() -> placeholder
        secret -> "•".repeat(value.length.coerceAtMost(16))
        else -> value
    }
    Box(modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        if (!editing) {
            ListRow(title, shown, { editing = true }, focusRequester = rowRequester, trailingIcon = null)
        } else {
            Row(
                Modifier.fillMaxWidth().background(LumenColors.Focused, LumenRadii.Row).heightIn(min = 44.dp).padding(horizontal = 17.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Txt(title, LumenType.Body, color = LumenColors.OnFocused)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LumenType.Body.copy(color = LumenColors.OnFocused, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(LumenColors.Accent),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onAny = { close(ime = true) }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(fieldRequester)
                        .onPreviewKeyEvent { e ->
                            // Back closes the keyboard and returns to the row instead of leaving the screen.
                            if (e.key == Key.Back && e.type == KeyEventType.KeyUp) { close(ime = false); true } else false
                        }
                        .onFocusChanged {
                            // The first callback reports "not focused" before the field takes focus.
                            if (it.isFocused) fieldFocused = true
                            else if (fieldFocused && editing) { fieldFocused = false; close(ime = false) }
                        },
                )
            }
            LaunchedEffect(Unit) { awaitFrame(); fieldRequester.requestFocus(); keyboard?.show() }
        }
    }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) opened = true
        else if (opened) {
            awaitFrame()
            // The keyboard's Next/Done moves on (the caller decides where); Back stays on the row.
            if (viaIme && onImeAction != null) { viaIme = false; onImeAction() } else { viaIme = false; runCatching { rowRequester.requestFocus() } }
        }
    }
}
