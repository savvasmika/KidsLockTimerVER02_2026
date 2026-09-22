package com.example.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_PARENT_ALERTS = "channel_kidlock_parent"
        const val CHANNEL_CHILD_STATUS = "channel_kidlock_child"
        const val CHANNEL_LOCK_ALERT = "channel_kidlock_lock_alert"
        const val NOTIF_ID_UNLOCK_REQ = 1001
        const val NOTIF_ID_PAIRING = 1002
        const val NOTIF_ID_LOCK_ALERT = 1003
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val parentChannel = NotificationChannel(
                CHANNEL_PARENT_ALERTS,
                context.getString(R.string.notif_channel_parent),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_parent_desc)
                enableVibration(true)
            }

            val childChannel = NotificationChannel(
                CHANNEL_CHILD_STATUS,
                context.getString(R.string.notif_channel_child),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_child_desc)
            }

            val lockAlertChannel = NotificationChannel(
                CHANNEL_LOCK_ALERT,
                context.getString(R.string.notif_channel_lock_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_lock_alert_desc)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            notificationManager.createNotificationChannel(parentChannel)
            notificationManager.createNotificationChannel(childChannel)
            notificationManager.createNotificationChannel(lockAlertChannel)
        }
    }

    fun triggerImmediateChildLockScreen(childName: String = "") {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_FORCE_LOCK", true)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_LOCK_ALERT,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_LOCK_ALERT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.locked_by_parent_title))
                .setContentText(context.getString(R.string.locked_by_parent_desc))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIF_ID_LOCK_ALERT, notification)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error in triggerImmediateChildLockScreen: ${e.message}")
        }
    }

    fun showUnlockRequestNotification(childName: String, requestId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_REQUEST_ID", requestId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PARENT_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_unlock_request_title))
            .setContentText(context.getString(R.string.notif_unlock_request_msg, childName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID_UNLOCK_REQ, notification)
    }

    fun showPairingRequestNotification(deviceName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_PAIRING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PARENT_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_pairing_title))
            .setContentText(context.getString(R.string.notif_pairing_msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID_PAIRING, notification)
    }
}
