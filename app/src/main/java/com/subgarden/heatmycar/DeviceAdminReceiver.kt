package com.subgarden.heatmycar

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("####", "DeviceAdminReceiver onEnabled")
    }
}