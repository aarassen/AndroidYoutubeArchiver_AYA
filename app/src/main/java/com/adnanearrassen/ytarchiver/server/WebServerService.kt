package com.adnanearrassen.ytarchiver.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.adnanearrassen.ytarchiver.core.common.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the [MediaWebServer] process alive while the
 * server is running, with an ongoing notification and a Stop action.
 */
@AndroidEntryPoint
class WebServerService : Service() {

    @Inject lateinit var manager: WebServerManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            manager.stop()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val url = manager.state.value.primaryUrl ?: ""
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WebServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, com.adnanearrassen.ytarchiver.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.DOWNLOADS)
            .setContentTitle("Web server running")
            .setContentText(if (url.isNotEmpty()) "Open $url in a browser" else "Serving your archive")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.adnanearrassen.ytarchiver.server.STOP"
    }
}
