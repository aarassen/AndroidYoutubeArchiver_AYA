package com.adnanearrassen.ytarchiver.core.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.adnanearrassen.ytarchiver.R

object NotificationChannels {
    const val DOWNLOADS = "downloads"
    const val PLAYBACK = "playback"
    const val UPDATES = "updates"

    fun createAll(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOADS,
                context.getString(R.string.channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_downloads_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PLAYBACK,
                context.getString(R.string.channel_playback_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_playback_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATES,
                context.getString(R.string.channel_updates_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = context.getString(R.string.channel_updates_desc) }
        )
    }
}
