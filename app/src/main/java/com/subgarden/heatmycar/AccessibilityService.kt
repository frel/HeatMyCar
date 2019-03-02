package com.subgarden.heatmycar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.widget.Toast
import com.subgarden.heatmycar.repository.StateRepository
import com.subgarden.heatmycar.repository.StateRepository.State.*
import com.subgarden.heatmycar.repository.StateRepository.lockScreen
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class AccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "####"
    }

    private fun getActionDelay() : Long {
        return this.applicationContext.getSharedPreferences("default", AppCompatActivity.MODE_PRIVATE)
                .getInt(MainActivity.ACTION_DELAY, 20)
                .toLong()
    }

    private fun launchOutlanderApp() {
        val outlanderAppIntent = packageManager.getLaunchIntentForPackage("com.inventec.iMobile1")
        if (outlanderAppIntent == null) {
            Toast.makeText(this, "Unable to open Outlander Remote App", Toast.LENGTH_LONG).show()
            StateRepository.updateState(StateRepository.State.MissingApp)
        }

        try {
            ContextCompat.startActivity(this, outlanderAppIntent, null)
        } catch (e: RuntimeException) {
            Log.d("####", "Unable to open Outlander PHEV Remote app ${e.message}")
            lockScreen(this)
        }
    }

    private var climateOnOffButtonNode: AccessibilityNodeInfo? = null


    private fun AccessibilityNodeInfo.performClick() {
        performAction(ACTION_CLICK)
        Log.d(TAG, "Performing click on view: $text")
    }

    private val state: StateRepository.State
        get() = StateRepository.state.blockingFirst()


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.inventec.iMobile1") {
            Log.d(TAG, "Ignoring events from ${event.packageName}")
            return
        }

        if (!StateRepository.serviceEnabled().blockingFirst()) {
            Log.d(TAG, "Service disabled from UI. Ignoring events from ${event.packageName}")
            return
        }

        if (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED == event.eventType ||
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED == event.eventType ||
            AccessibilityEvent.TYPE_WINDOWS_CHANGED == event.eventType) {

            if (event.source == null) {
                Log.d(TAG, "Event source is null. Aborting. " + event.eventType)

                return
            }

            if (AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED == event.eventType) {
                val climateOnOffButtonNodeList = event.source.findAccessibilityNodeInfosByText("climate on/off")
                if (climateOnOffButtonNodeList.isNotEmpty()) {

                    if (isHeating(event)) {
                        Log.d(TAG, "1 UI says it's heating.")
                        if (state != Abort && state !is Heating) {
                            Log.d(TAG, "It looks like the UI says it's heating. Forcing state.")
                            StateRepository.updateState(Heating(SystemClock.elapsedRealtime()))
                        }
                    } else {
                        Log.d(TAG, "1 UI says it's not heating.")
                    }

                }
                return
            }

            val okNode = event.source.findAccessibilityNodeInfosByText("ok")
            if (okNode.isNotEmpty()) {
                okNode[0].performClick()
            }

            val climateOnOffButtonNodeList = event.source.findAccessibilityNodeInfosByText("Climate ON/OFF")
            if (climateOnOffButtonNodeList.isEmpty()) {
                Log.d(TAG, "Not the main screen")

            } else {

//                if (isHeating(event) && event.eventType != TYPE_WINDOW_STATE_CHANGED) {
//                    Log.d(TAG, "2 UI says it's heating.")
//                    if (state != Abort && state != Heating) {
//                        Log.d(TAG,  "It looks like the UI says it's heating. Forcing state.")
//                        StateRepository.updateState(Heating)
//                        lockScreen(this)
//                        WifiUtil.disconnectFromWifi(this)
//                    }
//                } else if (event.eventType != TYPE_WINDOW_STATE_CHANGED) {
//                    Log.d(TAG, "2 UI says it's not heating.")
//                    StateRepository.updateState(Idle)
//                }

                climateOnOffButtonNode = climateOnOffButtonNodeList[0]

                Log.d(TAG, "Is on the main screen")

                val debugState = state
                when (state) {
                    Abort -> {
                        Log.d(TAG, "Doing nothing.")
                    }
                    is Heating -> {
                        Log.d(TAG, "Already heating, ignoring.")
                    }
                    Uninitialized, Ready, Idle -> {

                        Log.d(TAG, "Waiting ${getActionDelay()} seconds before clicking heat...")
                        Completable.timer(getActionDelay(), TimeUnit.SECONDS)
                                .subscribeOn(Schedulers.io())
                                .observeOn(Schedulers.io())
                                .subscribe {
                                    climateOnOffButtonNode?.performClick()
                                    StateRepository.updateState(Heating(SystemClock.elapsedRealtime()))

                                    Log.d(TAG, "Waiting ${getActionDelay()} seconds to open app, lock screen and disconnect WiFi.")

                                    Completable.timer(getActionDelay(), TimeUnit.SECONDS)
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(Schedulers.io())
                                            .subscribe {
                                                Log.d(TAG, "Locking screen and disconnecting WiFi.")
                                                startActivity(Intent(this, MainActivity::class.java).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                                lockScreen(this)
                                                WifiUtil.disconnectFromWifi(this)
                                            }

                                }

                    }
                    else -> StateRepository.updateState(Ready)
                }

            }

        } else {
            Log.i(TAG, "Unhandled event " + event.eventType)
        }
    }

    private fun isHeating(event: AccessibilityEvent): Boolean {
        val diff1 = isHeatingInternal(event)
        Thread.sleep(200)

        val diff2 = isHeatingInternal(event)
        Thread.sleep(223)

        val diff3 = isHeatingInternal(event)
        Thread.sleep(132)

        val diff4 = isHeatingInternal(event)
        Thread.sleep(57)

        val diff5 = isHeatingInternal(event)
        Thread.sleep(93)

        val diff6 = isHeatingInternal(event)

        // Check if there has been a change
        return (diff1 != diff2 || diff1 != diff3 || diff1 != diff4 || diff1 != diff5 || diff1 != diff6)
    }

    data class ImageDiff(
            val image1: Boolean,
            val image2: Boolean,
            val image3: Boolean,
            val image4: Boolean)

    private fun isHeatingInternal(event: AccessibilityEvent): ImageDiff {
        val viewIds = arrayOf(
                "com.inventec.iMobile1:id/image1",
                "com.inventec.iMobile1:id/image2",
                "com.inventec.iMobile1:id/image3",
                "com.inventec.iMobile1:id/image4")

        return ImageDiff(
                event.source.findAccessibilityNodeInfosByViewId(viewIds[0]).isNotEmpty(),
                event.source.findAccessibilityNodeInfosByViewId(viewIds[1]).isNotEmpty(),
                event.source.findAccessibilityNodeInfosByViewId(viewIds[2]).isNotEmpty(),
                event.source.findAccessibilityNodeInfosByViewId(viewIds[3]).isNotEmpty())
    }


    public override fun onServiceConnected() {
        StateRepository.setAccessibilityServiceEnabled(true)


        StateRepository.state
                .filter { it == Abort }
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map {
                    Log.d("####", "this.applicationContext = ${this.applicationContext}")
                    WifiUtil.connectToWifi(this.applicationContext).blockingFirst()
                    it
                }
                .map {
                    StateRepository.unlockScreen(this)
                    launchOutlanderApp()
                }
                .delay(getActionDelay(), TimeUnit.SECONDS)
                .map {
                    Log.d(TAG, "Aborting on command from user. Waiting ${getActionDelay()} second to update state...")
                    climateOnOffButtonNode?.performClick()
                }
                .delay(getActionDelay(), TimeUnit.SECONDS)
                .subscribe {
                    Log.d(TAG, "Aborting complete.")
                    StateRepository.updateState(Idle)
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    lockScreen(this)
                    WifiUtil.disconnectFromWifi(this)
                }
    }

    override fun onInterrupt() {

        Log.i(TAG, "onInterrupt")

    }

}