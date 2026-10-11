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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.RulePreviewLine
import com.github.kr328.clash.design.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Rule types offered by the editor; MATCH carries no value. */
internal val RULE_TYPES = listOf(
    "DOMAIN",
    "DOMAIN-SUFFIX",
    "DOMAIN-KEYWORD",
    "IP-CIDR",
    "IP-CIDR6",
    "GEOIP",
    "GEOSITE",
    "SRC-IP-CIDR",
    "DST-PORT",
    "MATCH",
)

@Composable
internal fun ruleTagTitle(name: String): String = when (name.uppercase()) {
    "DIRECT" -> stringResource(R.string.rule_tag_direct)
    "REJECT", "REJECT-DROP" -> stringResource(R.string.rule_tag_reject)
    else -> name
}

@Composable
internal fun rulePolicyLabel(name: String): String = when (name.uppercase()) {
    "DIRECT" -> stringResource(R.string.rule_tag_direct)
    "REJECT", "REJECT-DROP" -> stringResource(R.string.rule_tag_reject)
    else -> stringResource(R.string.rule_tag_proxy)
}

/** Current selection shown beside a proxy tag. Direct and reject tags have none. */
@Composable
internal fun ruleTagSelection(name: String, group: ProxyGroup?): String? {
    if (isTerminalPolicy(name) || group == null || group.now.isBlank()) return null
    return when (group.type) {
        "URLTest", "Fallback", "LoadBalance" -> {
            val auto = stringResource(R.string.rule_group_auto)
            "$auto · ${group.now}"
        }
        else -> group.now
    }
}

/** True when the tag is a select group whose member the user can assign. */
internal fun canAssignNode(name: String, group: ProxyGroup?): Boolean {
    return !isTerminalPolicy(name) &&
        group != null &&
        group.type == "Selector" &&
        group.proxies.isNotEmpty()
}

private fun isTerminalPolicy(name: String): Boolean {
    return when (name.uppercase()) {
        "DIRECT", "REJECT", "REJECT-DROP" -> true
        else -> false
    }
}

