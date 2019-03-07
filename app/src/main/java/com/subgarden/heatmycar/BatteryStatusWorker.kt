package com.subgarden.heatmycar

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.subgarden.heatmycar.repository.SmsRepository

/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class BatteryStatusWorker(val context : Context, params : WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        SmsRepository.checkBatteryStatus(context)
        return Result.success()
    }

}