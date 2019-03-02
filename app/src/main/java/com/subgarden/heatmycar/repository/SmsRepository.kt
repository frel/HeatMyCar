package com.subgarden.heatmycar.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.support.v4.content.ContextCompat
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.subgarden.heatmycar.BuildConfig
import com.subgarden.heatmycar.WifiUtil
import com.subgarden.heatmycar.repository.StateRepository.lockScreen
import com.subgarden.heatmycar.repository.StateRepository.unlockScreen
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
@SuppressLint("StaticFieldLeak")
object SmsRepository {
    data class Contact(val name: String, val number: String, val starred: Boolean)

    data class SMS(val address: String, val contact: Contact, val message: String)

    private var lastPhoneNumber = ""
    private var batteryWarnings = 0
    private var batteryWarningsEnabled = true

    private val _messages = PublishProcessor.create<SMS>()
    private val messages: Flowable<Pair<SMS, StateRepository.State>> = _messages
            .subscribeOn(Schedulers.computation())
            .observeOn(Schedulers.computation())
            .doOnNext { lastPhoneNumber = it.address }
            .map { sms ->
                sms.copy(message = sms.message.toLowerCase(Locale.ENGLISH)) to StateRepository.state.blockingFirst()
            }

    lateinit var context: Context

    private fun String.isHeatMessage() =  this == "heat" || this == "varm"
    private fun String.isBatteryMessage() =  this == "battery" || this == "batteri"
    private fun String.isResetMessage() =  this == "reset" || this == "nullstill"
    private fun String.isAbortMessage() =  this == "abort"
    private fun String.isStatusMessage() =  this == "status"
    private fun String.isToggleBatteryWarningMessage() =  this == "batterywarnings" || this == "batteriadvarsler"
    private fun String.isHelpMessage() =  this == "help" || this == "hjelp"

    val mainThreadHandler = Handler(Looper.getMainLooper())

