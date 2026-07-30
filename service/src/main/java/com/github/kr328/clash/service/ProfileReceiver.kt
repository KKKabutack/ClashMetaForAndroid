package com.github.kr328.clash.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileUpdateCompleted
import com.github.kr328.clash.service.util.sendProfileUpdateFailed
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                Global.launch {
                    rescheduleAll(context)
                }
            }
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
                val uuid = intent.uuid ?: return
                Global.launch {
                    val imported = ImportedDao().queryByUUID(uuid) ?: return@launch
                    schedule(context, imported)
                }
            }
        }
    }

    companion object {
        private val lock = Mutex()
        private var initialized: Boolean = false

        suspend fun rescheduleAll(context: Context) = lock.withLock {
            if (initialized)
                return

            initialized = true

            Log.i("Reschedule all profiles update")

            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)

            ImportedDao().queryAllUUIDs()
                .mapNotNull { ImportedDao().queryByUUID(it) }
                .filter { it.type != Profile.Type.File }
                .forEach { scheduleNext(context, it) }
        }

        fun cancelNext(context: Context, imported: Imported) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(ProfileUpdateWorker.uniqueWorkName(imported.uuid))
        }

        fun schedule(context: Context, imported: Imported) {
            val request = OneTimeWorkRequestBuilder<ProfileUpdateWorker>()
                .setInputData(workDataOf(ProfileUpdateWorker.KEY_UUID to imported.uuid.toString()))
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    ProfileUpdateWorker.uniqueWorkName(imported.uuid),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        fun scheduleNext(context: Context, imported: Imported) {
            cancelNext(context, imported)

            if (imported.interval < TimeUnit.MINUTES.toMillis(15))
                return

            val current = System.currentTimeMillis()
            val last = context.importedDir
                .resolve(imported.uuid.toString())
                .resolve("config.yaml")
                .lastModified()

            // file not existed
            if (last < 0)
                return

            val interval = (imported.interval - (current - last)).coerceAtLeast(0)

            val request = OneTimeWorkRequestBuilder<ProfileUpdateWorker>()
                .setInputData(workDataOf(ProfileUpdateWorker.KEY_UUID to imported.uuid.toString()))
                .setInitialDelay(interval, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    ProfileUpdateWorker.uniqueWorkName(imported.uuid),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        private suspend fun reset() = lock.withLock {
            initialized = false
        }

        private const val WORK_TAG = "profile-update"
    }
}
