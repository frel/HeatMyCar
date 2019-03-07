package com.subgarden.heatmycar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.subgarden.heatmycar.repository.StateRepository


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class HeatTimerReceiver : BroadcastReceiver() {

    companion object {
        fun startHeatTimer(context: Context, delayMs: Long) {
            Log.d("####", "Starting heat timer")
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(context, HeatTimerReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, 0, i, 0)
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), delayMs, pi)
        }

        fun cancelHeatTimer(context: Context) {
            Log.d("####", "Cancelling heat timer")
            val intent = Intent(context, HeatTimerReceiver::class.java)
            val sender = PendingIntent.getBroadcast(context, 0, intent, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(sender)
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