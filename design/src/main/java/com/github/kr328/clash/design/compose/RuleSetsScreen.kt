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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.github.kr328.clash.core.model.RuleSetConfig
import com.github.kr328.clash.design.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.DateFormat
import java.util.Date

/**
 * Rule set library: every persisted [RuleSetConfig] with activation, duplication,
 * rename and deletion, plus the glass FAB creating a blank configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSetsScreen(
    state: ProxyUiState,
    onAction: (ProxyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()
    val barGlassStyle = rememberLiquidGlassStyle(shape = RoundedCornerShape(0.dp))
    val fabGlassStyle = rememberLiquidGlassStyle(
        shape = RoundedCornerShape(percent = 50),
        pressScale = 0.94f,
    )
    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    val topOverlayHeight = with(density) { topOverlayHeightPx.toDp() }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RuleSetConfig?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(56.dp)
                    .liquidGlass(state = hazeState, style = fabGlassStyle)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showCreate = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_add),
                    contentDescription = stringResource(R.string.rule_set_new),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
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
                    if (state.ruleSets.isEmpty()) {
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
                        items(state.ruleSets, key = { it.uuid.toString() }) { config ->
                            RuleSetRow(
                                config = config,
                                active = config.uuid == state.activeRuleSet,
                                updatedText = if (config.updatedAt > 0) {
                                    stringResource(R.string.rule_set_updated_format, dateFormat.format(Date(config.updatedAt)))
                                } else {
                                    ""
                                },
                                onAction = onAction,
                                onRename = { renameTarget = config },
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
                    title = { Text(stringResource(R.string.rule_set_library)) },
                    navigationIcon = {
                        IconButton(onClick = { onAction(ProxyAction.Back) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = stringResource(R.string.close),
                            )
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

    if (showCreate) {
        RuleSetNameDialog(
            titleRes = R.string.rule_set_new,
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                onAction(ProxyAction.CreateRuleSet(name))
            },
        )
    }

    renameTarget?.let { target ->
        RuleSetNameDialog(
            titleRes = R.string.rename,
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onAction(ProxyAction.RenameRuleSet(target.uuid, name))
            },
        )
    }
}

@Composable
private fun RuleSetRow(
    config: RuleSetConfig,
    active: Boolean,
    updatedText: String,
    onAction: (ProxyAction) -> Unit,
    onRename: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tint = if (active) rememberGlassAccent() else rememberGlassCream(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (active) R.drawable.ic_outline_check_circle else R.drawable.ic_outline_folder,
                ),
                contentDescription = if (active) stringResource(R.string.rule_set_active) else null,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.rule_set_groups_format, config.groups.size) +
                        if (updatedText.isNotBlank()) " · $updatedText" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_more_vert),
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rule_set_activate)) },
                            onClick = {
                                menuExpanded = false
                                onAction(ProxyAction.ActivateRuleSet(config.uuid))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        onClick = {
                            menuExpanded = false
                            onAction(ProxyAction.DuplicateRuleSet(config.uuid))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            menuExpanded = false
                            onAction(ProxyAction.DeleteRuleSet(config.uuid))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSetNameDialog(
    titleRes: Int,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
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
                onClick = { onConfirm(name.trim()) },
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
