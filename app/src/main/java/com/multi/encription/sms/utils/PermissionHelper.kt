package com.multi.encription.sms.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    const val SMS_PERMISSION_REQUEST_CODE = 1001
    const val CONTACTS_PERMISSION_REQUEST_CODE = 1002
    
    val SMS_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )
    
    val CONTACTS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS
    )
    
    fun hasSmsPermissions(context: Context): Boolean {
        return SMS_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasContactsPermissions(context: Context): Boolean {
        return CONTACTS_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun requestSmsPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            SMS_PERMISSIONS,
            SMS_PERMISSION_REQUEST_CODE
        )
    }
    
    fun requestContactsPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            CONTACTS_PERMISSIONS,
            CONTACTS_PERMISSION_REQUEST_CODE
        )
    }
    
    fun shouldShowSmsPermissionRationale(activity: Activity): Boolean {
        return SMS_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    
    fun shouldShowContactsPermissionRationale(activity: Activity): Boolean {
        return CONTACTS_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onSmsPermissionGranted: () -> Unit = {},
        onSmsPermissionDenied: () -> Unit = {},
        onContactsPermissionGranted: () -> Unit = {},
        onContactsPermissionDenied: () -> Unit = {}
    ) {
        when (requestCode) {
            SMS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onSmsPermissionGranted()
                } else {
                    onSmsPermissionDenied()
                }
            }
            CONTACTS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onContactsPermissionGranted()
                } else {
                    onContactsPermissionDenied()
                }
            }
        }
    }
}
