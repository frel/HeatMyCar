package com.subgarden.heatmycar.repository

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers

/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
object StateRepository {

    sealed class State(val name: String) {
        data class Heating(val startingTime: Long): State("Heating")
        object Uninitialized : State("Uninitialized")
        object Idle : State("Idle")
        object Ready : State("Ready")
        object Abort: State("Abort")
        object MissingApp: State("MissingApp")
    }

    private val accessibilityServiceEnabledRelay = BehaviorProcessor.createDefault<Boolean>(false)

    private val serviceEnabledRelay = BehaviorProcessor.createDefault<Boolean>(true )

    private val stateRelay = BehaviorProcessor.createDefault<StateRepository.State>(StateRepository.State.Uninitialized)

    val state: Flowable<StateRepository.State> = stateRelay
            .subscribeOn(Schedulers.computation())
            .observeOn(Schedulers.computation())
            .distinctUntilChanged()
            .doOnNext { Log.d("####", "State doOnNext ${it.name}") }
            .replay(1)
            .autoConnect()

    val accessibilityServiceEnabled: Flowable<Boolean> = accessibilityServiceEnabledRelay

    fun updateState(state: StateRepository.State) {
        Log.d("####", "Offering state ${state.name}")
        stateRelay.offer(state)
    }

    fun setAccessibilityServiceEnabled(enabled: Boolean) {
        accessibilityServiceEnabledRelay.offer(enabled)
    }

    fun setServiceEnabled(enabled: Boolean) {
        Log.d("####", "Accessibility Service is now enabled=$enabled")
        serviceEnabledRelay.offer(enabled)
    }

    fun serviceEnabled(): Flowable<Boolean> = serviceEnabledRelay
            .distinctUntilChanged()

    fun abort() {
        stateRelay.offer(StateRepository.State.Abort)
    }

    fun unlockScreen(context: Context) {
        val screenLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG")
        if (!screenLock.isHeld) {
            screenLock.acquire(5 * 60 * 1000 /* 5 minutes */)
        }
    }

    fun lockScreen(context: Context) {
        val screenLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG")
        if (screenLock.isHeld) {
            screenLock.release()
        }

        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        manager.lockNow()
    }
}