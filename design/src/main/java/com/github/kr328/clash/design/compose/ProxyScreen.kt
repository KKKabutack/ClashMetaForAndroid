package com.github.kr328.clash.design.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.util.ProxySelection
import com.github.kr328.clash.design.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

data class ProxyUiState(
    val groups: Map<String, ProxyGroup> = emptyMap(),
    val selectedGroup: String? = null,
    val testingGroup: String? = null,
    val loading: Boolean = true,
)

sealed interface ProxyAction {
    data object Back : ProxyAction
    data class SelectGroup(val name: String) : ProxyAction
    data class SelectProxy(val group: String, val proxy: Proxy) : ProxyAction
    data class TestGroup(val group: String) : ProxyAction
}

/**
 * Material 3 selector screen with edge-to-edge insets and nested-group link labels.
 *
 * The scrollable proxy grid is full-bleed and captured via Haze; the glass overlay
 * (top app bar + group chips) floats above it, and the grid offsets its first row
 * by the measured overlay height. Cards scroll underneath the glass while scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    state: ProxyUiState,
    onAction: (ProxyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedName = state.selectedGroup ?: state.groups.keys.firstOrNull()
    val group = selectedName?.let(state.groups::get)

    val hazeState = rememberHazeState()
    val barGlassStyle = rememberLiquidGlassStyle(shape = RoundedCornerShape(0.dp))
    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    val topOverlayHeight = with(density) { topOverlayHeightPx.toDp() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrollable content is full-bleed so cards slide underneath the glass
            // overlay while scrolling; the overlay height offsets the first row.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                if (state.loading) {
                    // 核心载入配置/查询分组期间不渲染空态提示，避免"没有可以显示的组"闪现
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topOverlayHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (group == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topOverlayHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.proxy_empty_tips),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    ProxyGrid(
                        groupName = selectedName,
                        group = group,
                        groups = state.groups,
                        testing = state.testingGroup == selectedName,
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            top = topOverlayHeight + 8.dp,
                            end = 20.dp,
                            bottom = innerPadding.calculateBottomPadding() + 20.dp,
                        ),
                        onAction = onAction,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { topOverlayHeightPx = it.size.height }
                    .liquidGlass(state = hazeState, style = barGlassStyle),
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.endpoint_region))
                            Text(
                                stringResource(R.string.endpoint_region_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { onAction(ProxyAction.Back) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    },
                    actions = {
                        selectedName?.let { name ->
                            // While a latency test is in flight the flash icon becomes a spinner and
                            // the button is disabled so users get visible feedback and can't spam
                            // additional tests (which the Go core would otherwise merge via singleDo).
                            IconButton(
                                onClick = { onAction(ProxyAction.TestGroup(name)) },
                                enabled = state.testingGroup == null,
                            ) {
                                if (state.testingGroup == name) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = LocalContentColor.current,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_baseline_flash_on),
                                        contentDescription = stringResource(R.string.delay_test),
                                    )
                                }
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
                LazyRow(
                    modifier = Modifier.padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.groups.keys.toList(), key = { it }) { name ->
                        FilterChip(
                            selected = name == selectedName,
                            onClick = { onAction(ProxyAction.SelectGroup(name)) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyGrid(
    groupName: String,
    group: ProxyGroup,
    groups: Map<String, ProxyGroup>,
    testing: Boolean,
    contentPadding: PaddingValues,
    onAction: (ProxyAction) -> Unit,
) {
    val selectedLabel = remember(group.now, groups) {
        if (group.now.isBlank()) ""
        else ProxySelection.resolveLeafName(groups, group.now)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = when {
                    testing -> stringResource(R.string.delay_test)
                    selectedLabel.isBlank() -> group.now
                    selectedLabel == group.now -> group.now
                    else -> "${group.now} · $selectedLabel"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(group.proxies, key = { it.name }) { proxy ->
            val selected = proxy.name == group.now
            val subtitle = ProxySelection.subtitle(proxy, groups)
            GlassCard(
                onClick = {
                    if (group.type == "Selector") onAction(ProxyAction.SelectProxy(groupName, proxy))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tint = if (selected) rememberGlassAccent() else rememberGlassCream(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (proxy.isGroup) proxy.name else proxy.title.ifBlank { proxy.name },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                        )
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_outline_check_circle),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    if (proxy.delay in 0..65534) {
                        Text(
                            text = "${proxy.delay} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        // clash/mihomo LastDelayForTestUrl returns uint16 max (65535) as the
                        // "not tested / unreachable" sentinel. Surface that as an explicit label
                        // instead of leaking the raw sentinel value to the user.
                        Text(
                            text = stringResource(R.string.delay_untested),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
