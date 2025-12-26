package fansirsqi.xposed.sesame.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.ui.theme.LogViewerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LogViewerScreen(
    filePath: String,
    onBackClick: () -> Unit,
    viewModel: LogViewerViewModel = viewModel()
) {
    LogViewerTheme {
        val context = LocalContext.current
        val state by viewModel.uiState.collectAsState()
        val floatValue by viewModel.fontSize.collectAsState()
        val currentFontSize = floatValue.sp

        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        // 菜单显示状态
        var showMenu by remember { mutableStateOf(false) }
        var isSearchActive by remember { mutableStateOf(false) }

        val focusRequester = remember { FocusRequester() }

        // 拦截返回键
        BackHandler(enabled = isSearchActive) {
            isSearchActive = false
            viewModel.search("")
        }

        // 自动滚动逻辑
        LaunchedEffect(filePath) {
            viewModel.loadLogs(filePath)
            viewModel.scrollEvent.collect { index ->
                if (index >= 0 && index < state.mappingList.size) {
                    try {
                        listState.scrollToItem(index)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 自动聚焦
        LaunchedEffect(isSearchActive) {
            if (isSearchActive) {
                delay(100)
                focusRequester.requestFocus()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        // 【Tooltip 1】返回按钮
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text("返回") } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = {
                                if (isSearchActive) {
                                    isSearchActive = false
                                    viewModel.search("")
                                } else {
                                    onBackClick()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                    title = {
                        // Title 区域动画
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = {
                                if (targetState) {
                                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                } else {
                                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                }
                            },
                            label = "TitleAnimation"
                        ) { searching ->
                            if (searching) {
                                TextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.search(it) },
                                    placeholder = {
                                        Text(
                                            "Search...",
                                            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onPrimary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { /* 收起键盘逻辑 */ })
                                )
                            } else {
                                Column {
                                    Text(
                                        File(filePath).name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        if (state.isLoading) "Loading..." else "${state.mappingList.size} lines",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ActionAnimation"
                        ) { searching ->
                            if (searching) {
                                // 【Tooltip 2】清除搜索按钮
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                    tooltip = { PlainTooltip { Text("退出搜索") } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { viewModel.search("") }) {
                                        Icon(Icons.Default.Close, "Clear")
                                    }
                                }
                            } else {
                                Row {
                                    // 【Tooltip 3】自动滚动按钮 (这是你重点要的)
                                    val autoScrollText = if (state.autoScroll) "暂停自动滚动" else "开启自动滚动"
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                        tooltip = { PlainTooltip { Text(autoScrollText) } },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(onClick = { viewModel.toggleAutoScroll(!state.autoScroll) }) {
                                            // 换成了 Core 库的 VerticalAlignBottom，避免 Extended 库体积过大
                                            val icon = if (state.autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.Pause
                                            val tint = if (state.autoScroll) Color.Green else MaterialTheme.colorScheme.onPrimary
                                            Icon(icon, "AutoScroll", tint = tint)
                                        }
                                    }

                                    Box {
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                            tooltip = { PlainTooltip { Text("更多选项") } },
                                            state = rememberTooltipState()
                                        ) {
                                            IconButton(onClick = { showMenu = true }) {
                                                Icon(Icons.Default.MoreVert, "More Options")
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            // === 原有：搜索 ===
                                            DropdownMenuItem(
                                                text = { Text("搜索日志") },
                                                onClick = {
                                                    showMenu = false
                                                    isSearchActive = true
                                                },
                                                leadingIcon = { Icon(Icons.Default.Search, null) }
                                            )

                                            HorizontalDivider()

                                            // === 新增：滚动控制 ===
                                            DropdownMenuItem(
                                                text = { Text("滑动到顶部") },
                                                onClick = {
                                                    showMenu = false
                                                    // 使用父作用域的 scope 和 listState
                                                    scope.launch { listState.scrollToItem(0) }
                                                },
                                                leadingIcon = { Icon(Icons.Default.VerticalAlignTop, null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("滑动到底部") },
                                                onClick = {
                                                    showMenu = false
                                                    scope.launch {
                                                        val lastIndex = (state.mappingList.size - 1).coerceAtLeast(0)
                                                        listState.scrollToItem(lastIndex)
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.VerticalAlignBottom, null) }
                                            )

                                            HorizontalDivider()


                                            // === 二级菜单：字体设置 ===
                                            // 定义二级菜单的显示状态
                                            var showFontSubMenu by remember { mutableStateOf(false) }

                                            // 使用 Box 作为锚点，确保二级菜单显示在“字体设置”这一项旁边
                                            Box {
                                                DropdownMenuItem(
                                                    text = { Text("字体设置") },
                                                    onClick = { showFontSubMenu = true },
                                                    // 左侧图标（可选，如果没有 FormatSize 可以换别的）
                                                    leadingIcon = { Icon(Icons.Default.FontDownload, null) },
                                                    // 右侧指示箭头
//                                                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                                )

                                                // 二级菜单本体
                                                DropdownMenu(
                                                    expanded = showFontSubMenu,
                                                    onDismissRequest = { showFontSubMenu = false },
                                                    // 这里的 offset 可以微调二级菜单的位置，让它稍微错开一点
                                                    offset = androidx.compose.ui.unit.DpOffset(x = 10.dp, y = 0.dp)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("放大字体") },
                                                        onClick = {
                                                            // 这里看你需求：点击后是仅关闭二级菜单，还是关闭所有菜单？
                                                            // 通常为了方便连续调节，可以只操作字体，不关闭菜单。
                                                            // 但如果是为了整洁，可以设为 showMenu = false
                                                            viewModel.increaseFontSize()
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Add, null) }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("缩小字体") },
                                                        onClick = { viewModel.decreaseFontSize() },
                                                        leadingIcon = { Icon(Icons.Default.Remove, null) }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("重置大小") },
                                                        onClick = {
                                                            viewModel.resetFontSize()
                                                            showFontSubMenu = false // 重置通常是一次性操作，可以关闭二级菜单
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                                    )
                                                }
                                            }

                                            HorizontalDivider()

                                            // === 原有：文件操作 ===
                                            DropdownMenuItem(
                                                text = { Text("导出文件") },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.exportLogFile(context)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Share, null) }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("清空日志", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.clearLogFile(context)
                                                },
                                                leadingIcon = { Icon(Icons.Default.CleaningServices, null, tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) viewModel.scaleFontSize(zoom)
                        }
                    }
            ) {
                if (state.isLoading && state.mappingList.isEmpty()) {
                    // 修复：将 Loading 内容包裹在 Column 中，确保正确居中
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Loading...",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            items(
                                count = state.mappingList.size,
                                key = { index -> index }
                            ) { index ->
                                val lineContent = viewModel.getLineContent(index)
                                LogLineItem(
                                    line = lineContent,
                                    searchQuery = state.searchQuery,
                                    fontSize = currentFontSize,
                                    textColor = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                    DraggableScrollbar(listState = listState, totalItems = state.mappingList.size, modifier = Modifier.align(Alignment.CenterEnd))
                }

                if (!state.autoScroll && !state.isSearching) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp, bottom = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (listState.canScrollBackward) {
                            SmallFloatingActionButton(
                                onClick = { scope.launch { listState.scrollToItem(0) } },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Top")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (listState.canScrollForward) {
                            SmallFloatingActionButton(
                                onClick = { viewModel.toggleAutoScroll(true) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Bottom")
                            }
                        }
                    }
                }

                if (state.isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .pointerInput(Unit) {}
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Searching...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

// LogLineItem 和 DraggableScrollbar 保持不变...
@Composable
fun LogLineItem(line: String, searchQuery: String, fontSize: TextUnit, textColor: Color) {
    val annotatedString = if (searchQuery.isNotEmpty()) {
        buildAnnotatedString {
            val lowerLine = line.lowercase()
            val lowerQuery = searchQuery.lowercase()
            var startIndex = 0
            while (true) {
                val index = lowerLine.indexOf(lowerQuery, startIndex)
                if (index == -1) {
                    append(line.substring(startIndex)); break
                }
                append(line.substring(startIndex, index))
                withStyle(style = SpanStyle(background = Color(0xFFE64000), color = Color.White)) { append(line.substring(index, index + searchQuery.length)) }
                startIndex = index + searchQuery.length
            }
        }
    } else {
        buildAnnotatedString { append(line) }
    }
    Text(
        text = annotatedString,
        color = textColor,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        lineHeight = fontSize * 1.2f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
    )
}

@Composable
fun DraggableScrollbar(listState: LazyListState, totalItems: Int, modifier: Modifier = Modifier) {
    if (totalItems <= 0) return
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isVisible by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500); isVisible = false
        }
    }

    val alpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f, animationSpec = tween(durationMillis = 300), label = "Alpha")

    val scrollbarInfo by remember(totalItems, trackHeightPx) {
        derivedStateOf {
            if (trackHeightPx == 0f) return@derivedStateOf null
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) return@derivedStateOf null
            val visibleCount = visibleItemsInfo.size
            val firstVisible = listState.firstVisibleItemIndex
            val thumbSizeRatio = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.05f, 1f)
            val thumbHeightPx = trackHeightPx * thumbSizeRatio
            val scrollableHeightPx = trackHeightPx - thumbHeightPx
            val progress = firstVisible.toFloat() / max(1, totalItems - visibleCount).toFloat()
            val thumbOffsetPx = scrollableHeightPx * progress.coerceIn(0f, 1f)
            Pair(thumbHeightPx, thumbOffsetPx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(30.dp)
            .alpha(alpha)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    isDragging = true
                    if (trackHeightPx > 0f) {
                        val dragRatio = delta / trackHeightPx
                        val targetIndex = (listState.firstVisibleItemIndex + (dragRatio * totalItems)).toInt().coerceIn(0, totalItems - 1)
                        coroutineScope.launch { listState.scrollToItem(targetIndex) }
                    }
                },
                onDragStopped = { isDragging = false }
            )
    ) {
        if (scrollbarInfo != null) {
            val (thumbHeightPx, thumbOffsetPx) = scrollbarInfo!!
            val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
            val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .width(4.dp)
                    .height(thumbHeightDp)
                    .offset(y = thumbOffsetDp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
        }
    }
}