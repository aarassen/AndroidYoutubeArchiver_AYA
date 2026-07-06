package com.adnanearrassen.ytarchiver.download

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adnanearrassen.ytarchiver.core.common.ApplicationScope
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed queue orchestrator. Keeps the number of concurrently
 * running downloads at or below the user's `maxSimultaneousDownloads` by only
 * enqueuing enough workers to fill the free slots; each finished worker calls
 * [scheduleQueue] again to pull the next item.
 */
@Singleton
class WorkManagerDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val downloadDao: DownloadDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : DownloadScheduler {

    private val mutex = Mutex()

    override fun scheduleQueue() {
        scope.launch {
            mutex.withLock {
                val settings = settingsRepository.settings.first()
                val active = downloadDao.activeCount()
                val slots = (settings.maxSimultaneousDownloads - active).coerceAtLeast(0)
                if (slots <= 0) return@withLock

                val queued = downloadDao.byStatus(DownloadStatus.QUEUED)
                    .sortedBy { it.queuePosition }
                    .take(slots)

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .setRequiresBatteryNotLow(settings.batterySaver)
                    .build()

                queued.forEach { item ->
                    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(Data.Builder().putLong(DownloadWorker.KEY_DOWNLOAD_ID, item.id).build())
                        .setConstraints(constraints)
                        .addTag(DownloadWorker.tag(item.id))
                        .build()
                    workManager.enqueueUniqueWork(
                        DownloadWorker.uniqueName(item.id),
                        ExistingWorkPolicy.KEEP,
                        request,
                    )
                }
            }
        }
    }

    override fun cancelWork(downloadId: Long) {
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(downloadId))
    }
}
