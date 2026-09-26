package com.procmon.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.procmon.app.R

/**
 * Keeps the embedded [WebServer] alive as a foreground service so the PWA can
 * still fetch data when the app isn't in the foreground.
 */
class ServerService : Service() {

    private val server by lazy { WebServer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        runCatching { server.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { server.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ProcMon server running")
            .setContentText("Serving on http://localhost:${WebServer.DEFAULT_PORT}")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.server_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "procmon_server"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.procmon.app.STOP"

        fun start(context: Context) {
            val i = Intent(context, ServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ServerService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
