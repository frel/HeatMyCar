package com.subgarden.heatmycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.util.Log
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit


/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class WifiUtil {

    companion object {

        fun disconnectFromWifi(context: Context) {
            val disabled = disableWifi(context)
            Log.d("####", "WiFi: Disconnecting... Success=$disabled")
        }

        fun connectToWifi(context: Context): Flowable<Boolean> {
            if (isConnectedViaWifi(context)) { // TODO revert
                Log.d("####", "Already connected to Wifi. Continuing...")
                return Flowable.fromCallable { true }
            }
            Log.d("####", "Not connected to Wifi. Connecting...")
            return Flowable.create<Boolean>(
                    { emitter ->
                        val filter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return
                                if (isInitialStickyBroadcast) return

                                val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                                if (info != null && info.isConnected) {

                                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                                    val wifiInfo = wifiManager.connectionInfo
                                    val ssid = wifiInfo.ssid
                                    Log.d("####","Wifi connected. SSID=$ssid")

                                    emitter.onNext(true)
                                    emitter.onComplete()
                                    context.unregisterReceiver(this)
                                } else {
                                    Log.d("####","Wifi not connected.")
                                    emitter.onNext(false)
                                }

                            }
                        }
                        Log.d("####", "WiFi: Registering receiver...")
                        context.registerReceiver(receiver, filter)
                        Completable.timer(3, TimeUnit.SECONDS).subscribe {
                            val enabled = enableWifi(context)
                            Log.d("####", "WiFi: Enabling... Success=$enabled")
                        }
                    }, BackpressureStrategy.LATEST)
                    .doOnNext {
                        Log.d("####", "WiFi: doOnNext. Connected: $it")
                    }
                    .doOnComplete {
                        Log.d("####", "WiFi: doOnComplete.")
                    }
                    .doOnError {
                        Log.d("####", "WiFi Error: ${it.message}")
                    }
                    .doOnTerminate {
                        Log.d("####", "WiFi: Flowable terminating.")
                    }
                    .filter { true }
                    .doOnNext {
                        Log.d("####", "WiFi: After filter: doOnNext. Connected: $it")
                    }
                    .subscribeOn(Schedulers.io())
        }

        private fun isConnectedViaWifi(context:Context): Boolean {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            return wifi.isConnected
        }

        private fun enableWifi(context: Context): Boolean {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return wifiManager.setWifiEnabled(true)
        }

        private fun disableWifi(context: Context): Boolean {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return wifiManager.setWifiEnabled(false)
        }


    }

}