package com.multi.encription.sms.models

import com.google.gson.annotations.SerializedName

data class SmsRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("request_id")
    val requestId: String? = null
)

data class BulkSmsRequest(
    @SerializedName("messages")
    val messages: List<SmsRequest>
)

data class SmsStatusRequest(
    @SerializedName("request_id")
    val requestId: String? = null,
    
    @SerializedName("sms_id")
    val smsId: Long? = null
)
