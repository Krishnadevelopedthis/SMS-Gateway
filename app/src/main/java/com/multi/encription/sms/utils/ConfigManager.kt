package com.multi.encription.sms.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class ConfigManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "sms_gateway_config"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_SERVER_ENABLED = "server_enabled"
        private const val KEY_RATE_LIMIT_ENABLED = "rate_limit_enabled"
        private const val KEY_RATE_LIMIT_PER_MINUTE = "rate_limit_per_minute"
        private const val KEY_RATE_LIMIT_PER_HOUR = "rate_limit_per_hour"
        private const val KEY_AUTO_DELETE_OLD_SMS = "auto_delete_old_sms"
        private const val KEY_AUTO_DELETE_DAYS = "auto_delete_days"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_EXTERNAL_DOMAIN = "external_domain"
        private const val KEY_USE_EXTERNAL_DOMAIN = "use_external_domain"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_RATE_LIMIT_PER_MINUTE = 10
        const val DEFAULT_RATE_LIMIT_PER_HOUR = 100
        const val DEFAULT_AUTO_DELETE_DAYS = 30
    }
    
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, null) ?: generateAndSaveApiKey()
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()
    
    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()
    
    var isServerEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVER_ENABLED, value).apply()
    
    var isRateLimitEnabled: Boolean
        get() = prefs.getBoolean(KEY_RATE_LIMIT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_RATE_LIMIT_ENABLED, value).apply()
    
    var rateLimitPerMinute: Int
        get() = prefs.getInt(KEY_RATE_LIMIT_PER_MINUTE, DEFAULT_RATE_LIMIT_PER_MINUTE)
        set(value) = prefs.edit().putInt(KEY_RATE_LIMIT_PER_MINUTE, value).apply()
    
    var rateLimitPerHour: Int
        get() = prefs.getInt(KEY_RATE_LIMIT_PER_HOUR, DEFAULT_RATE_LIMIT_PER_HOUR)
        set(value) = prefs.edit().putInt(KEY_RATE_LIMIT_PER_HOUR, value).apply()
    
    var isAutoDeleteOldSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DELETE_OLD_SMS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DELETE_OLD_SMS, value).apply()
    
    var autoDeleteDays: Int
        get() = prefs.getInt(KEY_AUTO_DELETE_DAYS, DEFAULT_AUTO_DELETE_DAYS)
        set(value) = prefs.edit().putInt(KEY_AUTO_DELETE_DAYS, value).apply()
    
    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()

    var externalDomain: String
        get() = prefs.getString(KEY_EXTERNAL_DOMAIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EXTERNAL_DOMAIN, value).apply()

    var useExternalDomain: Boolean
        get() = prefs.getBoolean(KEY_USE_EXTERNAL_DOMAIN, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_EXTERNAL_DOMAIN, value).apply()

    fun getApiBaseUrl(localIp: String): String {
        return if (useExternalDomain && externalDomain.isNotBlank()) {
            if (externalDomain.startsWith("http")) {
                externalDomain
            } else {
                "https://$externalDomain"
            }
        } else {
            "http://$localIp:$serverPort"
        }
    }

    private fun generateAndSaveApiKey(): String {
        val newApiKey = "sms_" + UUID.randomUUID().toString().replace("-", "")
        apiKey = newApiKey
        return newApiKey
    }
    
    fun regenerateApiKey(): String {
        return generateAndSaveApiKey()
    }
    
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
    
    fun exportConfig(): Map<String, Any> {
        return mapOf(
            "api_key" to apiKey,
            "server_port" to serverPort,
            "server_enabled" to isServerEnabled,
            "rate_limit_enabled" to isRateLimitEnabled,
            "rate_limit_per_minute" to rateLimitPerMinute,
            "rate_limit_per_hour" to rateLimitPerHour,
            "auto_delete_old_sms" to isAutoDeleteOldSmsEnabled,
            "auto_delete_days" to autoDeleteDays,
            "notification_enabled" to isNotificationEnabled
        )
    }
}
