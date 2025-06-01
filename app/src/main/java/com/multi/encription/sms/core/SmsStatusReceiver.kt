package com.multi.encription.sms.core

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager as AndroidSmsManager
import android.util.Log
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.database.SmsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsStatusReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val smsId = intent.getLongExtra(com.multi.encription.sms.core.SmsManager.EXTRA_SMS_ID, -1L)
        if (smsId == -1L) {
            Log.w(TAG, "Received SMS status without valid SMS ID")
            return
        }
        
        val database = SmsDatabase.getDatabase(context)
        val smsDao = database.smsDao()
        val smsManager = com.multi.encription.sms.core.SmsManager(context)

        CoroutineScope(Dispatchers.IO).launch {
            when (intent.action) {
                com.multi.encription.sms.core.SmsManager.SMS_SENT_ACTION -> {
                    handleSmsSent(resultCode, smsId, smsManager)
                }
                com.multi.encription.sms.core.SmsManager.SMS_DELIVERED_ACTION -> {
                    handleSmsDelivered(smsId, smsManager)
                }
            }
        }
    }
    
    private suspend fun handleSmsSent(resultCode: Int, smsId: Long, smsManager: com.multi.encription.sms.core.SmsManager) {
        val status: SmsStatus
        val errorMessage: String?

        when (resultCode) {
            Activity.RESULT_OK -> {
                status = SmsStatus.SENT
                errorMessage = null
                Log.d(TAG, "SMS sent successfully: ID=$smsId")
            }
            AndroidSmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                status = SmsStatus.FAILED
                errorMessage = "Generic failure"
                Log.e(TAG, "SMS failed - Generic failure: ID=$smsId")
            }
            AndroidSmsManager.RESULT_ERROR_NO_SERVICE -> {
                status = SmsStatus.FAILED
                errorMessage = "No service"
                Log.e(TAG, "SMS failed - No service: ID=$smsId")
            }
            AndroidSmsManager.RESULT_ERROR_NULL_PDU -> {
                status = SmsStatus.FAILED
                errorMessage = "Null PDU"
                Log.e(TAG, "SMS failed - Null PDU: ID=$smsId")
            }
            AndroidSmsManager.RESULT_ERROR_RADIO_OFF -> {
                status = SmsStatus.FAILED
                errorMessage = "Radio off"
                Log.e(TAG, "SMS failed - Radio off: ID=$smsId")
            }
            else -> {
                status = SmsStatus.FAILED
                errorMessage = "Unknown error (code: $resultCode)"
                Log.e(TAG, "SMS failed - Unknown error: ID=$smsId, Code=$resultCode")
            }
        }

        smsManager.updateSmsStatus(smsId, status, errorMessage)
    }

    private suspend fun handleSmsDelivered(smsId: Long, smsManager: com.multi.encription.sms.core.SmsManager) {
        Log.d(TAG, "SMS delivered: ID=$smsId")
        smsManager.updateSmsStatus(smsId, SmsStatus.DELIVERED)
    }
}
