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
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.utils.ConfigManager
import kotlinx.coroutines.*
import java.net.NetworkInterface
import java.net.SocketException

class SmsGatewayService : Service() {
    
    private var apiServer: SmsApiServer? = null
    private lateinit var configManager: ConfigManager
    private lateinit var database: SmsDatabase
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "SmsGatewayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_gateway_channel"
        
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
        
        createNotificationChannel()
        Log.d(TAG, "SMS Gateway Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SMS Gateway Service starting")
        
        if (configManager.isServerEnabled) {
            startApiServer()
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start cleanup task
        startCleanupTask()
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopApiServer()
        serviceScope.cancel()
        Log.d(TAG, "SMS Gateway Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startApiServer() {
        try {
            if (apiServer?.isAlive == true) {
                Log.d(TAG, "API Server already running")
                return
            }
            
            apiServer = SmsApiServer(this, configManager.serverPort)
            val started = apiServer?.startServer() ?: false
            
            if (started) {
                Log.i(TAG, "API Server started successfully on port ${configManager.serverPort}")
                updateNotification("API Server running on port ${configManager.serverPort}")
                
                // Log server URLs
                logServerUrls()
            } else {
                Log.e(TAG, "Failed to start API Server")
                updateNotification("Failed to start API Server")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting API Server", e)
            updateNotification("Error starting API Server: ${e.message}")
        }
    }
    
    private fun stopApiServer() {
        try {
            apiServer?.stopServer()
            apiServer = null
            Log.i(TAG, "API Server stopped")
            updateNotification("API Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping API Server", e)
        }
    }
    
    private fun logServerUrls() {
        try {
            val port = configManager.serverPort
            val interfaces = NetworkInterface.getNetworkInterfaces()
            
            Log.i(TAG, "SMS Gateway API Server URLs:")
            Log.i(TAG, "- Local: http://localhost:$port/api/info")
            Log.i(TAG, "- Local: http://127.0.0.1:$port/api/info")
            
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                            Log.i(TAG, "- Network: http://${address.hostAddress}:$port/api/info")
                        }
                    }
                }
            }
            
        } catch (e: SocketException) {
            Log.w(TAG, "Could not enumerate network interfaces", e)
        }
    }
    
    private fun startCleanupTask() {
        if (!configManager.isAutoDeleteOldSmsEnabled) {
            return
        }
        
        serviceScope.launch {
            while (isActive) {
                try {
                    val cutoffTime = System.currentTimeMillis() - (configManager.autoDeleteDays * 24 * 60 * 60 * 1000L)
                    val deletedCount = database.smsDao().run {
                        val oldSmsCount = getSmsCountSince(cutoffTime)
                        deleteOldSms(cutoffTime)
                        oldSmsCount
                    }
                    
                    if (deletedCount > 0) {
                        Log.d(TAG, "Cleaned up $deletedCount old SMS records")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during cleanup task", e)
                }
                
                // Run cleanup every 24 hours
                delay(24 * 60 * 60 * 1000L)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Gateway Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS Gateway API Server notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String = "SMS Gateway Service running"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun updateNotification(message: String) {
        if (configManager.isNotificationEnabled) {
            val notification = createNotification(message)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    fun restartApiServer() {
        serviceScope.launch {
            stopApiServer()
            delay(1000) // Wait a second before restarting
            if (configManager.isServerEnabled) {
                startApiServer()
            }
        }
    }
    
    fun getServerStatus(): Map<String, Any> {
        return mapOf(
            "server_running" to (apiServer?.isAlive == true),
            "server_port" to configManager.serverPort,
            "server_enabled" to configManager.isServerEnabled
        )
    }
}
