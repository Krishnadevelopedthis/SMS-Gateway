package com.multi.encription.sms.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager as AndroidSmsManager
import android.util.Log
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.database.SmsEntity
import com.multi.encription.sms.database.SmsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class SmsManager(private val context: Context) {
    
    private val smsManager = AndroidSmsManager.getDefault()
    private val database = SmsDatabase.getDatabase(context)
    private val smsDao = database.smsDao()
    
    companion object {
        private const val TAG = "SmsManager"
        const val SMS_SENT_ACTION = "SMS_SENT"
        const val SMS_DELIVERED_ACTION = "SMS_DELIVERED"
        const val EXTRA_SMS_ID = "sms_id"
        const val EXTRA_REQUEST_ID = "request_id"
    }
    
    suspend fun sendSms(
        phoneNumber: String,
        message: String,
        apiKey: String? = null,
        requestId: String? = null
    ): Result<Long> {
        return try {
            // Validate phone number
            if (!isValidPhoneNumber(phoneNumber)) {
                return Result.failure(IllegalArgumentException("Invalid phone number format"))
            }
            
            // Validate message
            if (message.isBlank()) {
                return Result.failure(IllegalArgumentException("Message cannot be empty"))
            }
            
            // Create SMS entity
            val smsEntity = SmsEntity(
                phoneNumber = phoneNumber,
                message = message,
                status = SmsStatus.PENDING,
                apiKey = apiKey,
                requestId = requestId ?: UUID.randomUUID().toString()
            )
            
            // Insert into database
            val smsId = smsDao.insertSms(smsEntity)
            
            // Send SMS
            sendSmsInternal(smsId, phoneNumber, message)
            
            Log.d(TAG, "SMS queued for sending: ID=$smsId, Phone=$phoneNumber")
            Result.success(smsId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            Result.failure(e)
        }
    }
    
    private fun sendSmsInternal(smsId: Long, phoneNumber: String, message: String) {
        try {
            // Create pending intents for sent and delivered status
            val sentIntent = createSentIntent(smsId)
            val deliveredIntent = createDeliveredIntent(smsId)
            
            // Split message if it's too long
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                // Single part message
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
                )
            } else {
                // Multi-part message
                val sentIntents = arrayListOf<PendingIntent>()
                val deliveredIntents = arrayListOf<PendingIntent>()
                
                repeat(parts.size) {
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }
                
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS internally", e)
            // Update status to failed
            CoroutineScope(Dispatchers.IO).launch {
                updateSmsStatus(smsId, SmsStatus.FAILED, e.message)
            }
        }
    }
    
    private fun createSentIntent(smsId: Long): PendingIntent {
        val intent = Intent(SMS_SENT_ACTION).apply {
            putExtra(EXTRA_SMS_ID, smsId)
        }
        return PendingIntent.getBroadcast(
            context,
            smsId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createDeliveredIntent(smsId: Long): PendingIntent {
        val intent = Intent(SMS_DELIVERED_ACTION).apply {
            putExtra(EXTRA_SMS_ID, smsId)
        }
        return PendingIntent.getBroadcast(
            context,
            smsId.toInt() + 10000, // Offset to avoid conflicts
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    suspend fun updateSmsStatus(smsId: Long, status: SmsStatus, errorMessage: String? = null) {
        try {
            val sms = smsDao.getSmsById(smsId)
            if (sms != null) {
                val updatedSms = sms.copy(
                    status = status,
                    errorMessage = errorMessage,
                    deliveryTimestamp = if (status == SmsStatus.DELIVERED) System.currentTimeMillis() else sms.deliveryTimestamp
                )
                smsDao.updateSms(updatedSms)
                Log.d(TAG, "Updated SMS status: ID=$smsId, Status=$status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update SMS status", e)
        }
    }
    
    suspend fun getSmsById(smsId: Long): SmsEntity? {
        return smsDao.getSmsById(smsId)
    }
    
    suspend fun getSmsByRequestId(requestId: String): SmsEntity? {
        return smsDao.getSmsByRequestId(requestId)
    }
    
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Basic phone number validation
        val cleanNumber = phoneNumber.replace(Regex("[^+\\d]"), "")
        return cleanNumber.length >= 10 && cleanNumber.matches(Regex("^\\+?[1-9]\\d{1,14}$"))
    }
}
