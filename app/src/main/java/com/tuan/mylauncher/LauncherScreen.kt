package com.tuan.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

// Chữ trắng có bóng mờ để đọc rõ trên mọi hình nền
private val shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f)
private val textOnWallpaper = TextStyle(color = Color.White, shadow = shadow)

private const val MAX_FAVORITES = 7

/** Sắp xếp chữ cái: A–Z trước, "#" (số, ký hiệu) sau cùng. */
private val letterOrder = compareBy<String> { it == "#" }.thenBy { it }

// ===========================================================================
// Khung chính: màn hình chính + ngăn "Tất cả ứng dụng" trượt lên
// ===========================================================================
@Composable
fun LauncherScreen(repo: AppRepository, homeSignal: Int) {
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favoriteKeys by remember { mutableStateOf(repo.favorites()) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // Chữ cái đang "xem nhanh" trên màn hình chính (kiểu Niagara) và vị trí ngón tay
    var peekLetter by remember { mutableStateOf<String?>(null) }
    var peekY by remember { mutableFloatStateOf(0f) }

    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            apps = repo.loadApps()
        }
    }

    fun closeDrawer() {
        drawerOpen = false
        query = ""
        focusManager.clearFocus()
    }

    fun resetHome() {
        closeDrawer()
        peekLetter = null
    }

    LaunchedEffect(homeSignal) { if (homeSignal > 0) resetHome() }
    BackHandler(enabled = drawerOpen || peekLetter != null) { resetHome() }

    val favorites = remember(apps, favoriteKeys) {
        favoriteKeys.mapNotNull { k -> apps.find { it.key == k } }
    }
    val allLetters = remember(apps) {
        apps.map { it.letter }.distinct().sortedWith(letterOrder)
    }
    val peekApps = remember(apps, peekLetter) {
        val l = peekLetter
        if (l == null) emptyList() else apps.filter { it.letter == l }
    }

    fun open(app: AppInfo) {
        repo.launch(app)
        resetHome()
    }

    fun toggleFavorite(app: AppInfo) {
        if (app.key in favoriteKeys) {
            favoriteKeys = favoriteKeys - app.key
        } else {
            if (favoriteKeys.size >= MAX_FAVORITES) {
                repo.toast("Tối đa $MAX_FAVORITES ứng dụng yêu thích. Hãy bỏ bớt một ứng dụng trước.")
                return
            }
            favoriteKeys = favoriteKeys + app.key
            repo.toast("Đã thêm ${app.label} vào màn hình chính")
        }
        repo.saveFavorites(favoriteKeys)
    }

    val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            modifier = Modifier.graphicsLayer { alpha = homeAlpha },
            repo = repo,
            favorites = favorites,
            peekLetter = peekLetter,
            peekApps = peekApps,
            peekY = peekY,
            onDismissPeek = { peekLetter = null },
            onOpen = ::open,
            onToggleFavorite = ::toggleFavorite,
            onOpenDrawer = {
                peekLetter = null
                drawerOpen = true
            },
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            AppDrawer(
                repo = repo,
                apps = apps,
                favoriteKeys = favoriteKeys,
                query = query,
                onQueryChange = { query = it },
                onOpen = ::open,
                onToggleFavorite = ::toggleFavorite,
                onClose = ::closeDrawer,
            )
        }

        // Thanh chữ cái của màn hình chính: vuốt tới chữ nào, các app của chữ đó
        // hiện ngay bên trái (giống Niagara), không cần mở cả danh sách
        if (!drawerOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                AlphabetBar(
                    letters = allLetters,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 20.dp),
                    onSelect = { letter, yInRoot ->
                        peekLetter = letter
                        peekY = yInRoot
                    },
                )
            }
        }
    }
}

