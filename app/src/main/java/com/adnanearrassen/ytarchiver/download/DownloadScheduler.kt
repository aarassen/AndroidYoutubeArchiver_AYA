package com.adnanearrassen.ytarchiver.download

/**
 * Abstraction over the mechanism that actually runs downloads (WorkManager).
 * Kept as an interface so the data layer can schedule work without depending on
 * WorkManager directly, and so it can be faked in tests.
 */
interface DownloadScheduler {
    /** Ensure the queue is being processed; schedules workers up to the
     *  configured max-simultaneous limit. */
    fun scheduleQueue()

    /** Stop the worker for a specific download (pause/cancel). */
    fun cancelWork(downloadId: Long)
}
