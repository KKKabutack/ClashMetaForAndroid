package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.databinding.Observable
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    sealed class Request {
        data object ToggleStatus : Request()
        data object OpenProxy : Request()
        data object OpenProviders : Request()
        data object OpenLogs : Request()
        data object OpenHelp : Request()
        data object OpenAbout : Request()
        data object OpenAppSettings : Request()
        data object OpenNetworkSettings : Request()
        data object OpenOverrideSettings : Request()
        data object OpenMetaFeatureSettings : Request()
        data object CreateProfile : Request()
        data object UpdateAllProfiles : Request()
        data class ActiveProfile(val profile: Profile) : Request()
        data class UpdateProfile(val profile: Profile) : Request()
        data class EditProfile(val profile: Profile) : Request()
        data class DuplicateProfile(val profile: Profile) : Request()
        data class DeleteProfile(val profile: Profile) : Request()
        data class PatchMode(val mode: TunnelState.Mode) : Request()
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    private val profileAdapter = ProfileAdapter(
        context,
        this::requestActiveProfile,
        this::showProfileMenu
    )

    private val rotateAnimation: Animation =
        AnimationUtils.loadAnimation(context, R.anim.rotate_infinite)

    private var suppressModeCallback = false
    private var allUpdating = false

    private val profileMenuHandler = object : ProfileMenuHandler {
        override fun requestUpdate(dialog: Dialog, profile: Profile) {
            requests.trySend(Request.UpdateProfile(profile))
            dialog.dismiss()
        }

        override fun requestEdit(dialog: Dialog, profile: Profile) {
            requests.trySend(Request.EditProfile(profile))
            dialog.dismiss()
        }

        override fun requestDuplicate(dialog: Dialog, profile: Profile) {
            requests.trySend(Request.DuplicateProfile(profile))
            dialog.dismiss()
        }

        override fun requestDelete(dialog: Dialog, profile: Profile) {
            requests.trySend(Request.DeleteProfile(profile))
            dialog.dismiss()
        }
    }

    override val root: View
        get() = binding.root

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
            binding.modeToggle.isEnabled = running
            val color = if (running) binding.colorClashStarted else binding.colorClashStopped
            binding.connectButton.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }

            suppressModeCallback = true
            val checkedId = when (mode) {
                TunnelState.Mode.Direct -> R.id.mode_direct
                TunnelState.Mode.Global -> R.id.mode_global
                else -> R.id.mode_rule
            }
            if (binding.modeToggle.checkedButtonId != checkedId) {
                binding.modeToggle.check(checkedId)
            }
            suppressModeCallback = false
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        profileAdapter.apply {
            patchDataSet(this::profiles, profiles, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }

        withContext(Dispatchers.Main) {
            binding.endpointsSync.visibility = if (updatable) View.VISIBLE else View.GONE
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.EditProfile(profile))
            }
        }
    }

    fun updateElapsed() {
        profileAdapter.updateElapsed()
    }

    fun requestUpdateAll() {
        allUpdating = true
        changeUpdateAllButtonStatus()
        requests.trySend(Request.UpdateAllProfiles)
    }

    fun finishUpdateAll() {
        allUpdating = false
        changeUpdateAllButtonStatus()
    }

    fun showEndpointsTab() {
        showTab(R.id.tab_endpoints)
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val aboutBinding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(aboutBinding.root)
                .show()
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    fun requestToggleStatus() = request(Request.ToggleStatus)
    fun requestOpenProxy() = request(Request.OpenProxy)
    fun requestOpenProviders() = request(Request.OpenProviders)
    fun requestCreateProfile() = request(Request.CreateProfile)
    fun requestOpenAppSettings() = request(Request.OpenAppSettings)
    fun requestOpenNetworkSettings() = request(Request.OpenNetworkSettings)
    fun requestOpenOverrideSettings() = request(Request.OpenOverrideSettings)
    fun requestOpenMetaFeatureSettings() = request(Request.OpenMetaFeatureSettings)
    fun requestOpenLogs() = request(Request.OpenLogs)
    fun requestOpenHelp() = request(Request.OpenHelp)
    fun requestOpenAbout() = request(Request.OpenAbout)

    init {
        binding.self = this
        binding.forwarded = "0 B"
        binding.bottomContentPadding = 0

        binding.colorClashStarted =
            context.resolveThemedColor(androidx.appcompat.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

        binding.profilesList.applyLinearAdapter(context, profileAdapter)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        binding.modeToggle.addOnButtonCheckedListener(
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked || suppressModeCallback) return@OnButtonCheckedListener
                val mode = when (checkedId) {
                    R.id.mode_direct -> TunnelState.Mode.Direct
                    R.id.mode_global -> TunnelState.Mode.Global
                    else -> TunnelState.Mode.Rule
                }
                requests.trySend(Request.PatchMode(mode))
            }
        )

        surface.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                updateBottomContentPadding()
            }
        })

        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBottomContentPadding()
        }

        showTab(R.id.tab_connection)
    }

    private fun updateBottomContentPadding() {
        val navHeight = binding.bottomNav.height.takeIf { it > 0 }
            ?: context.resources.getDimensionPixelSize(R.dimen.floating_nav_height)
        val margin = context.resources.getDimensionPixelSize(R.dimen.floating_nav_margin)
        binding.bottomContentPadding = surface.insets.bottom + navHeight + margin * 2

        val lp = binding.bottomNav.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        if (lp != null) {
            val bottom = surface.insets.bottom + margin
            if (lp.bottomMargin != bottom) {
                lp.bottomMargin = bottom
                binding.bottomNav.layoutParams = lp
            }
        }
    }

    private fun showTab(itemId: Int) {
        binding.pageConnection.isVisible = itemId == R.id.tab_connection
        binding.pageEndpoints.isVisible = itemId == R.id.tab_endpoints
        binding.pageSettings.isVisible = itemId == R.id.tab_settings

        if (binding.bottomNav.selectedItemId != itemId) {
            binding.bottomNav.selectedItemId = itemId
        }
    }

    private fun requestActiveProfile(profile: Profile) {
        requests.trySend(Request.ActiveProfile(profile))
    }

    private fun showProfileMenu(profile: Profile) {
        val dialog = AppBottomSheetDialog(context)
        val menuBinding = DialogProfilesMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        menuBinding.master = profileMenuHandler
        menuBinding.self = dialog
        menuBinding.profile = profile

        dialog.setContentView(menuBinding.root)
        dialog.show()
    }

    private fun changeUpdateAllButtonStatus() {
        if (allUpdating) {
            binding.endpointsSync.startAnimation(rotateAnimation)
        } else {
            binding.endpointsSync.clearAnimation()
        }
    }
}
