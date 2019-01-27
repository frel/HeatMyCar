package com.subgarden.heatmycar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast


class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1337
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("####", "Starting MainActivity")

        ActivityCompat.requestPermissions(this,
                                          arrayOf ( Manifest.permission.RECEIVE_SMS,
                                                    Manifest.permission.READ_SMS,
                                                    Manifest.permission.SEND_SMS,
                                                    Manifest.permission.READ_CONTACTS),
                                          PERMISSION_REQUEST_CODE)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("####", "onNewIntent")
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
}
