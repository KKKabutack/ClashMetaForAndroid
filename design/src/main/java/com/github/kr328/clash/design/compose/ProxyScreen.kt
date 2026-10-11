package com.github.kr328.clash.design.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.github.kr328.clash.core.model.RuleClassPreview
import com.github.kr328.clash.core.model.RuleSetConfig
import com.github.kr328.clash.core.util.ProxySelection
import com.github.kr328.clash.design.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID

/** Top-level segments of the proxy screen: node selection vs rule tags. */
enum class ProxySegment(val labelRes: Int) {
    Nodes(R.string.proxy_segment_nodes),
    Rules(R.string.proxy_segment_rules),
}

/** State of the L4 rule editor sheet; null means the sheet is dismissed. */
data class RuleEditorState(
    val className: String,
    /** Index into the class's custom rules; null creates a new rule. */
    val index: Int?,
    val initialType: String,
    val initialValue: String,
)

data class ProxyUiState(
    val groups: Map<String, ProxyGroup> = emptyMap(),
    val selectedGroup: String? = null,
    val testingGroup: String? = null,
    val loading: Boolean = true,
    val segment: ProxySegment = ProxySegment.Nodes,
    val ruleClasses: List<RuleClassPreview> = emptyList(),
    /** Name of the tag shown in the rule list; null stays on the tag list. */
    val selectedRuleClass: String? = null,
    val showRuleSets: Boolean = false,
    val ruleSets: List<RuleSetConfig> = emptyList(),
    val activeRuleSet: UUID? = null,
    val ruleEditor: RuleEditorState? = null,
)

sealed interface ProxyAction {
    data object Back : ProxyAction
    data class SelectGroup(val name: String) : ProxyAction
    data class SelectProxy(val group: String, val proxy: Proxy) : ProxyAction
    data class TestGroup(val group: String) : ProxyAction

    data class SwitchSegment(val segment: ProxySegment) : ProxyAction
    data class OpenRuleClass(val name: String) : ProxyAction
    data object CloseRuleClass : ProxyAction
    data object OpenRuleSets : ProxyAction
    data object CloseRuleSets : ProxyAction
    data class CreateRuleClass(val name: String) : ProxyAction
    data class AddRule(val className: String, val type: String, val value: String) : ProxyAction
    data class UpdateRule(val className: String, val index: Int, val type: String, val value: String) : ProxyAction
    data class DeleteRule(val className: String, val index: Int) : ProxyAction
    data class DeleteRuleClass(val className: String) : ProxyAction
    data class OpenRuleEditor(val className: String, val index: Int?, val initialType: String, val initialValue: String) : ProxyAction
    data object CloseRuleEditor : ProxyAction
    data class SaveRuleEditor(val type: String, val value: String) : ProxyAction

    data class ActivateRuleSet(val uuid: UUID) : ProxyAction
    data class DuplicateRuleSet(val uuid: UUID) : ProxyAction
    data class RenameRuleSet(val uuid: UUID, val name: String) : ProxyAction
    data class DeleteRuleSet(val uuid: UUID) : ProxyAction
    data class CreateRuleSet(val name: String) : ProxyAction
}

/**
 * Entry of the proxy/rules flow. The rules segment is a tag list; opening a tag
 * shows [RulesScreen]. The rule editor floats as a sheet above either level.
 *
 * The scrollable content is full-bleed and captured via Haze; the glass overlay
 * (top app bar + segment control + group chips) floats above it, and the content
 * offsets its first row by the measured overlay height.
 */
