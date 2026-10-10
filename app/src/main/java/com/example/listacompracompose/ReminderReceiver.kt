package com.example.listacompracompose

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only notify if the list has pending items
        val pending = try {
            AppDatabase.getInstance(context.applicationContext).productDao().getPendingSync()
        } catch (e: Exception) {
            emptyList()
        }
        if (pending.isEmpty()) return

        ensureChannel(context)

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val count = pending.size
        val preview = pending.take(3).joinToString(", ") { it.name }
        val body = if (count <= 3) preview else "$preview… (+${count - 3} más)"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("¿No olvidaste hacer la compra? 🛒")
            .setContentText("$count producto${if (count > 1) "s" else ""} pendiente${if (count > 1) "s" else ""}: $body")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$count producto${if (count > 1) "s" else ""} en tu lista:\n$body"
            ))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    companion object {
        const val CHANNEL_ID = "shopping_reminder"
        const val NOTIF_ID = 1001
        const val ACTION_REMINDER = "com.example.listacompracompose.REMINDER"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorio de compra",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Aviso semanal para no olvidar la lista" }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }
}
