package com.multi.encription.sms.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.models.*
import com.multi.encription.sms.utils.ConfigManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

class SmsApiServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {
    
    private val gson = Gson()
    private val smsManager = SmsManager(context)
    private val authManager = AuthenticationManager(context)
    private val configManager = ConfigManager(context)
    private val database = SmsDatabase.getDatabase(context)
    private val smsDao = database.smsDao()
    
    companion object {
        private const val TAG = "SmsApiServer"
        private const val CONTENT_TYPE_JSON = "application/json"
    }
    
    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            val method = session.method
            val headers = session.headers.mapKeys { it.key.lowercase() }
            
            Log.d(TAG, "API Request: $method $uri")
            
            // Handle CORS preflight requests
            if (method == Method.OPTIONS) {
                return createCorsResponse(newFixedLengthResponse(""))
            }
            
            // Authenticate request (except for info endpoint)
            if (uri != "/api/info") {
                val authResult = authManager.authenticate(headers)
                if (!authResult.isAuthenticated) {
                    return createErrorResponse(
                        if (authResult.rateLimited) 429 else 401,
                        "AUTHENTICATION_FAILED",
                        authResult.errorMessage ?: "Authentication failed"
                    )
                }
            }
            
            // Route requests
            when {
                uri == "/api/info" && method == Method.GET -> handleInfo()
                uri == "/api/send" && method == Method.POST -> handleSendSms(session)
                uri == "/api/send-bulk" && method == Method.POST -> handleSendBulkSms(session)
                uri == "/api/status" && method == Method.GET -> handleGetStatus(session)
                uri == "/api/history" && method == Method.GET -> handleGetHistory(session)
                uri == "/api/config" && method == Method.GET -> handleGetConfig()
                uri.startsWith("/api/") -> createErrorResponse(404, "NOT_FOUND", "Endpoint not found")
                else -> createErrorResponse(404, "NOT_FOUND", "Endpoint not found")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Server error", e)
            createErrorResponse(500, "INTERNAL_ERROR", "Internal server error: ${e.message}")
        }
    }
    
    private fun handleInfo(): Response {
        val response = ApiInfoResponse()
        return createJsonResponse(response)
    }
    
    private fun handleSendSms(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val request = gson.fromJson(body, SmsRequest::class.java)
            
            // Validate request
            if (request.phoneNumber.isBlank()) {
                return createErrorResponse(400, "INVALID_REQUEST", "Phone number is required")
            }
            if (request.message.isBlank()) {
                return createErrorResponse(400, "INVALID_REQUEST", "Message is required")
            }
            
            // Send SMS
            runBlocking {
                val result = smsManager.sendSms(
                    phoneNumber = request.phoneNumber,
                    message = request.message,
                    apiKey = extractApiKey(session.headers),
                    requestId = request.requestId
                )
                
                if (result.isSuccess) {
                    val smsId = result.getOrThrow()
                    val response = SmsResponse(
                        success = true,
                        message = "SMS queued for sending",
                        smsId = smsId,
                        requestId = request.requestId,
                        status = "PENDING"
                    )
                    createJsonResponse(response)
                } else {
                    val error = result.exceptionOrNull()
                    createErrorResponse(
                        400,
                        "SMS_SEND_FAILED",
                        error?.message ?: "Failed to send SMS"
                    )
                }
            }
            
        } catch (e: JsonSyntaxException) {
            createErrorResponse(400, "INVALID_JSON", "Invalid JSON format")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            createErrorResponse(500, "INTERNAL_ERROR", "Failed to process request: ${e.message}")
        }
    }
    
    private fun handleSendBulkSms(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val request = gson.fromJson(body, BulkSmsRequest::class.java)
            
            if (request.messages.isEmpty()) {
                return createErrorResponse(400, "INVALID_REQUEST", "No messages provided")
            }
            
            if (request.messages.size > 100) {
                return createErrorResponse(400, "INVALID_REQUEST", "Maximum 100 messages per bulk request")
            }
            
            val results = mutableListOf<SmsResponse>()
            var successCount = 0
            var failedCount = 0
            
            runBlocking {
                request.messages.forEach { smsRequest ->
                    try {
                        if (smsRequest.phoneNumber.isBlank() || smsRequest.message.isBlank()) {
                            results.add(
                                SmsResponse(
                                    success = false,
                                    message = "Invalid phone number or message",
                                    requestId = smsRequest.requestId,
                                    errorCode = "INVALID_REQUEST"
                                )
                            )
                            failedCount++
                        } else {
                            val result = smsManager.sendSms(
                                phoneNumber = smsRequest.phoneNumber,
                                message = smsRequest.message,
                                apiKey = extractApiKey(session.headers),
                                requestId = smsRequest.requestId
                            )
                            
                            if (result.isSuccess) {
                                val smsId = result.getOrThrow()
                                results.add(
                                    SmsResponse(
                                        success = true,
                                        message = "SMS queued for sending",
                                        smsId = smsId,
                                        requestId = smsRequest.requestId,
                                        status = "PENDING"
                                    )
                                )
                                successCount++
                            } else {
                                val error = result.exceptionOrNull()
                                results.add(
                                    SmsResponse(
                                        success = false,
                                        message = error?.message ?: "Failed to send SMS",
                                        requestId = smsRequest.requestId,
                                        errorCode = "SMS_SEND_FAILED"
                                    )
                                )
                                failedCount++
                            }
                        }
                    } catch (e: Exception) {
                        results.add(
                            SmsResponse(
                                success = false,
                                message = "Error processing message: ${e.message}",
                                requestId = smsRequest.requestId,
                                errorCode = "PROCESSING_ERROR"
                            )
                        )
                        failedCount++
                    }
                }
            }
            
            val bulkResponse = BulkSmsResponse(
                success = successCount > 0,
                message = "Processed ${request.messages.size} messages: $successCount successful, $failedCount failed",
                results = results,
                totalCount = request.messages.size,
                successCount = successCount,
                failedCount = failedCount
            )
            
            createJsonResponse(bulkResponse)
            
        } catch (e: JsonSyntaxException) {
            createErrorResponse(400, "INVALID_JSON", "Invalid JSON format")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending bulk SMS", e)
            createErrorResponse(500, "INTERNAL_ERROR", "Failed to process bulk request: ${e.message}")
        }
    }
    
    private fun handleGetStatus(session: IHTTPSession): Response {
        return try {
            val params = session.parms
            val requestId = params["request_id"]
            val smsIdStr = params["sms_id"]
            
            if (requestId.isNullOrBlank() && smsIdStr.isNullOrBlank()) {
                return createErrorResponse(400, "INVALID_REQUEST", "Either request_id or sms_id is required")
            }
            
            runBlocking {
                val smsEntity = if (!requestId.isNullOrBlank()) {
                    smsManager.getSmsByRequestId(requestId)
                } else {
                    val smsId = smsIdStr?.toLongOrNull()
                    if (smsId == null) {
                        return@runBlocking createErrorResponse(400, "INVALID_REQUEST", "Invalid sms_id format")
                    }
                    smsManager.getSmsById(smsId)
                }
                
                if (smsEntity == null) {
                    createErrorResponse(404, "NOT_FOUND", "SMS not found")
                } else {
                    val response = SmsStatusResponse(
                        success = true,
                        smsId = smsEntity.id,
                        requestId = smsEntity.requestId,
                        phoneNumber = smsEntity.phoneNumber,
                        message = smsEntity.message,
                        status = smsEntity.status.name,
                        timestamp = smsEntity.timestamp,
                        deliveryTimestamp = smsEntity.deliveryTimestamp,
                        errorMessage = smsEntity.errorMessage
                    )
                    createJsonResponse(response)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SMS status", e)
            createErrorResponse(500, "INTERNAL_ERROR", "Failed to get status: ${e.message}")
        }
    }

    private fun handleGetHistory(session: IHTTPSession): Response {
        return try {
            val params = session.parms
            val limit = params["limit"]?.toIntOrNull() ?: 50
            val offset = params["offset"]?.toIntOrNull() ?: 0
            val status = params["status"]
            val phoneNumber = params["phone_number"]

            if (limit > 1000) {
                return createErrorResponse(400, "INVALID_REQUEST", "Maximum limit is 1000")
            }

            runBlocking {
                val allSms = smsDao.getAllSms()
                // Note: For simplicity, we're getting all and filtering in memory
                // In production, you'd want to implement proper database queries with pagination

                var result: Response? = null
                allSms.collect { smsList ->
                    var filteredList = smsList

                    // Apply filters
                    if (!status.isNullOrBlank()) {
                        filteredList = filteredList.filter { it.status.name.equals(status, true) }
                    }
                    if (!phoneNumber.isNullOrBlank()) {
                        filteredList = filteredList.filter { it.phoneNumber.contains(phoneNumber, true) }
                    }

                    // Apply pagination
                    val paginatedList = filteredList.drop(offset).take(limit)

                    val history = paginatedList.map { sms ->
                        SmsStatusResponse(
                            success = true,
                            smsId = sms.id,
                            requestId = sms.requestId,
                            phoneNumber = sms.phoneNumber,
                            message = sms.message,
                            status = sms.status.name,
                            timestamp = sms.timestamp,
                            deliveryTimestamp = sms.deliveryTimestamp,
                            errorMessage = sms.errorMessage
                        )
                    }

                    result = createJsonResponse(
                        mapOf(
                            "success" to true,
                            "data" to history,
                            "total_count" to filteredList.size,
                            "limit" to limit,
                            "offset" to offset
                        )
                    )
                }

                result ?: createJsonResponse(mapOf("success" to true, "data" to emptyList<Any>()))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting SMS history", e)
            createErrorResponse(500, "INTERNAL_ERROR", "Failed to get history: ${e.message}")
        }
    }

    private fun handleGetConfig(): Response {
        val config = mapOf(
            "server_port" to configManager.serverPort,
            "server_enabled" to configManager.isServerEnabled,
            "rate_limit_enabled" to configManager.isRateLimitEnabled,
            "rate_limit_per_minute" to configManager.rateLimitPerMinute,
            "rate_limit_per_hour" to configManager.rateLimitPerHour,
            "notification_enabled" to configManager.isNotificationEnabled
        )
        return createJsonResponse(mapOf("success" to true, "config" to config))
    }

    private fun getRequestBody(session: IHTTPSession): String {
        val map = mutableMapOf<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private fun extractApiKey(headers: Map<String, String>): String? {
        return headers["x-api-key"] ?: headers["authorization"]?.removePrefix("Bearer ")
    }

    private fun createJsonResponse(data: Any): Response {
        val json = gson.toJson(data)
        return createCorsResponse(newFixedLengthResponse(Response.Status.OK, CONTENT_TYPE_JSON, json))
    }

    private fun createErrorResponse(statusCode: Int, errorCode: String, message: String): Response {
        val status = when (statusCode) {
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            404 -> Response.Status.NOT_FOUND
            429 -> Response.Status.TOO_MANY_REQUESTS
            500 -> Response.Status.INTERNAL_ERROR
            else -> Response.Status.INTERNAL_ERROR
        }

        val errorResponse = ErrorResponse(
            error = message,
            errorCode = errorCode
        )

        val json = gson.toJson(errorResponse)
        return createCorsResponse(newFixedLengthResponse(status, CONTENT_TYPE_JSON, json))
    }

    private fun createCorsResponse(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key")
        response.addHeader("Access-Control-Max-Age", "86400")
        return response
    }

    fun startServer(): Boolean {
        return try {
            start()
            Log.i(TAG, "SMS API Server started on port ${configManager.serverPort}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server", e)
            false
        }
    }

    fun stopServer() {
        stop()
        Log.i(TAG, "SMS API Server stopped")
    }
}
