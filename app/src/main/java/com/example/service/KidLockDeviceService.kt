package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.network.NotificationHelper

class KidLockDeviceService : Service() {

    companion object {
        private const val TAG = "KidLockService"
        private const val NOTIF_SERVICE_ID = 9901
        private var lastForegroundCallTime = 0L

        fun startService(context: Context) {
            try {
                val intent = Intent(context, KidLockDeviceService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting KidLockDeviceService: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, KidLockDeviceService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping KidLockDeviceService: ${e.message}")
            }
        }

        fun bringAppToForeground(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastForegroundCallTime < 1500) {
                Log.d(TAG, "bringAppToForeground throttled")
                return
            }
            lastForegroundCallTime = now

            try {
                // Wake screen if sleeping
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager?.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "KidLock:RemoteLockWakeLock"
                )
                wakeLock?.acquire(3000)
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock acquire error: ${e.message}")
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("EXTRA_FORCE_LOCK", true)
            }
            try {
                context.startActivity(intent)
                Log.d(TAG, "Successfully requested MainActivity to come to foreground")
            } catch (e: Exception) {
                Log.e(TAG, "Error bringing MainActivity to foreground: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KidLock background service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification()
        startForeground(NOTIF_SERVICE_ID, notification)
        return START_STICKY
    }

    private fun createServiceNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CHILD_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.break_time_title))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KidLock background service stopped")
    }
}
