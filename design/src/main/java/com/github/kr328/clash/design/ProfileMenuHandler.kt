package com.github.kr328.clash.design

import android.app.Dialog
import com.github.kr328.clash.service.model.Profile

interface ProfileMenuHandler {
    fun requestUpdate(dialog: Dialog, profile: Profile)
    fun requestEdit(dialog: Dialog, profile: Profile)
    fun requestDuplicate(dialog: Dialog, profile: Profile)
    fun requestDelete(dialog: Dialog, profile: Profile)
}
