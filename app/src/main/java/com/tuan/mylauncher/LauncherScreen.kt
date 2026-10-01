package com.tuan.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Chữ trắng có bóng mờ để đọc rõ trên mọi hình nền
private val shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f)
private val textOnWallpaper = TextStyle(color = Color.White, shadow = shadow)

/** Một dòng trong danh sách: tiêu đề chữ cái hoặc một ứng dụng. */
private sealed class ListRow(val key: String) {
    class Header(val letter: String) : ListRow("h_$letter")
    class App(val app: AppInfo, val isFavorite: Boolean, section: String) :
        ListRow("${section}_${app.key}")
}

private const val FAVORITE_MARK = "★"
private const val TOP_ITEMS = 2 // đồng hồ + ô tìm kiếm nằm trước danh sách

@Composable
fun LauncherScreen(repo: AppRepository, homeSignal: Int) {
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favoriteKeys by remember { mutableStateOf(repo.favorites()) }
    var query by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Tải lại danh sách mỗi khi quay về màn hình chính (bắt được app mới cài / vừa gỡ)
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            apps = repo.loadApps()
        }
    }

    fun resetView() {
        query = ""
        focusManager.clearFocus()
    }

    // Bấm Home khi đang ở launcher: xoá tìm kiếm, cuộn lên đầu
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            resetView()
            listState.animateScrollToItem(0)
        }
    }
    BackHandler(enabled = query.isNotEmpty()) { resetView() }

    // ----- Dữ liệu cho danh sách -----
    val favorites = remember(apps, favoriteKeys) {
        favoriteKeys.mapNotNull { k -> apps.find { it.key == k } }
    }
    val filtered = remember(apps, query) {
        val q = query.trim().simplify()
        if (q.isEmpty()) apps else apps.filter { it.searchName.contains(q) }
    }
    val rows = remember(filtered, favorites, query) {
        buildList {
            if (query.isBlank() && favorites.isNotEmpty()) {
                add(ListRow.Header(FAVORITE_MARK))
                favorites.forEach { add(ListRow.App(it, true, "fav")) }
            }
            filtered.groupBy { it.letter }
                .toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
                .forEach { (letter, list) ->
                    add(ListRow.Header(letter))
                    list.forEach { add(ListRow.App(it, it.key in favoriteKeys, "all")) }
                }
        }
    }
    val letterIndex = remember(rows) {
        rows.withIndex()
            .filter { it.value is ListRow.Header }
            .associate { (it.value as ListRow.Header).letter to it.index }
    }

    fun open(app: AppInfo) {
        repo.launch(app)
        resetView()
    }

    fun toggleFavorite(app: AppInfo) {
        favoriteKeys = if (app.key in favoriteKeys) favoriteKeys - app.key else favoriteKeys + app.key
        repo.saveFavorites(favoriteKeys)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0x88000000))))
            .systemBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 32.dp),
        ) {
            item(key = "clock") { ClockHeader(repo) }
            item(key = "search") {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onGo = { filtered.firstOrNull()?.let { open(it) } },
                )
            }
            if (rows.isEmpty() && query.isNotBlank()) {
                item(key = "empty") {
                    Text(
                        "Không có ứng dụng nào tên \"$query\"",
                        style = textOnWallpaper.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f)),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ListRow.Header -> Text(
                        text = if (row.letter == FAVORITE_MARK) "Yêu thích" else row.letter,
                        style = textOnWallpaper.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                    )
                    is ListRow.App -> AppRow(
                        app = row.app,
                        isFavorite = row.isFavorite,
                        onOpen = { open(row.app) },
                        onToggleFavorite = { toggleFavorite(row.app) },
                        onInfo = { repo.openAppInfo(row.app) },
                        onUninstall = { repo.uninstall(row.app) },
                    )
                }
            }
            item(key = "bottom_space") { Spacer(Modifier.size(48.dp)) }
        }

        AlphabetBar(
            letters = letterIndex.keys.toList(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            onSelect = { letter ->
                letterIndex[letter]?.let { idx ->
                    scope.launch { listState.scrollToItem(idx + TOP_ITEMS) }
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Đồng hồ lớn + ngày + pin
// ---------------------------------------------------------------------------
@Composable
private fun ClockHeader(repo: AppRepository) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableIntStateOf(-1) }

    // Cập nhật đúng lúc sang phút mới
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    // Theo dõi % pin
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) battery = level * 100 / scale
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val vi = remember { Locale("vi") }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'tháng' M", vi))
        .replaceFirstChar { it.titlecase(vi) }

    Column(Modifier.padding(start = 24.dp, top = 56.dp, bottom = 28.dp)) {
        Text(
            text = time,
            style = textOnWallpaper.copy(fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
            modifier = Modifier.clickable { repo.openSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        )
        Text(
            text = if (battery >= 0) "$date   ·   $battery%" else date,
            style = textOnWallpaper.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f)),
            modifier = Modifier.clickable {
                repo.openSafely(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Ô tìm kiếm ứng dụng
// ---------------------------------------------------------------------------
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (query.isEmpty()) {
            Text("Tìm ứng dụng", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Một dòng ứng dụng: chạm để mở, nhấn giữ để hiện menu
// ---------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = app.label,
                style = textOnWallpaper.copy(fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Bỏ khỏi yêu thích" else "Thêm vào yêu thích") },
                onClick = { menuOpen = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text("Thông tin ứng dụng") },
                onClick = { menuOpen = false; onInfo() },
            )
            DropdownMenuItem(
                text = { Text("Gỡ cài đặt") },
                onClick = { menuOpen = false; onUninstall() },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Thanh chữ cái A–Z bên phải: chạm hoặc vuốt để nhảy nhanh
// ---------------------------------------------------------------------------
@Composable
private fun AlphabetBar(
    letters: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var heightPx by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentLetters by rememberUpdatedState(letters)

    fun pick(y: Float) {
        val list = currentLetters
        if (list.isEmpty()) return
        val i = ((y / heightPx) * list.size).toInt().coerceIn(0, list.lastIndex)
        val letter = list[i]
        if (letter != active) {
            active = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnSelect(letter)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .width(24.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ -> pick(change.position.y) }
            }
            .pointerInput(Unit) {
                detectTapGestures { pick(it.y); active = null }
            },
    ) {
        letters.forEach { letter ->
            val isActive = letter == active
            Text(
                text = letter,
                style = textOnWallpaper.copy(
                    fontSize = if (isActive) 16.sp else 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.padding(vertical = 1.5.dp),
            )
        }
    }
}
