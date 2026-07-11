package com.github.kr328.clash.design.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.design.R

data class ProxyUiState(
    val groups: Map<String, ProxyGroup> = emptyMap(),
    val selectedGroup: String? = null,
    val testingGroup: String? = null,
)

sealed interface ProxyAction {
    data object Back : ProxyAction
    data class SelectGroup(val name: String) : ProxyAction
    data class SelectProxy(val group: String, val proxy: Proxy) : ProxyAction
    data class TestGroup(val group: String) : ProxyAction
}

/** Material 3 Compose selector screen with an adaptive proxy-card grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    state: ProxyUiState,
    onAction: (ProxyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedName = state.selectedGroup ?: state.groups.keys.firstOrNull()
    val group = selectedName?.let(state.groups::get)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
                        IconButton(onClick = { onAction(ProxyAction.TestGroup(name)) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_flash_on),
                                contentDescription = stringResource(R.string.delay_test),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
            if (group == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
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
                    testing = state.testingGroup == selectedName,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ProxyGrid(
    groupName: String,
    group: ProxyGroup,
    testing: Boolean,
    onAction: (ProxyAction) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = if (testing) stringResource(R.string.delay_test) else group.now,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(group.proxies, key = { it.name }) { proxy ->
            val selected = proxy.name == group.now
            ElevatedCard(
                onClick = {
                    if (group.type == "Selector") onAction(ProxyAction.SelectProxy(groupName, proxy))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = proxy.title.ifBlank { proxy.name },
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
                    if (proxy.subtitle.isNotBlank()) {
                        Text(
                            text = proxy.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    if (proxy.delay >= 0) {
                        Text(
                            text = "${proxy.delay} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