// ===========================================================================
// Màn hình chính: đồng hồ + ứng dụng yêu thích trên nền hình nền trong suốt.
// Vuốt lên: mở danh sách. Vuốt xuống: mở thanh thông báo.
// ===========================================================================
@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    repo: AppRepository,
    favorites: List<AppInfo>,
    peekLetter: String?,
    peekApps: List<AppInfo>,
    peekY: Float,
    onDismissPeek: () -> Unit,
    onOpen: (AppInfo) -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val dismissPeek by rememberUpdatedState(onDismissPeek)
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val favoritesAlpha by animateFloatAsState(if (peekLetter == null) 1f else 0f, label = "favAlpha")

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x33000000), Color.Transparent, Color(0x33000000))
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        when {
                            dragTotal < -threshold -> openDrawer()
                            dragTotal > threshold -> repo.expandNotifications()
                        }
                        dragTotal = 0f
                    },
                    onDragCancel = { dragTotal = 0f },
                ) { change, amount ->
                    change.consume()
                    dragTotal += amount
                }
            }
            // Chạm vào chỗ trống: tắt danh sách xem nhanh
            .pointerInput(Unit) { detectTapGestures { dismissPeek() } }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(end = 48.dp)
        ) {
            ClockHeader(repo)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = favoritesAlpha },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (favorites.isEmpty()) {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            "Vuốt dọc theo bảng chữ cái bên phải để tìm ứng dụng",
                            style = textOnWallpaper.copy(fontSize = 17.sp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nhấn giữ một ứng dụng và chọn \"Thêm vào yêu thích\" để ghim nó ra đây.",
                            style = textOnWallpaper.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f)),
                        )
                    }
                } else {
                    Column {
                        favorites.forEach { app ->
                            AppRow(
                                app = app,
                                isFavorite = true,
                                large = true,
                                onOpen = { onOpen(app) },
                                onToggleFavorite = { onToggleFavorite(app) },
                                onInfo = { repo.openAppInfo(app) },
                                onUninstall = { repo.uninstall(app) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Danh sách xem nhanh: các app của chữ cái đang chọn, đặt ngang tầm ngón tay
        if (peekLetter != null && peekApps.isNotEmpty()) {
            PeekPanel(
                letter = peekLetter,
                apps = peekApps,
                anchorY = peekY,
                repo = repo,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun PeekPanel(
    letter: String,
    apps: List<AppInfo>,
    anchorY: Float,
    repo: AppRepository,
    onOpen: (AppInfo) -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
) {
    val density = LocalDensity.current
    var containerHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val topLimit = with(density) { 180.dp.toPx() }      // không đè lên đồng hồ
    val bottomLimit = with(density) { 56.dp.toPx() }    // chừa thanh điều hướng

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeight = it.height }
    ) {
        val maxPanel = with(density) { (containerHeight - topLimit - bottomLimit).coerceAtLeast(0f).toDp() }
        Column(
            Modifier
                .padding(end = 64.dp)
                .fillMaxWidth()
                .heightIn(max = maxPanel)
                .onSizeChanged { panelHeight = it.height }
                .graphicsLayer {
                    val wanted = anchorY - panelHeight / 2f
                    val maxTop = (containerHeight - bottomLimit - panelHeight).coerceAtLeast(topLimit)
                    translationY = wanted.coerceIn(topLimit, maxTop)
                }
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = letter,
                style = textOnWallpaper.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
            )
            apps.forEach { app ->
                AppRow(
                    app = app,
                    isFavorite = false,
                    large = false,
                    onOpen = { onOpen(app) },
                    onToggleFavorite = { onToggleFavorite(app) },
                    onInfo = { repo.openAppInfo(app) },
                    onUninstall = { repo.uninstall(app) },
                )
            }
        }
    }
}

// ===========================================================================
// Ngăn "Tất cả ứng dụng": tìm kiếm + danh sách A–Z
// Kéo xuống khi đang ở đầu danh sách để đóng.
// ===========================================================================
@Composable
private fun AppDrawer(
    repo: AppRepository,
    apps: List<AppInfo>,
    favoriteKeys: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (AppInfo) -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val closeDistance = with(density) { 110.dp.toPx() }
    val close by rememberUpdatedState(onClose)
    var pull by remember { mutableFloatStateOf(0f) }

    val filtered = remember(apps, query) {
        val q = query.trim().simplify()
        if (q.isEmpty()) apps else apps.filter { it.searchName.contains(q) }
    }
    val rows = remember(filtered, favoriteKeys) {
        buildList {
            filtered.groupBy { it.letter }
                .toSortedMap(letterOrder)
                .forEach { (letter, list) ->
                    add(ListRow.Header(letter))
                    list.forEach { add(ListRow.App(it, it.key in favoriteKeys)) }
                }
        }
    }
    val letterIndex = remember(rows) {
        rows.withIndex()
            .filter { it.value is ListRow.Header }
            .associate { (it.value as ListRow.Header).letter to it.index }
    }

    // Kéo xuống ở đầu danh sách để đóng
    val pullToClose = remember {
        object : NestedScrollConnection {
            var startedAtTop = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    pull += available.y
                    if (pull > closeDistance) {
                        pull = 0f
                        close()
                    }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f || consumed.y < 0f) pull = 0f
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Đang kéo xuống rồi đổi ý kéo lên: trả lại vị trí trước
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                startedAtTop = !listState.canScrollBackward
                pull = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (startedAtTop && available.y > 2500f) close()
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = pull * 0.5f }
            .background(Color.Black.copy(alpha = 0.45f))
            // Chặn chạm xuyên xuống màn hình chính phía dưới
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            SearchBar(
                query = query,
                onQueryChange = {
                    onQueryChange(it)
                    // Gõ hoặc xoá chữ: luôn đưa danh sách về đầu
                    scope.launch { listState.scrollToItem(0) }
                },
                onGo = { filtered.firstOrNull()?.let(onOpen) },
            )
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 48.dp)
                        .nestedScroll(pullToClose),
                ) {
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
                                text = row.letter,
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
                                large = false,
                                onOpen = { onOpen(row.app) },
                                onToggleFavorite = { onToggleFavorite(row.app) },
                                onInfo = { repo.openAppInfo(row.app) },
                                onUninstall = { repo.uninstall(row.app) },
                            )
                        }
                    }
                }
            }
        }

        AlphabetBar(
            letters = letterIndex.keys.toList(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 20.dp),
            onSelect = { letter, _ ->
                letterIndex[letter]?.let { idx -> scope.launch { listState.scrollToItem(idx) } }
            },
        )
    }
}

