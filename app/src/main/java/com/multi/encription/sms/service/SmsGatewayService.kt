package com.multi.encription.sms.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.multi.encription.sms.MainActivity
import com.multi.encription.sms.R
import com.multi.encription.sms.api.SmsApiServer
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.utils.ConfigManager
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.TimeUnit

class SmsGatewayService : Service() {

    private var apiServer: SmsApiServer? = null

    private lateinit var configManager: ConfigManager
    private lateinit var database: SmsDatabase
    private lateinit var smsManager: SmsManager

    private val serviceScope =
        CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var webSocketClient: OkHttpClient? = null

    private var reconnectJob: Job? = null
    private var cloudConnectionRunning = false

    companion object {
        private const val TAG = "SmsGatewayService"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_gateway_channel"

        private const val RECONNECT_DELAY_MS = 5000L

        fun startService(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        configManager = ConfigManager(this)
        database = SmsDatabase.getDatabase(this)
        smsManager = SmsManager(this)

        createNotificationChannel()

        Log.d(TAG, "SMS Gateway Service created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(TAG, "SMS Gateway Service starting")

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        if (configManager.isServerEnabled) {
            startApiServer()
        } else {
            stopApiServer()
        }

        if (configManager.isCloudConnectionEnabled) {
            startCloudConnection()
        } else {
            stopCloudConnection()
        }

        startCleanupTask()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "SMS Gateway Service destroying")

        stopCloudConnection()
        stopApiServer()

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============================================================
    // LOCAL NANOHTTPD SERVER
    // ============================================================

    private fun startApiServer() {
        try {

            if (apiServer?.isAlive == true) {
                Log.d(TAG, "API Server already running")
                return
            }

            apiServer =
                SmsApiServer(
                    this,
                    configManager.serverPort
                )

            val started =
                apiServer?.startServer() ?: false

            if (started) {

                Log.i(
                    TAG,
                    "API Server started successfully on port ${configManager.serverPort}"
                )

                updateNotification(
                    "Local API + Cloud gateway running"
                )

                logServerUrls()

            } else {

                Log.e(TAG, "Failed to start API Server")

                updateNotification(
                    "Failed to start local API Server"
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error starting API Server",
                e
            )

            updateNotification(
                "Error starting API Server: ${e.message}"
            )
        }
    }

    private fun stopApiServer() {
        try {

            apiServer?.stopServer()
            apiServer = null

            Log.i(TAG, "API Server stopped")

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error stopping API Server",
                e
            )
        }
    }

    // ============================================================
    // CLOUD WEBSOCKET
    // ============================================================

    private fun startCloudConnection() {

        if (cloudConnectionRunning) {
            Log.d(TAG, "Cloud connection already running")
            return
        }

        if (configManager.cloudGatewayUrl.isBlank()) {
            Log.e(TAG, "Cloud gateway URL is empty")
            return
        }

        if (configManager.cloudGatewayApiKey.isBlank()) {
            Log.e(TAG, "Cloud gateway API key is empty")
            updateNotification(
                "Cloud gateway API key not configured"
            )
            return
        }

        cloudConnectionRunning = true

        connectToCloudGateway()
    }

    private fun connectToCloudGateway() {

        if (!cloudConnectionRunning) {
            return
        }

        val url = configManager.cloudGatewayUrl

        Log.i(
            TAG,
            "Connecting to cloud gateway: $url"
        )

        try {

            webSocketClient?.dispatcher?.executorService?.shutdown()

            webSocketClient =
                OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

            val request =
                Request.Builder()
                    .url(url)
                    .addHeader(
                        "X-API-Key",
                        configManager.cloudGatewayApiKey
                    )
                    .build()

            webSocket =
                webSocketClient!!.newWebSocket(
                    request,
                    cloudWebSocketListener
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create cloud WebSocket",
                e
            )

            scheduleReconnect()
        }
    }

    private val cloudWebSocketListener =
        object : WebSocketListener() {

            override fun onOpen(
                webSocket: WebSocket,
                response: Response
            ) {

                Log.i(
                    TAG,
                    "Connected to cloud SMS gateway"
                )

                updateNotification(
                    "Cloud SMS gateway connected"
                )

                // Tell Render that this is an SMS phone.
                val registration =
                    JSONObject().apply {
                        put(
                            "type",
                            "phone_connected"
                        )
                        put(
                            "device",
                            "android_sms_gateway"
                        )
                    }

                webSocket.send(
                    registration.toString()
                )
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String
            ) {

                Log.i(
                    TAG,
                    "Cloud message received: $text"
                )

                handleCloudMessage(
                    webSocket,
                    text
                )
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {

                Log.w(
                    TAG,
                    "Cloud WebSocket closing: $code $reason"
                )

                webSocket.close(
                    1000,
                    null
                )
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {

                Log.w(
                    TAG,
                    "Cloud WebSocket closed: $code $reason"
                )

                if (cloudConnectionRunning) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {

                Log.e(
                    TAG,
                    "Cloud WebSocket failure",
                    t
                )

                updateNotification(
                    "Cloud gateway disconnected - reconnecting"
                )

                if (cloudConnectionRunning) {
                    scheduleReconnect()
                }
            }
        }

    private fun handleCloudMessage(
        webSocket: WebSocket,
        text: String
    ) {

        serviceScope.launch(Dispatchers.IO) {

            try {

                val json =
                    JSONObject(text)

                val type =
                    json.optString("type")

                if (type != "send_sms") {

                    Log.d(
                        TAG,
                        "Ignoring unknown cloud message type: $type"
                    )

                    return@launch
                }

                val requestId =
                    json.optString("request_id")

                val phoneNumber =
                    json.optString("phone_number")

                val message =
                    json.optString("message")

                if (
                    requestId.isBlank() ||
                    phoneNumber.isBlank() ||
                    message.isBlank()
                ) {

                    sendCloudAck(
                        webSocket,
                        requestId,
                        "FAILED",
                        "Invalid SMS request"
                    )

                    return@launch
                }

                Log.i(
                    TAG,
                    "Sending cloud SMS request $requestId to $phoneNumber"
                )

                val result =
                    smsManager.sendSms(
                        phoneNumber = phoneNumber,
                        message = message,
                        apiKey = configManager.cloudGatewayApiKey,
                        requestId = requestId
                    )

                if (result.isSuccess) {

                    val smsId =
                        result.getOrNull()

                    Log.i(
                        TAG,
                        "SMS queued successfully: smsId=$smsId"
                    )

                    sendCloudAck(
                        webSocket,
                        requestId,
                        "PENDING",
                        "SMS queued for sending",
                        smsId
                    )

                } else {

                    val error =
                        result.exceptionOrNull()

                    Log.e(
                        TAG,
                        "SMS sending failed",
                        error
                    )

                    sendCloudAck(
                        webSocket,
                        requestId,
                        "FAILED",
                        error?.message
                            ?: "Failed to queue SMS"
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error processing cloud SMS request",
                    e
                )

                try {

                    val json =
                        JSONObject(text)

                    sendCloudAck(
                        webSocket,
                        json.optString("request_id"),
                        "FAILED",
                        e.message ?: "Processing error"
                    )

                } catch (_: Exception) {
                }
            }
        }
    }

    private fun sendCloudAck(
        webSocket: WebSocket,
        requestId: String,
        status: String,
        message: String,
        smsId: Long? = null
    ) {

        try {

            val response =
                JSONObject().apply {

                    put(
                        "type",
                        "sms_result"
                    )

                    put(
                        "request_id",
                        requestId
                    )

                    put(
                        "status",
                        status
                    )

                    put(
                        "message",
                        message
                    )

                    if (smsId != null) {
                        put(
                            "sms_id",
                            smsId
                        )
                    }
                }

            webSocket.send(
                response.toString()
            )

            Log.i(
                TAG,
                "Cloud ACK sent: $response"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to send cloud ACK",
                e
            )
        }
    }

    private fun scheduleReconnect() {

        reconnectJob?.cancel()

        reconnectJob =
            serviceScope.launch {

                delay(RECONNECT_DELAY_MS)

                if (cloudConnectionRunning) {

                    Log.i(
                        TAG,
                        "Attempting cloud gateway reconnect"
                    )

                    connectToCloudGateway()
                }
            }
    }

    private fun stopCloudConnection() {

        cloudConnectionRunning = false

        reconnectJob?.cancel()
        reconnectJob = null

        try {
            webSocket?.close(
                1000,
                "Service stopped"
            )
        } catch (_: Exception) {
        }

        webSocket = null

        try {
            webSocketClient
                ?.dispatcher
                ?.executorService
                ?.shutdown()
        } catch (_: Exception) {
        }

        webSocketClient = null

        Log.i(
            TAG,
            "Cloud connection stopped"
        )
    }

    fun restartCloudConnection() {

        serviceScope.launch {

            stopCloudConnection()

            delay(1000)

            if (configManager.isCloudConnectionEnabled) {
                startCloudConnection()
            }
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    private fun startCleanupTask() {

        if (!configManager.isAutoDeleteOldSmsEnabled) {
            return
        }

        serviceScope.launch {

            while (isActive) {

                try {

                    val cutoffTime =
                        System.currentTimeMillis() -
                                (
                                    configManager.autoDeleteDays *
                                            24 *
                                            60 *
                                            60 *
                                            1000L
                                )

                    val deletedCount =
                        database.smsDao().run {

                            val oldSmsCount =
                                getSmsCountSince(cutoffTime)

                            deleteOldSms(cutoffTime)

                            oldSmsCount
                        }

                    if (deletedCount > 0) {

                        Log.d(
                            TAG,
                            "Cleaned up $deletedCount old SMS records"
                        )
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Error during cleanup task",
                        e
                    )
                }

                delay(
                    24 * 60 * 60 * 1000L
                )
            }
        }
    }

    // ============================================================
    // NETWORK INFO
    // ============================================================

    private fun logServerUrls() {

        try {

            val port =
                configManager.serverPort

            val interfaces =
                NetworkInterface.getNetworkInterfaces()

            Log.i(
                TAG,
                "SMS Gateway API Server URLs:"
            )

            Log.i(
                TAG,
                "- Local: http://localhost:$port/api/info"
            )

            Log.i(
                TAG,
                "- Local: http://127.0.0.1:$port/api/info"
            )

            while (interfaces.hasMoreElements()) {

                val networkInterface =
                    interfaces.nextElement()

                if (
                    !networkInterface.isLoopback &&
                    networkInterface.isUp
                ) {

                    val addresses =
                        networkInterface.inetAddresses

                    while (addresses.hasMoreElements()) {

                        val address =
                            addresses.nextElement()

                        if (
                            !address.isLoopbackAddress &&
                            address.hostAddress
                                ?.contains(':') == false
                        ) {

                            Log.i(
                                TAG,
                                "- Network: http://${address.hostAddress}:$port/api/info"
                            )
                        }
                    }
                }
            }

        } catch (e: SocketException) {

            Log.w(
                TAG,
                "Could not enumerate network interfaces",
                e
            )
        }
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "SMS Gateway Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "SMS Gateway API Server notifications"

                    setShowBadge(false)
                }

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(
        message: String = "SMS Gateway Service running"
    ): Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("SMS Gateway")
            .setContentText(message)
            .setSmallIcon(
                R.drawable.ic_launcher_foreground
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun updateNotification(
        message: String
    ) {

        if (configManager.isNotificationEnabled) {

            val notification =
                createNotification(message)

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.notify(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    // ============================================================
    // RESTART / STATUS
    // ============================================================

    fun restartApiServer() {

        serviceScope.launch {

            stopApiServer()

            delay(1000)

            if (configManager.isServerEnabled) {
                startApiServer()
            }
        }
    }

    fun getServerStatus(): Map<String, Any> {

        return mapOf(
            "server_running" to
                    (apiServer?.isAlive == true),

            "server_port" to
                    configManager.serverPort,

            "server_enabled" to
                    configManager.isServerEnabled,

            "cloud_connected" to
                    (webSocket != null &&
                            cloudConnectionRunning),

            "cloud_enabled" to
                    configManager.isCloudConnectionEnabled
        )
    }
}