/**
 * Rules under one tag. The header assigns a node to the whole group when the
 * tag is a select policy. Each row shows the match and a proxy / direct / reject badge.
 * Subscription rows are read-only; user rows can be edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: ProxyUiState,
    onAction: (ProxyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val className = state.selectedRuleClass
    val preview = state.ruleClasses.firstOrNull { it.name == className }
    val group = className?.let(state.groups::get)
    var pickingNode by remember { mutableStateOf(false) }

    val hazeState = rememberHazeState()
    val barGlassStyle = rememberLiquidGlassStyle(shape = RoundedCornerShape(0.dp))
    val fabGlassStyle = rememberLiquidGlassStyle(
        shape = RoundedCornerShape(percent = 50),
        pressScale = 0.94f,
    )
    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    val topOverlayHeight = with(density) { topOverlayHeightPx.toDp() }
    val deletableTag = preview?.takeIf { it.isCustom && it.rules.none { line -> !line.custom } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (preview != null && className != null) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .size(56.dp)
                        .liquidGlass(state = hazeState, style = fabGlassStyle)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAction(ProxyAction.OpenRuleEditor(className, null, RULE_TYPES.first(), "")) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add),
                        contentDescription = stringResource(R.string.rule_add),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = topOverlayHeight + 8.dp,
                        end = 20.dp,
                        bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (preview == null || className == null) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.empty),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        if (group != null && !isTerminalPolicy(className)) {
                            item(key = "selection") {
                                GroupSelectionCard(
                                    group = group,
                                    assignable = canAssignNode(className, group),
                                    onPick = { pickingNode = true },
                                )
                            }
                        }
                        if (preview.rules.isEmpty()) {
                            item(key = "empty_rules") {
                                Text(
                                    text = stringResource(R.string.rule_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        itemsIndexed(preview.rules, key = { index, line ->
                            if (line.custom) "c_${line.customIndex}" else "s_$index"
                        }) { _, line ->
                            RuleRow(
                                tag = preview.name,
                                line = line,
                                onAction = onAction,
                            )
                        }
                    }
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
                        Text(className?.let { ruleTagTitle(it) } ?: "")
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
                        if (deletableTag != null) {
                            IconButton(onClick = { onAction(ProxyAction.DeleteRuleClass(deletableTag.name)) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_outline_delete),
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }
    }

    val openTag = className
    val selectable = if (pickingNode && openTag != null) group?.takeIf { canAssignNode(openTag, it) } else null
    if (selectable != null && openTag != null) {
        GroupNodeDialog(
            group = selectable,
            onDismiss = { pickingNode = false },
            onSelect = { proxy ->
                pickingNode = false
                onAction(ProxyAction.SelectProxy(openTag, proxy))
            },
        )
    }
}

@Composable
private fun GroupSelectionCard(
    group: ProxyGroup,
    assignable: Boolean,
    onPick: () -> Unit,
) {
    val selection = when (group.type) {
        "URLTest", "Fallback", "LoadBalance" -> {
            val auto = stringResource(R.string.rule_group_auto)
            if (group.now.isBlank()) auto else "$auto · ${group.now}"
        }
        else -> group.now
    }

    GlassCard(
        onClick = if (assignable) onPick else null,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tint = rememberGlassAccent(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rule_group_using),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selection,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (assignable) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_swap_vert),
                    contentDescription = stringResource(R.string.rule_group_using),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RuleRow(
    tag: String,
    line: RulePreviewLine,
    onAction: (ProxyAction) -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tint = rememberGlassCream(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (line.custom) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = line.type,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (line.value.isNotBlank()) {
                    Text(
                        text = line.value,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                }
                if (line.extra.isNotBlank()) {
                    Text(
                        text = line.extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = rulePolicyLabel(tag),
                style = MaterialTheme.typography.labelLarge,
                color = when (tag.uppercase()) {
                    "DIRECT" -> MaterialTheme.colorScheme.onSurfaceVariant
                    "REJECT", "REJECT-DROP" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.End,
            )
            if (line.custom && line.customIndex >= 0) {
                IconButton(onClick = {
                    onAction(ProxyAction.OpenRuleEditor(tag, line.customIndex, line.type, line.value))
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_edit),
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { onAction(ProxyAction.DeleteRule(tag, line.customIndex)) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_delete),
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Picks one member for the whole select group. */
@Composable
internal fun GroupNodeDialog(
    group: ProxyGroup,
    onDismiss: () -> Unit,
    onSelect: (Proxy) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_group_using)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(group.proxies, key = { it.name }) { proxy ->
                    val selected = proxy.name == group.now
                    val label = if (proxy.isGroup) proxy.name else proxy.title.ifBlank { proxy.name }
                    TextButton(
                        onClick = { onSelect(proxy) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.fillMaxWidth(),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Bottom sheet editing one custom rule, or creating one when [RuleEditorState.index] is null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleEditorSheet(
    editor: RuleEditorState,
    onAction: (ProxyAction) -> Unit,
) {
    var type by remember(editor) { mutableStateOf(editor.initialType) }
    var value by remember(editor) { mutableStateOf(editor.initialValue) }
    var typeMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { onAction(ProxyAction.CloseRuleEditor) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(if (editor.index == null) R.string.rule_add else R.string.rule_edit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Box {
                OutlinedButton(
                    onClick = { typeMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(type)
                }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    RULE_TYPES.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate) },
                            onClick = {
                                type = candidate
                                typeMenu = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rule_value)) },
                enabled = type != "MATCH",
                singleLine = true,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onAction(ProxyAction.CloseRuleEditor) }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = {
                    onAction(ProxyAction.SaveRuleEditor(type, value.trim()))
                }) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
