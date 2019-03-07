package com.subgarden.heatmycar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import com.subgarden.heatmycar.repository.StateRepository
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1337
        const val ACTION_DELAY = "action_delay"
    }

    private lateinit var stateObserver: Disposable

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == PolicyManager.PM_ACTIVATION_REQUEST_CODE) {
            Toast.makeText(this, "Admin rights added.", Toast.LENGTH_LONG).show()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("####", "Starting MainActivity")

        val policyManager = PolicyManager(this)
        if (!policyManager.isAdminActive) {
            val activateDeviceAdmin = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            activateDeviceAdmin.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, policyManager.component)
            activateDeviceAdmin.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Admin is required to use HeatMyCar (for turning off the screen)")
            startActivityForResult(activateDeviceAdmin, PolicyManager.PM_ACTIVATION_REQUEST_CODE)
        }

        StateRepository.serviceEnabled()
                .subscribe {
                    if (toggleButton.isChecked != it) {
                        toggleButton.isChecked = it
                    }
        }

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            StateRepository.setServiceEnabled(isChecked)
        }

        ActivityCompat.requestPermissions(this,
                                          arrayOf ( Manifest.permission.RECEIVE_SMS,
                                                    Manifest.permission.READ_SMS,
                                                    Manifest.permission.SEND_SMS,
                                                    Manifest.permission.READ_PHONE_STATE,
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.ACCESS_WIFI_STATE,
                                                    Manifest.permission.ACCESS_NETWORK_STATE,
                                                    Manifest.permission.CHANGE_WIFI_STATE,
                                                    Manifest.permission.WAKE_LOCK),
                                          PERMISSION_REQUEST_CODE)

        // Starting the foreground service containing app state.
        ContextCompat.startForegroundService(this, Intent(this, ForegroundService::class.java))

        stateObserver = StateRepository.state
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Log.d("####", "MainActivity: State changed to ${it.name}")
                    state.text = it.name
                }

        StateRepository.accessibilityServiceEnabled
                .subscribe {
                    Log.d("####", "Accessibility service enabled: $it")
                    accessibilityServiceLabel.text = "Accessibility service is ${if (it) "enabled" else "disabled"}"
                }

        val actionDelay = getSharedPreferences("default", MODE_PRIVATE).getInt(ACTION_DELAY, 15).toString()
        delay.setText(actionDelay)

        Flowable.create<Int>(
                { emitter ->
                    delay.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {}

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (s.isNullOrBlank()) return
                            s.toString().toIntOrNull()?.let {
                                emitter.onNext(it)
                            }
                        }
                    })
                }, BackpressureStrategy.LATEST)
                .debounce(500, TimeUnit.MILLISECONDS)
                .subscribe {
                    Log.d("####", "New delay value: $it")
                    getSharedPreferences("default", MODE_PRIVATE).edit().apply {
                        putInt(ACTION_DELAY, it)
                        apply()
                    }
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        stateObserver.dispose()
        Log.d("####", "Destroying MainActivity")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) return

        if (permissions.isEmpty() || grantResults.isEmpty()) return

        if (grantResults.any { it == PERMISSION_DENIED }) {
            Toast.makeText(this, "Missing permissions", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
        }

    }

    private fun isConnectedViaWifi(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mWifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        return mWifi.isConnected
    }




    fun launchOutlanderApp() {
        val outlanderAppIntent = packageManager.getLaunchIntentForPackage("com.inventec.iMobile1")
        if (outlanderAppIntent == null) {
            Toast.makeText(this, "Unable to open Outlander Remote App", Toast.LENGTH_LONG).show()
            StateRepository.updateState(StateRepository.State.MissingApp)
        }

        try {
            ContextCompat.startActivity(this, outlanderAppIntent, null)
        } catch (e: RuntimeException) {
            Log.d("####", "Unable to open Outlander PHEV Remote app ${e.message}")
            StateRepository.lockScreen(this)
        }
    }
}