/** Một dòng trong danh sách: tiêu đề chữ cái hoặc một ứng dụng. */
private sealed class ListRow(val key: String) {
    class Header(val letter: String) : ListRow("h_$letter")
    class App(val app: AppInfo, val isFavorite: Boolean) : ListRow("a_${app.key}")
}

// ===========================================================================
// Đồng hồ lớn + ngày + pin
// ===========================================================================
@Composable
private fun ClockHeader(repo: AppRepository) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

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

    // Kiểu Niagara: "20:42" và "T.4, 30 Th9"
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val weekday = when (now.dayOfWeek.value) {
        7 -> "CN"
        else -> "T.${now.dayOfWeek.value + 1}"
    }
    val date = "$weekday, ${now.dayOfMonth} Th${now.monthValue}"

    Column(Modifier.padding(start = 28.dp, top = 64.dp)) {
        Text(
            text = time,
            style = textOnWallpaper.copy(fontSize = 52.sp, fontWeight = FontWeight.Normal),
            modifier = Modifier.clickable { repo.openSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        )
        Text(
            text = if (battery >= 0) "$date   ·   $battery%" else date,
            style = textOnWallpaper.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f)),
            modifier = Modifier.clickable {
                repo.openSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
            },
        )
    }
}

// ===========================================================================
// Ô tìm kiếm ứng dụng
// ===========================================================================
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.14f))
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

