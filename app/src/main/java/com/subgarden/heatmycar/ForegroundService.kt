package com.subgarden.heatmycar

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.util.Log
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.subgarden.heatmycar.repository.StateRepository
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class ForegroundService : Service() {

    companion object {
        private const val TAG = "####"
        private const val NOTIFICATION_CHANNEL_ID = "1"
        private const val NOTIFICATION_CHANNEL_NAME = "Service running notification"
    }

    override fun onBind(intent: Intent): IBinder {
        // TODO: Return the communication channel to the service.
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Foreground service onCreate().")
        startForegroundService()

        StateRepository.state.filter { it is StateRepository.State.Heating }
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .map { it as StateRepository.State.Heating }
                .subscribe {
                    HeatTimerReceiver.startHeatTimer(this, 31)
                }

        StateRepository.state.filter { it == StateRepository.State.Abort }
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .subscribe {
                    Log.d("####", "Abort received. Cancelling heat timer.")
                    HeatTimerReceiver.cancelHeatTimer(this)
                }

        val batteryCheckWork = PeriodicWorkRequest.Builder(BatteryStatusWorker::class.java, 5, TimeUnit.HOURS).build()
        WorkManager.getInstance().enqueue(batteryCheckWork)
    }

    override fun onDestroy() {
        Log.d(TAG, "Foreground service onDestroy().")
    }

    /* Used to build and start foreground service. */
    private fun startForegroundService() {
        Log.d(TAG, "Start foreground service.")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, importance)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val resultIntent = Intent(this, MainActivity::class.java)
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setAutoCancel(false)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_directions_car_black_24dp)
                .setTicker("HeatMyCar")
                .setContentTitle("HeatMyCar is running")
                .setContentText("Waiting for things to do ☺️")
                .setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_SOUND)
                .setContentInfo("Info")
                .setContentIntent(resultPendingIntent)

        // Start foreground service.
        startForeground(1, builder.build())
    }

    private fun stopForegroundService() {
        Log.d(TAG, "Stop foreground service.")

        // Stop foreground service and remove the notification.
        stopForeground(true)

        // Stop the foreground service.
        stopSelf()
    }

}