    init {
        Log.d("####", "SmsHandler")

        val threeHoursMs = 3 * 60 * 60 * 1000L
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                mainThreadHandler.post {
                    Log.d("####", "Battery charge timer triggered.")
                    // Only send sms between 9:00 and 22:00 hours
                    val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val acceptableHourOfDay = hourOfDay in 10..22
                    Log.d("####", "Hour of day is: $hourOfDay. It is an acceptable hour of day: $acceptableHourOfDay")

                    val battery = getBatteryStatus()
                    if (battery in 80..100) {
                        // Resetting warnings since the battery charge is good.
                        Log.d("####", "Charge above 80%. Resetting battery warnings counter.")
                        batteryWarnings = 0
                    } else if (batteryWarningsEnabled &&
                               acceptableHourOfDay &&
                               batteryWarnings < 1 &&
                               lastPhoneNumber.isNotEmpty()) {
                        if (battery in 1..10) {
                            sendSMS(lastPhoneNumber, "Jeg må lades :) Batterinivå er $battery%")
                            batteryWarnings++
                        }
                    }
                }
            }
        }, 0, threeHoursMs)

        messages.filter { it.first.message.isHeatMessage() && it.second is StateRepository.State.Heating }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map { it.first to it.second as StateRepository.State.Heating }
                .subscribe {
                    val elapsedMs = SystemClock.elapsedRealtime() - it.second.startingTime
                    val minutesAgo = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
                    val secondsAgo = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
                    val message = "Jeg varmer allerede :) Startet for $minutesAgo minutt(er) og $secondsAgo sekund(er) siden. Batterinivå på mobilen er ${getBatteryStatus()}%"
                    sendSMS(it.first.address, message)
                }

        messages.filter { it.first.message.isHeatMessage() && it.second !is StateRepository.State.Heating}
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .observeOn(Schedulers.io())
                .map {
                    WifiUtil.connectToWifi(context.applicationContext).blockingFirst()
                    it
                }
                .map {
                    unlockScreen(context)
                    it
                }
                .delay(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val sms = it.first
                    sendSMS(sms.address, "Varmer bilen i 30 minutter :) Batterinivå på mobilen er ${getBatteryStatus()}%")
                    launchOutlanderApp(sms)
                }

        messages.filter { it.first.message.isBatteryMessage() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .subscribe {
                    val battery = getBatteryStatus()
                    val message = "Batterinivå på mobilen er $battery% :)"
                    sendSMS(it.first.address, message)
                }

        messages.filter { it.first.message.isHelpMessage() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .subscribe {
                    val battery = getBatteryStatus()
                    val sms = it.first.message.toLowerCase(Locale.ENGLISH)
                    val message = if (sms == "help") {
                        "Available commands: heat, battery, reset, abort, status, help. Battery level $battery% :)"
                    } else {
                        "Mulige kommandoer: varm, batteri, nullstill, abort, status, hjelp. Batterinivå på mobilen er $battery% :)"
                    }
                    sendSMS(it.first.address, message)
                }

        messages.filter { it.first.message.isAbortMessage() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .subscribe {
                    val sms = it.first
                    val state = it.second
                    val battery = getBatteryStatus()
                    when (state) {
                        is StateRepository.State.Heating -> {
                            StateRepository.abort()
                            launchOutlanderApp(sms)
                            val message = "Varming deaktivert :) Batterinivå på mobilen er $battery%"
                            sendSMS(sms.address, message)
                        }
                        StateRepository.State.Abort -> {
                            val message = "Deaktiverer allerede varming :) Batterinivå på mobilen er $battery%"
                            sendSMS(sms.address, message)
                        }
                        else -> {
                            val message = "Varmer ikke? :/ Batterinivå på mobilen er $battery%"
                            sendSMS(sms.address, message)
                        }
                    }
                }

        messages.filter { it.first.message.isStatusMessage() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .subscribe {
                    val sms = it.first
                    val state = it.second
                    val battery = getBatteryStatus()
                    val message = if (state is StateRepository.State.Heating) {
                        val elapsedMs = SystemClock.elapsedRealtime() - state.startingTime
                        val minutesAgo = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
                        val secondsAgo = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
                        "Status: ${state.name}. Startet $minutesAgo minutt(er) og $secondsAgo sekund(er) siden. Batterinivå på mobilen er $battery%"
                    } else {
                        "Status: ${state.name}. Batterinivå på mobilen er $battery%"
                    }
                    sendSMS(sms.address, message)
                }

        messages.filter { it.first.message.isResetMessage() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .subscribe {
                    val sms = it.first
                    val state = it.second
                    val battery = getBatteryStatus()
                    StateRepository.updateState(StateRepository.State.Ready)
                    val message = "Nullstiller :) Status: ${state.name}. Batterinivå på mobilen er $battery%"
                    sendSMS(sms.address, message)
                }

        messages.filter { it.first.message.isToggleBatteryWarningMessage() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    throw IllegalStateException("#### ${it.message}")
                }
                .subscribe {
                    val sms = it.first
                    val battery = getBatteryStatus()
                    val warningsEnabled = toggleBatteryWarnings()
                    val enabledLabel = if (warningsEnabled) "på" else "av"
                    val message = "Batteriadvarsler er nå skrudd $enabledLabel. Batterinivå på mobilen er $battery%"
                    sendSMS(sms.address, message)
                }
    }

    private fun toggleBatteryWarnings(): Boolean {
        val current = batteryWarningsEnabled
        batteryWarningsEnabled = !current
        return batteryWarningsEnabled
    }

    private fun launchOutlanderApp(sms: SMS) {
        val outlanderAppIntent = context.packageManager.getLaunchIntentForPackage("com.inventec.iMobile1")
        if (outlanderAppIntent == null) {
            Toast.makeText(context, "Unable to open Outlander Remote App", Toast.LENGTH_LONG).show()
            StateRepository.updateState(StateRepository.State.MissingApp)
        }

        try {
            ContextCompat.startActivity(context, outlanderAppIntent, null)
        } catch (e: RuntimeException) {
            Log.d("####", "Unable to open Outlander PHEV Remote app ${e.message}")
            sendSMS(sms.address, "Greide ikke åpne Outlander PEHV Remote :/")
            lockScreen(context)
        }
    }

    fun handleSms(context: Context, address: String, messageBody: String) {
        SmsRepository.context = context.applicationContext
        // Check if number is from favourite contact
        val allContacts = getAllContacts()
        val noFavourites = allContacts.none { it.starred }
        if (noFavourites) {
            Toast.makeText(context, "No favourite contacts added", Toast.LENGTH_LONG).show()
            return
        }

        val contact = allContacts.find { PhoneNumberUtils.compare(it.number, address) && it.starred }
                      ?: run { Log.d("####", "Declining message from $address"); return }

        Log.d("####", "Accepting message from $address - ${contact.name} (${contact.number})")
        val message = "Incoming SMS: ($address) $messageBody"
        Log.d("####", message)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        val sms = SMS(address, contact, messageBody)

        _messages.offer(sms)
    }

    private fun getBatteryStatus() : Int {
        // Sticky event. No receiver needed.
        val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val batteryPct = level / scale.toFloat()

        return (batteryPct * 100).toInt()
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        val debugMessage = "<- Sending SMS: $message to $phoneNumber"
        Log.d("####", debugMessage)
        if (BuildConfig.DEBUG) Toast.makeText(context, debugMessage, Toast.LENGTH_LONG).show()

        val sms = SmsManager.getDefault()
        sms.sendTextMessage(phoneNumber, null, message, null, null)

    }

    private fun getAllContacts(): List<Contact> {
        val projections = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                  ContactsContract.CommonDataKinds.Phone.NUMBER,
                                  ContactsContract.CommonDataKinds.Phone.STARRED)

        val phones = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                                   projections,
                                                   null,
                                                   null,
                                                   null)
        return phones.use { cursor ->
            generateSequence {
                if (cursor.moveToNext()) cursor else null }
                    .map {
                        Contact(
                                it.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)),
                                it.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)),
                                it.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)) == 1)

                    }.toList()
        }

    }

}