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
import com.example.data.local.SecurityPreferences
import com.example.model.DeviceRole
import com.example.network.LocalP2PCommunication
import com.example.network.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class KidLockDeviceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null
    private lateinit var securityPrefs: SecurityPreferences
    private val p2pCommunication = LocalP2PCommunication()

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
            if (now - lastForegroundCallTime < 1000) {
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

            // Move app task to front using ActivityManager
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val tasks = am?.appTasks
                if (!tasks.isNullOrEmpty()) {
                    for (task in tasks) {
                        task.moveToFront()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ActivityManager moveToFront error: ${e.message}")
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                NotificationHelper.NOTIF_ID_LOCK_ALERT,
                intent,
                pendingIntentFlags
            )

            // Trigger high-priority Full Screen Intent notification (bypasses Android 10+ background activity restrictions)
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                val fullScreenNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_LOCK_ALERT)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.status_locked))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                notificationManager?.notify(NotificationHelper.NOTIF_ID_LOCK_ALERT, fullScreenNotification)
            } catch (e: Exception) {
                Log.w(TAG, "FullScreenIntent notification error: ${e.message}")
            }

            // Also send PendingIntent and direct startActivity for maximum compatibility across all Android versions
            try {
                fullScreenPendingIntent.send()
            } catch (e: Exception) {
                Log.w(TAG, "PendingIntent send error: ${e.message}")
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
        securityPrefs = SecurityPreferences(applicationContext)
        Log.d(TAG, "KidLock background service created")
        startBackgroundTimerMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification()
        startForeground(NOTIF_SERVICE_ID, notification)
        return START_STICKY
    }

    private fun startBackgroundTimerMonitor() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var heartbeatTickCounter = 0
            while (isActive) {
                delay(1000)
                val isLocked = securityPrefs.isChildLocked()
                val isChild = securityPrefs.getDeviceRole() == DeviceRole.CHILD

                if (isLocked && isChild) {
                    // Bring MainActivity to front if app is locked and child attempts to switch away
                    bringAppToForeground(applicationContext)
                } else if (!isLocked) {
                    val unlockedUntil = securityPrefs.getUnlockedUntilTimestamp()
                    if (unlockedUntil > 0) {
                        val now = System.currentTimeMillis()
                        val remainingSecs = ((unlockedUntil - now) / 1000).toInt()
                        if (remainingSecs <= 0) {
                            Log.d(TAG, "Background time expired! Locking tablet and bringing MainActivity to front.")
                            securityPrefs.setChildLocked(true)
                            securityPrefs.setUnlockedUntilTimestamp(0L)
                            bringAppToForeground(applicationContext)
                        }
                    }
                }

                // Periodically send Heartbeat P2P status to Parent device every 3 seconds
                heartbeatTickCounter++
                if (heartbeatTickCounter >= 3) {
                    heartbeatTickCounter = 0
                    val parentIp = securityPrefs.getPairedParentIp()
                    val parentPort = securityPrefs.getPairedParentPort()
                    if (parentIp.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val unlockedUntil = securityPrefs.getUnlockedUntilTimestamp()
                        val remainingSecs = if (!securityPrefs.isChildLocked() && unlockedUntil > now) {
                            ((unlockedUntil - now) / 1000).toInt()
                        } else 0

                        val payload = JSONObject().apply {
                            put("type", "HEARTBEAT")
                            put("childDeviceId", securityPrefs.getDeviceId())
                            put("isLocked", securityPrefs.isChildLocked())
                            put("remainingSeconds", remainingSecs)
                            put("battery", 90)
                            put("theme", securityPrefs.getActiveThemeId())
                        }
                        p2pCommunication.sendMessage(parentIp, parentPort, payload)
                    }
                }
            }
        }
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
        timerJob?.cancel()
        Log.d(TAG, "KidLock background service stopped")
    }
}
