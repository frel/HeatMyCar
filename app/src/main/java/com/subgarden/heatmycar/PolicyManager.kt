package com.subgarden.heatmycar

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * @author Fredrik Larsen (f@subgarden.com)
 */
class PolicyManager(context: Context) {

    companion object {
        const val PM_ACTIVATION_REQUEST_CODE = 101
    }

    private val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val component = ComponentName(context.packageName, context.packageName + ".DeviceAdminReceiver")
    val isAdminActive get() = manager.isAdminActive(component)
}