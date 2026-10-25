package com.aras.client.ui.rdr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.R

/** پوسته‌ی RADAR Glass برای صفحه‌ی لیست: پس‌زمینه و رنگ‌ها را فراهم می‌کند. */
@Composable
fun RdrListShell(isDark: Boolean, content: @Composable () -> Unit) {
    val themeState = rememberRdrThemeState()
    RdrTheme(themeState, darkOverride = isDark) {
        RdrBackground { content() }
    }
}

/**
 * نوار بالای صفحه‌ی لیست کانفیگ‌ها: برگشت، عنوان (یا کادر جستجو) و همان کپسول ابزار صفحه‌ی اصلی.
 */
@Composable
fun RdrListTopBar(
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    toolbar: @Composable () -> Unit,
) {
    val c = LocalRdrColors.current
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(RdrDims.topBarHeight)
                    .padding(horizontal = RdrDims.topBarPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.rdr_back),
                    { if (showSearch) onSearchToggle(false) else onBack() },
                    size = RdrDims.menuButton,
                )
                Spacer(Modifier.width(10.dp))
                if (showSearch) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .glass(CircleShape, shadow = 8.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(stringResource(R.string.rdr_search_hint), color = c.textSecondary, fontSize = 15.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                                cursorBrush = SolidColor(c.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            PlainIcon(Icons.Rounded.Close, null, { onSearchQueryChange("") }, size = 30.dp)
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.rdr_configs),
                        color = c.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    toolbar()
                }
            }
        }
        if (isLoading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = RdrDims.topBarPadding),
                color = c.accent,
                trackColor = c.separator,
            )
        }
    }
}
