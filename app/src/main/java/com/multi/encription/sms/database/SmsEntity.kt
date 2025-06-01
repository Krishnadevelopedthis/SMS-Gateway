package com.multi.encription.sms.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sms_history")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: SmsStatus = SmsStatus.PENDING,
    val apiKey: String? = null, // For API requests
    val requestId: String? = null, // For tracking API requests
    val errorMessage: String? = null,
    val deliveryTimestamp: Long? = null
)

enum class SmsStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    UNKNOWN
}

// Extension functions for easier date handling
fun SmsEntity.getTimestampAsDate(): Date = Date(timestamp)
fun SmsEntity.getDeliveryTimestampAsDate(): Date? = deliveryTimestamp?.let { Date(it) }
