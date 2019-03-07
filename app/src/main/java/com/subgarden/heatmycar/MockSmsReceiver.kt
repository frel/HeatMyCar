package com.subgarden.heatmycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.subgarden.heatmycar.repository.SmsRepository


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class MockSmsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_HEAT = "com.subgarden.mock.sms.heat"
        const val ACTION_BATTERY = "com.subgarden.mock.sms.battery"
        const val ACTION_ABORT = "com.subgarden.mock.sms.abort"
        const val ACTION_STATUS = "com.subgarden.mock.sms.status"
        const val ACTION_RESET = "com.subgarden.mock.sms.reset"
        const val ACTION_BATTERY_WARNINGS = "com.subgarden.mock.sms.batterywarnings"
        const val ACTION_HELP = "com.subgarden.mock.sms.help"

        const val testPhoneNumber = "12345678"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("####", "MockSmsReceiver received action: $action")


        when (action) {
            ACTION_HEAT                -> SmsRepository.handleSms(context, testPhoneNumber, "heat")
            ACTION_BATTERY             -> SmsRepository.handleSms(context, testPhoneNumber, "battery")
            ACTION_ABORT               -> SmsRepository.handleSms(context, testPhoneNumber, "abort")
            ACTION_STATUS              -> SmsRepository.handleSms(context, testPhoneNumber, "status")
            ACTION_RESET               -> SmsRepository.handleSms(context, testPhoneNumber, "reset")
            ACTION_BATTERY_WARNINGS    -> SmsRepository.handleSms(context, testPhoneNumber, "batterywarnings")
            ACTION_HELP                -> SmsRepository.handleSms(context, testPhoneNumber, "help")
        }
    }
}