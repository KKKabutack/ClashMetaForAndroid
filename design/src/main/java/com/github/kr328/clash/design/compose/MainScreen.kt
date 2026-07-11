package com.github.kr328.clash.design.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

enum class MainDestination(val labelRes: Int, val iconRes: Int) {
    Connection(R.string.tab_connection, R.drawable.ic_baseline_vpn_lock),
    Endpoints(R.string.tab_endpoints, R.drawable.ic_baseline_cloud_download),
    Settings(R.string.settings, R.drawable.ic_baseline_settings),
}

data class MainUiState(
    val running: Boolean = false,
    val forwarded: String = "0 B",
    val mode: TunnelState.Mode = TunnelState.Mode.Rule,
    val profileName: String? = null,
    val hasProviders: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val hasUpdatableProfiles: Boolean = false,
    val isUpdatingProfiles: Boolean = false,
    val aboutVersion: String? = null,
)

sealed interface MainAction {
    data object ToggleConnection : MainAction
    data object OpenProxy : MainAction
    data object OpenProviders : MainAction
    data object CreateProfile : MainAction
    data object UpdateAllProfiles : MainAction
    data object OpenAppSettings : MainAction
    data object OpenNetworkSettings : MainAction
    data object OpenOverrideSettings : MainAction
    data object OpenMetaFeatureSettings : MainAction
    data object OpenLogs : MainAction
    data object OpenHelp : MainAction
    data object OpenAbout : MainAction
    data object DismissAbout : MainAction
    data class ActivateProfile(val profile: Profile) : MainAction
    data class UpdateProfile(val profile: Profile) : MainAction
    data class EditProfile(val profile: Profile) : MainAction
    data class DuplicateProfile(val profile: Profile) : MainAction
    data class DeleteProfile(val profile: Profile) : MainAction
    data class PatchMode(val mode: TunnelState.Mode) : MainAction
}

