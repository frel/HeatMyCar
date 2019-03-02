package com.subgarden.heatmycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.subgarden.heatmycar.repository.SmsRepository


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d("####", "onReceive called. ${intent.action} $intent")
        }
        Log.i("####", "onReceive called. ${intent.action} $intent")

        val bundle = intent.extras ?: return

        val pdus = bundle.get("pdus") as Array<*>

        val messages = Array(pdus.size) { index ->
            SmsMessage.createFromPdu(pdus[index] as ByteArray)
        }

        // One SMS can be split into multiple messages. For short commands this will always be 1.
        val messageBody = messages.joinToString { it.messageBody }
        val address = messages[0].originatingAddress
        SmsRepository.handleSms(context, address, messageBody)
    }

}