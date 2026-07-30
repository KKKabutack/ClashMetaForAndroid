package com.github.kr328.clash.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.sendProfileUpdateCompleted
import com.github.kr328.clash.service.util.sendProfileUpdateFailed
import java.util.UUID

class ProfileUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uuidString = inputData.getString(KEY_UUID) ?: return Result.failure()
        val uuid = try {
            UUID.fromString(uuidString)
        } catch (e: IllegalArgumentException) {
            return Result.failure()
        }

        val imported = ImportedDao().queryByUUID(uuid) ?: return Result.failure()

        if (imported.type == Profile.Type.File) {
            return Result.failure()
        }

        return try {
            ProfileProcessor.update(applicationContext, uuid, null)
            applicationContext.sendProfileUpdateCompleted(uuid)
            ProfileReceiver.scheduleNext(applicationContext, imported)
            Result.success()
        } catch (e: Exception) {
            Log.e("Profile update failed: ${e.message}", e)
            applicationContext.sendProfileUpdateFailed(uuid, e.message ?: "Unknown")
            Result.failure()
        }
    }

    companion object {
        const val KEY_UUID = "uuid"

        fun uniqueWorkName(uuid: UUID): String = "profile-update-$uuid"
    }
}