/**
 * Main Compose shell. The three destinations are peers; profile management lives
 * only under Endpoints, avoiding the old Connection-page shortcut that merely
 * switched tabs. The navigation is a floating Material surface, while Scaffold
 * owns its content and FAB offsets so they never overlap system navigation.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf(MainDestination.Connection) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MainDestination.entries.forEach { item ->
                            FloatingToolbarItem(
                                item = item,
                                selected = destination == item,
                                onClick = { destination = item },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (destination == MainDestination.Endpoints) {
                FloatingActionButton(onClick = { onAction(MainAction.CreateProfile) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add),
                        contentDescription = stringResource(R.string._new),
                    )
                }
            }
        },
    ) { contentPadding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 40)) togetherWith
                    fadeOut(animationSpec = tween(120))
            },
            label = "main_destination",
        ) { currentDestination ->
            when (currentDestination) {
                MainDestination.Connection -> ConnectionPage(state, onAction, contentPadding)
                MainDestination.Endpoints -> EndpointsPage(state, onAction, contentPadding)
                MainDestination.Settings -> SettingsPage(onAction, contentPadding)
            }
        }
    }

    state.aboutVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { onAction(MainAction.DismissAbout) },
            title = { Text(stringResource(R.string.about)) },
            text = { Text(version) },
            confirmButton = {
                TextButton(onClick = { onAction(MainAction.DismissAbout) }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun RowScope.FloatingToolbarItem(
    item: MainDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) selectedColor else Color.Transparent,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = stringResource(item.labelRes),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnectionPage(
    state: MainUiState,
    onAction: (MainAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(scaffoldPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.connection_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                text = if (state.running) state.forwarded else stringResource(R.string.tap_to_start),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FloatingActionButton(
                    onClick = { onAction(MainAction.ToggleConnection) },
                    modifier = Modifier.size(128.dp),
                    shape = CircleShape,
                    containerColor = if (state.running) {
                        Color(0xFF1B7A4E)
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.running) R.drawable.ic_outline_check_circle
                            else R.drawable.ic_outline_not_interested,
                        ),
                        contentDescription = if (state.running) {
                            stringResource(R.string.disconnect)
                        } else {
                            stringResource(R.string.connect)
                        },
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(if (state.running) R.string.running else R.string.stopped),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            ActionCard(
                title = stringResource(R.string.endpoint_region),
                description = stringResource(R.string.endpoint_region_summary),
                iconRes = R.drawable.ic_baseline_apps,
                highlighted = true,
                onClick = { onAction(MainAction.OpenProxy) },
            )
        }
        item {
            RoutingCard(mode = state.mode, onModeSelected = { onAction(MainAction.PatchMode(it)) })
        }
    }
}

@Composable
private fun EndpointsPage(
    state: MainUiState,
    onAction: (MainAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(scaffoldPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.endpoint_config_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.endpoint_config_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.hasUpdatableProfiles) {
                    TextButton(onClick = { onAction(MainAction.UpdateAllProfiles) }) {
                        Text(stringResource(R.string.update))
                    }
                }
            }
        }
        if (state.hasProviders) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                ActionCard(
                    title = stringResource(R.string.providers),
                    description = stringResource(R.string.endpoint_config_subtitle),
                    iconRes = R.drawable.ic_baseline_swap_vertical_circle,
                    highlighted = true,
                    onClick = { onAction(MainAction.OpenProviders) },
                )
            }
        }
        items(state.profiles, key = { it.uuid }) { profile ->
            ProfileCard(
                profile = profile,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun SettingsPage(
    onAction: (MainAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(scaffoldPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            SettingsGroup(
                listOf(
                    SettingItem(R.string.app, R.drawable.ic_baseline_settings, MainAction.OpenAppSettings),
                    SettingItem(R.string.network, R.drawable.ic_baseline_dns, MainAction.OpenNetworkSettings),
                    SettingItem(R.string.override, R.drawable.ic_baseline_extension, MainAction.OpenOverrideSettings),
                    SettingItem(R.string.meta_features, R.drawable.ic_baseline_meta, MainAction.OpenMetaFeatureSettings),
                ),
                onAction,
            )
        }
        item {
            SettingsGroup(
                listOf(
                    SettingItem(R.string.logs, R.drawable.ic_baseline_assignment, MainAction.OpenLogs),
                    SettingItem(R.string.help, R.drawable.ic_baseline_help_center, MainAction.OpenHelp),
                    SettingItem(R.string.about, R.drawable.ic_baseline_info, MainAction.OpenAbout),
                ),
                onAction,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    iconRes: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoutingCard(
    mode: TunnelState.Mode,
    onModeSelected: (TunnelState.Mode) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.routing_rules), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.routing_rules_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(R.string.rule_mode, mode == TunnelState.Mode.Rule, Modifier.weight(1f)) {
                    onModeSelected(TunnelState.Mode.Rule)
                }
                ModeChip(R.string.global_mode, mode == TunnelState.Mode.Global, Modifier.weight(1f)) {
                    onModeSelected(TunnelState.Mode.Global)
                }
                ModeChip(R.string.direct_mode, mode == TunnelState.Mode.Direct, Modifier.weight(1f)) {
                    onModeSelected(TunnelState.Mode.Direct)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(labelRes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, onAction: (MainAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = { onAction(MainAction.ActivateProfile(profile)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (profile.active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (profile.active) R.drawable.ic_outline_check_circle else R.drawable.ic_outline_inbox,
                ),
                contentDescription = null,
                tint = if (profile.active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile.type.name,
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
                    if (profile.imported && profile.type != Profile.Type.File) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update)) },
                            onClick = { menuExpanded = false; onAction(MainAction.UpdateProfile(profile)) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = { menuExpanded = false; onAction(MainAction.EditProfile(profile)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        onClick = { menuExpanded = false; onAction(MainAction.DuplicateProfile(profile)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuExpanded = false; onAction(MainAction.DeleteProfile(profile)) },
                    )
                }
            }
        }
    }
}

private data class SettingItem(val titleRes: Int, val iconRes: Int, val action: MainAction)

@Composable
private fun SettingsGroup(items: List<SettingItem>, onAction: (MainAction) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onAction(item.action) }) {
                        Icon(painterResource(item.iconRes), contentDescription = null)
                    }
                    TextButton(
                        onClick = { onAction(item.action) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = stringResource(item.titleRes),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun screenPadding(scaffoldPadding: PaddingValues): PaddingValues = PaddingValues(
    start = 24.dp,
    top = scaffoldPadding.calculateTopPadding() + 20.dp,
    end = 24.dp,
    bottom = scaffoldPadding.calculateBottomPadding() + 20.dp,
)

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    ClashTheme {
        MainScreen(
            state = MainUiState(
                profileName = "Demo",
                profiles = listOf(
                    Profile(
                        uuid = UUID.randomUUID(),
                        name = "Demo subscription",
                        type = Profile.Type.Url,
                        source = "https://example.com",
                        active = true,
                        interval = 0,
                        upload = 0,
                        download = 0,
                        total = 0,
                        expire = 0,
                        updatedAt = 0,
                        imported = true,
                        pending = false,
                    )
                ),
            ),
            onAction = {},
        )
    }
}
