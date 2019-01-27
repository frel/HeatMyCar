package com.subgarden.heatmycar

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK

/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "####"
    }

    sealed class State(val name: String) {
        object Uninitialized : State("Uninitialized")
        object Idle : State("Idle")
        object Starting : State("Starting")
        object Ready : State("Ready")
        object Heating: State("Heating")
        object DemoScreen : State("DemoScreen")
        object Error: State("Error")
    }

    private var state: State = State.Uninitialized
        set(value) {
            field = value
            Log.d(TAG, "Updating state to ${value.name}")
        }

    private val demoMode = BuildConfig.DEBUG

    fun AccessibilityNodeInfo.performClick() {
        performAction(ACTION_CLICK)
        Log.d(TAG, "Performing click on view: $text")
    }

    private var shouldEnableHeater = true

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.inventec.iMobile1") {
            Log.d(TAG, "Ignoring events from ${event.packageName}")
            return
        }

        Log.i(TAG, "onAccessibilityEvent: " + event.eventType)

        if (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED == event.eventType) {
            // Opening app

            when (state) {
                State.Uninitialized -> {
                    state = State.Starting
                    shouldEnableHeater = true
                }
//                is State.Idle          -> state = State.Starting
            }

            val okNode = event.source.findAccessibilityNodeInfosByText("ok")
            if (okNode.isNotEmpty()) {
                shouldEnableHeater = false
                okNode[0].performClick()
            }

            // TODP Feiler med å finne "Please choose climate control" osv "ok" button.
            val demoButtonNode = event.source.findAccessibilityNodeInfosByText("demo")
            if (demoButtonNode.isNotEmpty()) {
                state = State.DemoScreen
                if (demoMode) {
                    demoButtonNode[0].performClick()
                }
            }

            val climateOnOffButtonNode = event.source.findAccessibilityNodeInfosByText("climate on/off")
            if (climateOnOffButtonNode.isEmpty()) {
                Log.d(TAG, "Not the main screen")
                // TODO ny state for demo mode?
            } else {
                state = State.Ready
                Log.d(TAG, "Is on the main screen")
                if (state == State.Ready && shouldEnableHeater) {
                    climateOnOffButtonNode[0].performClick()
                }
            }

        }


        debugEvent(event)
    }

    private fun debugEvent(event: AccessibilityEvent) {
        var eventText: String = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "Clicked: "
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "Focused: "
            else -> ""
        }

        eventText += event.contentDescription

        Log.d(TAG, "DebugEvent(${event.eventType}): $eventText")
    }

    public override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected: ")
    }

    override fun onInterrupt() {

        Log.i(TAG, "onInterrupt")

    }

}