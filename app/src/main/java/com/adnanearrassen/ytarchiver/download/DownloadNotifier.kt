package com.adnanearrassen.ytarchiver.download

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.adnanearrassen.ytarchiver.R
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.core.common.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the ongoing progress notification shown while a download runs. */
@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun buildProgress(
        title: String,
        percent: Int,
        speedBytesPerSec: Long,
        indeterminate: Boolean = false,
    ): Notification =
        NotificationCompat.Builder(context, NotificationChannels.DOWNLOADS)
            .setContentTitle(title)
            .setContentText(
                if (indeterminate) "Preparing…"
                else "$percent% · ${Formatters.speed(speedBytesPerSec)}"
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .build()

    fun notificationIdFor(downloadId: Long): Int = (downloadId % Int.MAX_VALUE).toInt() + 1000
}
