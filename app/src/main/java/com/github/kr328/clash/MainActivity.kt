package com.github.kr328.clash

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.compose.ClashTheme
import com.github.kr328.clash.design.compose.MainAction
import com.github.kr328.clash.design.compose.MainScreen
import com.github.kr328.clash.design.compose.MainUiState
import com.github.kr328.clash.remote.LiveNotificationController
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Compose-first activity host for the application's three top-level destinations. */
class MainActivity : BaseActivity<Design<*>>() {
    private val uiState = MutableStateFlow(MainUiState())
    private val actions = Channel<MainAction>(Channel.UNLIMITED)

    override suspend fun main() {
        setContent {
            val state by uiState.collectAsState()
            ClashTheme {
                MainScreen(state = state, onAction = actions::trySend)
            }
        }

        refreshUi()
        refreshProfiles()

        val trafficTicker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> {
                            refreshUi()
                            refreshProfiles()
                        }
                        else -> Unit
                    }
                }
                actions.onReceive(::handleAction)
                if (clashRunning) {
                    trafficTicker.onReceive { refreshTraffic() }
                }
            }
        }
    }

    private suspend fun handleAction(action: MainAction) {
        when (action) {
            MainAction.ToggleConnection -> {
                if (clashRunning) stopClashService() else startClash()
            }
            MainAction.OpenProxy -> openProxy()
            MainAction.OpenProviders -> startActivity(ProvidersActivity::class.intent)
            MainAction.OpenLogs -> startActivity(
                if (LogcatService.running) LogcatActivity::class.intent else LogsActivity::class.intent
            )
            MainAction.OpenHelp -> startActivity(HelpActivity::class.intent)
            MainAction.OpenAbout -> uiState.update { it.copy(aboutVersion = queryAppVersionName()) }
            MainAction.DismissAbout -> uiState.update { it.copy(aboutVersion = null) }
            MainAction.OpenAppSettings -> startActivity(AppSettingsActivity::class.intent)
            MainAction.OpenNetworkSettings -> startActivity(NetworkSettingsActivity::class.intent)
            MainAction.OpenOverrideSettings -> startActivity(OverrideSettingsActivity::class.intent)
            MainAction.OpenMetaFeatureSettings -> startActivity(MetaFeatureSettingsActivity::class.intent)
            MainAction.CreateProfile -> startActivity(NewProfileActivity::class.intent)
            MainAction.UpdateAllProfiles -> updateAllProfiles()
            is MainAction.UpdateProfile -> withProfile { update(action.profile.uuid) }
            is MainAction.DeleteProfile -> withProfile { delete(action.profile.uuid) }
            is MainAction.EditProfile -> {
                startActivity(PropertiesActivity::class.intent.setUUID(action.profile.uuid))
            }
            is MainAction.ActivateProfile -> {
                withProfile {
                    if (action.profile.imported) {
                        setActive(action.profile)
                    } else {
                        toast(DesignR.string.active_unsaved_tips)
                    }
                }
            }
            is MainAction.DuplicateProfile -> {
                val uuid = withProfile { clone(action.profile.uuid) }
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            }
            is MainAction.PatchMode -> {
                withClash {
                    queryOverride(Clash.OverrideSlot.Session).also {
                        it.mode = action.mode
                        patchOverride(Clash.OverrideSlot.Session, it)
                    }
                }
                refreshUi()
            }
        }
    }

    /** Opens selector groups even while the VPN is disconnected. */
    private suspend fun openProxy() {
        if (!clashRunning) {
            val active = withProfile { queryActive() }
            if (active == null || !active.imported) {
                toast(DesignR.string.no_profile_selected)
                return
            }

            try {
                withClash { loadActiveProfile() }
            } catch (e: Exception) {
                toast(e.message ?: getString(DesignR.string.no_profile_selected))
                return
            }
        }

        startActivity(ProxyActivity::class.intent)
    }

    private suspend fun refreshUi() {
        val running = clashRunning
        val state = withClash { queryTunnelState() }
        val providers = withClash { queryProviders() }
        val activeName = withProfile { queryActive()?.name }

        uiState.update {
            it.copy(
                running = running,
                mode = state.mode,
                hasProviders = providers.isNotEmpty(),
                profileName = activeName,
            )
        }
    }

    private suspend fun refreshProfiles() {
        val profiles = withProfile { queryAll() }
        uiState.update {
            it.copy(
                profiles = profiles,
                hasUpdatableProfiles = profiles.any { profile ->
                    profile.imported && profile.type != Profile.Type.File
                },
            )
        }
    }

    private suspend fun refreshTraffic() {
        val forwarded = withClash { queryTrafficTotal().trafficTotal() }
        uiState.update { it.copy(forwarded = forwarded) }
    }

    private suspend fun updateAllProfiles() {
        uiState.update { it.copy(isUpdatingProfiles = true) }
        try {
            withProfile {
                queryAll().forEach { profile ->
                    if (profile.imported && profile.type != Profile.Type.File) update(profile.uuid)
                }
            }
        } finally {
            uiState.update { it.copy(isUpdatingProfiles = false) }
        }
    }

    private suspend fun startClash() {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            toast(DesignR.string.no_profile_selected)
            return
        }

        try {
            val vpnRequest = startClashService()
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest,
                )
                if (result.resultCode == RESULT_OK) startClashService()
            }
        } catch (_: Exception) {
            toast(DesignR.string.unable_to_start_vpn)
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private suspend fun queryAppVersionName(): String = withContext(Dispatchers.IO) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName +
            "\n" + Bridge.nativeCoreVersion().replace("_", "-")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
                if (isGranted) LiveNotificationController.refresh()
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                LiveNotificationController.refresh()
            }
        }
        setupShortcuts()
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if (uuid == null) return
        launch {
            val name = withProfile { queryByUUID(uuid)?.name }
            toast(getString(DesignR.string.toast_profile_updated_complete, name))
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null) return
        launch {
            val name = withProfile { queryByUUID(uuid)?.name }
            toast(getString(DesignR.string.toast_profile_updated_failed, name, reason))
        }
    }

    private fun setupShortcuts() {
        if (uiStore.hideAppIcon) return

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_all))
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags),
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_on))
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags),
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_off))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags),
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }
}
