package com.subgarden.heatmycar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.subgarden.heatmycar.repository.StateRepository
import java.util.concurrent.TimeUnit


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class HeatTimerReceiver : BroadcastReceiver() {

    companion object {

    const val ACTION_HEAT = "com.subgarden.alarms.heat"

        fun startHeatTimer(context: Context, minutes: Long) {
            Log.d("####", "Starting heat timer")
            val alarmIntent = Intent(ACTION_HEAT)

            val pendingIntent = PendingIntent.getBroadcast(context, 1337, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.cancel(pendingIntent)

            val triggerAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes)
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= 19) {
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        fun cancelHeatTimer(context: Context) {
            Log.d("####", "Cancelling heat timer")
            val alarmIntent = Intent(ACTION_HEAT)
            val pendingIntent = PendingIntent.getBroadcast(context, 1337, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, HeatTimerReceiver::class.java.simpleName)
        wl.acquire(60 * 1000 /* 60 seconds */)

        // Update state to idle
        Log.d("####", "Heat timer finished. Updating state to Idle.")
        StateRepository.updateState(StateRepository.State.Idle)

        wl.release()
    }

}