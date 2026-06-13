package com.example.listacompracompose

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {

    private const val REQUEST_CODE = 2001

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("compra_settings", Context.MODE_PRIVATE)
            .getBoolean("reminder_enabled", false)

    fun getReminderDay(context: Context): Int =
        context.getSharedPreferences("compra_settings", Context.MODE_PRIVATE)
            .getInt("reminder_day", Calendar.SATURDAY)   // default: sábado

    fun getReminderHour(context: Context): Int =
        context.getSharedPreferences("compra_settings", Context.MODE_PRIVATE)
            .getInt("reminder_hour", 10)   // default: 10:00

    fun schedule(context: Context, dayOfWeek: Int, hour: Int) {
        val prefs = context.getSharedPreferences("compra_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("reminder_enabled", true)
            .putInt("reminder_day", dayOfWeek)
            .putInt("reminder_hour", hour)
            .apply()

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context)

        val trigger = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            // If the time is already past this week, move to next week
            if (timeInMillis < System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                AlarmManager.INTERVAL_DAY * 7,
                intent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                AlarmManager.INTERVAL_DAY * 7,
                intent
            )
        }
    }

    fun cancel(context: Context) {
        context.getSharedPreferences("compra_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("reminder_enabled", false).apply()
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_REMINDER },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val dayNames = listOf(
        Calendar.MONDAY    to "Lunes",
        Calendar.TUESDAY   to "Martes",
        Calendar.WEDNESDAY to "Miércoles",
        Calendar.THURSDAY  to "Jueves",
        Calendar.FRIDAY    to "Viernes",
        Calendar.SATURDAY  to "Sábado",
        Calendar.SUNDAY    to "Domingo"
    )
}