@Composable
fun ProxyScreen(
    state: ProxyUiState,
    onAction: (ProxyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.selectedRuleClass != null -> RulesScreen(state = state, onAction = onAction, modifier = modifier)
        else -> ProxyLevel2(state = state, onAction = onAction, modifier = modifier)
    }

    state.ruleEditor?.let { editor ->
        RuleEditorSheet(editor = editor, onAction = onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyLevel2(
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

    var showCreateClass by remember { mutableStateOf(false) }

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
                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topOverlayHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.segment == ProxySegment.Nodes && group == null -> Column(
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
                    state.segment == ProxySegment.Nodes -> ProxyGrid(
                        groupName = selectedName!!,
                        group = group!!,
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
                    else -> RuleTagList(
                        state = state,
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            top = topOverlayHeight + 8.dp,
                            end = 20.dp,
                            bottom = innerPadding.calculateBottomPadding() + 20.dp,
                        ),
                        onAction = onAction,
                        onCreateTag = { showCreateClass = true },
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
                            Text(stringResource(R.string.proxy_rules))
                            Text(
                                stringResource(R.string.proxy_rules_summary),
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
                        if (state.segment == ProxySegment.Nodes) {
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
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentChip(
                        segment = ProxySegment.Nodes,
                        selected = state.segment == ProxySegment.Nodes,
                        modifier = Modifier.weight(1f),
                    ) {
                        onAction(ProxyAction.SwitchSegment(ProxySegment.Nodes))
                    }
                    SegmentChip(
                        segment = ProxySegment.Rules,
                        selected = state.segment == ProxySegment.Rules,
                        modifier = Modifier.weight(1f),
                    ) {
                        onAction(ProxyAction.SwitchSegment(ProxySegment.Rules))
                    }
                }
                if (state.segment == ProxySegment.Nodes) {
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
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

    if (showCreateClass) {
        NewTagDialog(
            onDismiss = { showCreateClass = false },
            onCreate = { name ->
                showCreateClass = false
                onAction(ProxyAction.CreateRuleClass(name))
            },
        )
    }
}

/** Segment pill of the L2 switcher, styled like the mode chips on the main screen. */
@Composable
private fun SegmentChip(
    segment: ProxySegment,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .glassSurface(
                shape = MaterialTheme.shapes.medium,
                tint = if (selected) rememberGlassAccent() else rememberGlassCream(),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(segment.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Rules segment: one row per tag, with the group's current selection beside the rule count. */
@Composable
private fun RuleTagList(
    state: ProxyUiState,
    contentPadding: PaddingValues,
    onAction: (ProxyAction) -> Unit,
    onCreateTag: () -> Unit,
) {
    var pickingTag by remember { mutableStateOf<String?>(null) }
    val pickingGroup = pickingTag?.let(state.groups::get)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.ruleClasses.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.rule_tag_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.ruleClasses, key = { it.name }) { preview ->
            RuleTagRow(
                preview = preview,
                group = state.groups[preview.name],
                onOpen = { onAction(ProxyAction.OpenRuleClass(preview.name)) },
                onPickNode = { pickingTag = preview.name },
            )
        }
        item(key = "new_tag") {
            GlassCard(
                onClick = onCreateTag,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.rule_tag_new),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (pickingTag != null && pickingGroup != null && canAssignNode(pickingTag!!, pickingGroup)) {
        GroupNodeDialog(
            group = pickingGroup,
            onDismiss = { pickingTag = null },
            onSelect = { proxy ->
                val tag = pickingTag
                pickingTag = null
                if (tag != null) onAction(ProxyAction.SelectProxy(tag, proxy))
            },
        )
    }
}

@Composable
private fun RuleTagRow(
    preview: RuleClassPreview,
    group: ProxyGroup?,
    onOpen: () -> Unit,
    onPickNode: () -> Unit,
) {
    val selection = ruleTagSelection(preview.name, group)
    val count = stringResource(R.string.rule_count_format, preview.rules.size)
    val subtitle = if (selection.isNullOrBlank()) count else "$count · $selection"
    val assignable = canAssignNode(preview.name, group)

    GlassCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = ruleTagTitle(preview.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (assignable) {
                IconButton(onClick = onPickNode) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_swap_vert),
                        contentDescription = stringResource(R.string.rule_group_using),
                    )
                }
            }
        }
    }
}

/** Dialog for creating a tag. The node is chosen later on the tag row. */
@Composable
private fun NewTagDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_tag_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
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
