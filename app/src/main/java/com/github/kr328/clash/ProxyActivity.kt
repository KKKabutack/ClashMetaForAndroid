package com.github.kr328.clash

import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.compose.ClashTheme
import com.github.kr328.clash.design.compose.ProxyAction
import com.github.kr328.clash.design.compose.ProxyScreen
import com.github.kr328.clash.design.compose.ProxyUiState
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

/** Compose selector screen used both before and after the VPN connection starts. */
class ProxyActivity : BaseActivity<Design<*>>() {
    private val uiState = MutableStateFlow(ProxyUiState())
    private val actions = Channel<ProxyAction>(Channel.UNLIMITED)

    override suspend fun main() {
        setContent {
            val state by uiState.collectAsState()
            ClashTheme {
                ProxyScreen(state = state, onAction = actions::trySend)
            }
        }

        reloadGroups()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    if (it == Event.ProfileLoaded) reloadGroups()
                }
                actions.onReceive(::handleAction)
            }
        }
    }

    private suspend fun handleAction(action: ProxyAction) {
        when (action) {
            ProxyAction.Back -> finish()
            is ProxyAction.SelectGroup -> uiState.update { it.copy(selectedGroup = action.name) }
            is ProxyAction.SelectProxy -> {
                withClash { patchSelector(action.group, action.proxy.name) }
                reloadGroups(action.group)
            }
            is ProxyAction.TestGroup -> {
                uiState.update { it.copy(testingGroup = action.group) }
                try {
                    withClash { healthCheck(action.group) }
                } finally {
                    reloadGroups(action.group)
                    uiState.update { it.copy(testingGroup = null) }
                }
            }
        }
    }

    private suspend fun reloadGroups(preferredGroup: String? = uiState.value.selectedGroup) {
        val groups = withClash {
            queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                .associateWith { name -> queryProxyGroup(name, uiStore.proxySort) }
        }
        uiState.update {
            it.copy(
                groups = groups,
                selectedGroup = preferredGroup?.takeIf(groups::containsKey) ?: groups.keys.firstOrNull(),
            )
        }
    }

    override fun onBackInvoked() = finish()

    companion object {
        val intent = ProxyActivity::class.intent
    }
}