// ===========================================================================
// Một dòng ứng dụng: chạm để mở, nhấn giữ để hiện menu
// ===========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    isFavorite: Boolean,
    large: Boolean,
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
                .padding(horizontal = 16.dp, vertical = if (large) 11.dp else 9.dp),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(if (large) 44.dp else 40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = app.label,
                style = textOnWallpaper.copy(fontSize = if (large) 22.sp else 18.sp),
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

// ===========================================================================
// Thanh chữ cái A–Z với hiệu ứng sóng kiểu Niagara:
// các chữ gần ngón tay phình to và cong ra trái, kèm bong bóng chữ lớn.
// ===========================================================================
@Composable
private fun AlphabetBar(
    letters: List<String>,
    modifier: Modifier = Modifier,
    onDragStateChange: (Boolean) -> Unit = {},
    onSelect: (letter: String, yInRoot: Float) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDrag by rememberUpdatedState(onDragStateChange)

    var touching by remember { mutableStateOf(false) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf<String?>(null) }
    val centers = remember(letters) { FloatArray(letters.size) }
    var barTop by remember { mutableFloatStateOf(0f) }

    // Độ mạnh của sóng: 0 = phẳng, 1 = phình hết cỡ
    val strength by animateFloatAsState(
        targetValue = if (touching) 1f else 0f,
        animationSpec = tween(durationMillis = if (touching) 120 else 320),
        label = "wave",
    )

    val waveRadius = with(density) { 150.dp.toPx() }   // bán kính vùng cong
    val maxShift = with(density) { 64.dp.toPx() }      // chữ cong ra trái tối đa
    val bubbleRadius = with(density) { 28.dp.toPx() }
    val bubbleOffset = with(density) { 96.dp.toPx() }  // khoảng cách bong bóng tới thanh
    val bubbleText = remember { TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Color.White) }

    fun waveAt(index: Int): Float {
        val c = centers.getOrElse(index) { return 0f }
        val d = abs(c - touchY)
        if (d >= waveRadius) return 0f
        return cos(d / waveRadius * (PI / 2)).toFloat() * strength
    }

    fun pick(y: Float) {
        touchY = y
        if (letters.isEmpty()) return
        var best = 0
        var bestDistance = Float.MAX_VALUE
        centers.forEachIndexed { i, c ->
            val d = abs(c - y)
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        val letter = letters[best.coerceIn(0, letters.lastIndex)]
        if (letter != active) {
            active = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnSelect(letter, barTop + centers.getOrElse(best) { y })
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .width(32.dp)
            .onGloballyPositioned { barTop = it.positionInRoot().y }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    active = null
                    touching = true
                    currentOnDrag(true)
                    try {
                        pick(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                    } finally {
                        touching = false
                        currentOnDrag(false)
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val letter = active ?: return@drawWithContent
                if (strength < 0.01f) return@drawWithContent
                val center = Offset(size.width / 2f - bubbleOffset, touchY)
                drawCircle(
                    color = Color(0xFF2B2E33).copy(alpha = 0.85f * strength),
                    radius = bubbleRadius * (0.5f + 0.5f * strength),
                    center = center,
                )
                val layout = textMeasurer.measure(letter, bubbleText)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        center.x - layout.size.width / 2f,
                        center.y - layout.size.height / 2f,
                    ),
                    alpha = strength,
                )
            },
    ) {
        letters.forEachIndexed { index, letter ->
            val isActive = touching && letter == active
            Text(
                text = letter,
                style = textOnWallpaper.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
                ),
                modifier = Modifier
                    .onGloballyPositioned {
                        if (index < centers.size) {
                            centers[index] = it.positionInParent().y + it.size.height / 2f
                        }
                    }
                    .graphicsLayer {
                        val w = waveAt(index)
                        translationX = -w * maxShift
                        scaleX = 1f + w * 0.35f
                        scaleY = 1f + w * 0.35f
                    }
                    .padding(vertical = 1.dp),
            )
        }
    }
}
