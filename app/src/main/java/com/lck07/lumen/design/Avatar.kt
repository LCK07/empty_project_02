package com.lck07.lumen.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lck07.lumen.R
import com.lck07.lumen.data.db.ProfileEntity

/** The name to show: an empty stored name means the localised default. */
@Composable
fun ProfileEntity.displayName(): String = name.ifBlank { stringResource(R.string.profile_default_name) }

/** The profile's initial in a coloured circle. */
@Composable
fun Avatar(name: String, colour: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(modifier.size(size).background(LumenColors.avatar(colour), CircleShape), contentAlignment = Alignment.Center) {
        Txt(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            LumenType.Caption1Strong.copy(fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.Bold),
        )
    }
}
