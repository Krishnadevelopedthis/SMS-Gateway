package com.multi.encription.sms

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.multi.encription.sms.core.SmsManager
import com.multi.encription.sms.database.SmsDatabase
import com.multi.encription.sms.service.SmsGatewayService
import com.multi.encription.sms.ui.SmsHistoryAdapter
import com.multi.encription.sms.utils.ConfigManager
import com.multi.encription.sms.utils.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var smsManager: SmsManager
    private lateinit var database: SmsDatabase
    private lateinit var smsHistoryAdapter: SmsHistoryAdapter

    // Local API UI
    private lateinit var serverStatusText: TextView
    private lateinit var serverToggle: SwitchMaterial
    private lateinit var apiKeyText: TextView
    private lateinit var portText: TextView
    private lateinit var apiUrlsText: TextView
    private lateinit var testApiButton: Button
    private lateinit var externalDomainButton: Button

    // Cloud Gateway UI
    private lateinit var cloudGatewayStatusText: TextView
    private lateinit var cloudGatewayUrlText: TextView
    private lateinit var cloudGatewayToggle: SwitchMaterial
    private lateinit var cloudGatewaySettingsButton: Button

    // SMS UI
    private lateinit var smsHistoryRecyclerView: RecyclerView
    private lateinit var sendSmsFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        initializeComponents()
        setupUI()
        checkPermissions()
    }

    private fun initializeComponents() {
        configManager = ConfigManager(this)
        smsManager = SmsManager(this)
        database = SmsDatabase.getDatabase(this)

        // Local API UI
        serverStatusText = findViewById(R.id.serverStatusText)
        serverToggle = findViewById(R.id.serverToggle)
        apiKeyText = findViewById(R.id.apiKeyText)
        portText = findViewById(R.id.portText)
        apiUrlsText = findViewById(R.id.apiUrlsText)
        testApiButton = findViewById(R.id.testApiButton)
        externalDomainButton = findViewById(R.id.externalDomainButton)

        // Cloud Gateway UI
        cloudGatewayStatusText = findViewById(R.id.cloudGatewayStatusText)
        cloudGatewayUrlText = findViewById(R.id.cloudGatewayUrlText)
        cloudGatewayToggle = findViewById(R.id.cloudGatewayToggle)
        cloudGatewaySettingsButton = findViewById(R.id.cloudGatewaySettingsButton)

        // SMS UI
        smsHistoryRecyclerView = findViewById(R.id.smsHistoryRecyclerView)
        sendSmsFab = findViewById(R.id.sendSmsFab)
    }

    private fun setupUI() {

        // ---------------------------------------------------------
        // SMS HISTORY
        // ---------------------------------------------------------

        smsHistoryAdapter = SmsHistoryAdapter { sms ->
            showSmsDetails(sms)
        }

        smsHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = smsHistoryAdapter
        }

        database.smsDao().getAllSmsLiveData().observe(this) { smsList ->
            smsHistoryAdapter.submitList(smsList)
        }

        // ---------------------------------------------------------
        // LOCAL API SERVER
        // ---------------------------------------------------------

        serverToggle.isChecked = configManager.isServerEnabled

        serverToggle.setOnCheckedChangeListener { _, isChecked ->

            configManager.isServerEnabled = isChecked

            syncGatewayService()

            updateServerStatus()
            updateApiUrls()
            updateCloudGatewayStatus()
        }

        // ---------------------------------------------------------
        // CLOUD SMS GATEWAY
        // ---------------------------------------------------------

        cloudGatewayToggle.isChecked = configManager.isCloudConnectionEnabled

        cloudGatewayToggle.setOnCheckedChangeListener { _, isChecked ->

            if (isChecked) {

                val apiKey = configManager.cloudGatewayApiKey.trim()

                if (apiKey.isBlank()) {
                    cloudGatewayToggle.isChecked = false

                    Toast.makeText(
                        this,
                        "Please configure the Cloud Gateway API key first",
                        Toast.LENGTH_LONG
                    ).show()

                    showCloudGatewaySettingsDialog()
                    return@setOnCheckedChangeListener
                }
            }

            configManager.isCloudConnectionEnabled = isChecked

            syncGatewayService()
            updateCloudGatewayStatus()

            if (isChecked) {
                Toast.makeText(
                    this,
                    "Cloud Gateway enabled. Connecting...",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Cloud Gateway disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        cloudGatewaySettingsButton.setOnClickListener {
            showCloudGatewaySettingsDialog()
        }

        // ---------------------------------------------------------
        // FAB
        // ---------------------------------------------------------

        sendSmsFab.setOnClickListener {
            showSendSmsDialog()
        }

        // ---------------------------------------------------------
        // API KEY / PORT / URL
        // ---------------------------------------------------------

        updateApiKeyDisplay()
        updatePortDisplay()

        apiKeyText.setOnClickListener {
            showApiKeyDialog()
        }

        portText.setOnClickListener {
            showPortDialog()
        }

        apiUrlsText.setOnClickListener {
            showApiUrlsDialog()
        }

        // ---------------------------------------------------------
        // TEST API
        // ---------------------------------------------------------

        testApiButton.setOnClickListener {
            showTestApiDialog()
        }

        // ---------------------------------------------------------
        // EXTERNAL DOMAIN
        // ---------------------------------------------------------

        externalDomainButton.setOnClickListener {
            showExternalDomainDialog()
        }

        // ---------------------------------------------------------
        // INITIAL UI STATE
        // ---------------------------------------------------------

        updateServerStatus()
        updateApiUrls()
        updateExternalDomainButton()
        updateCloudGatewayStatus()
    }

    // =============================================================
    // SERVICE LIFECYCLE
    // =============================================================

    private fun syncGatewayService() {

        val localEnabled = configManager.isServerEnabled
        val cloudEnabled = configManager.isCloudConnectionEnabled

        if (localEnabled || cloudEnabled) {
            SmsGatewayService.startService(this)
        } else {
            SmsGatewayService.stopService(this)
        }
    }

    // =============================================================
    // CLOUD GATEWAY
    // =============================================================

    private fun updateCloudGatewayStatus() {

        val enabled = configManager.isCloudConnectionEnabled
        val apiKeyConfigured =
            configManager.cloudGatewayApiKey.isNotBlank()

        cloudGatewayUrlText.text =
            "Gateway: ${configManager.cloudGatewayUrl}"

        cloudGatewayStatusText.text = when {
            !enabled ->
                "Cloud Status: Disabled"

            !apiKeyConfigured ->
                "Cloud Status: API key not configured"

            else ->
                "Cloud Status: Enabled • Connecting..."
        }
    }

    private fun showCloudGatewaySettingsDialog() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }

        val urlEditText = EditText(this).apply {
            hint = "WebSocket Gateway URL"
            setText(configManager.cloudGatewayUrl)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_URI
        }

        val apiKeyEditText = EditText(this).apply {
            hint = "Cloud Gateway API Key"
            setText(configManager.cloudGatewayApiKey)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(urlEditText)
        layout.addView(apiKeyEditText)

        AlertDialog.Builder(this)
            .setTitle("Cloud Gateway Settings")
            .setMessage(
                "Configure the Render Cloud SMS Gateway connection."
            )
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->

                val url = urlEditText.text.toString().trim()
                val apiKey = apiKeyEditText.text.toString().trim()

                if (url.isBlank()) {
                    Toast.makeText(
                        this,
                        "Gateway URL cannot be empty",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                if (!url.startsWith("wss://") &&
                    !url.startsWith("ws://")
                ) {
                    Toast.makeText(
                        this,
                        "Use a WebSocket URL starting with ws:// or wss://",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                if (apiKey.isBlank()) {
                    Toast.makeText(
                        this,
                        "Cloud Gateway API key cannot be empty",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // If currently connected, stop first so the new
                // configuration is picked up cleanly.
                val wasCloudEnabled =
                    configManager.isCloudConnectionEnabled

                if (wasCloudEnabled) {
                    configManager.isCloudConnectionEnabled = false
                    syncGatewayService()
                }

                configManager.cloudGatewayUrl = url
                configManager.cloudGatewayApiKey = apiKey

                updateCloudGatewayStatus()

                Toast.makeText(
                    this,
                    "Cloud Gateway settings saved",
                    Toast.LENGTH_SHORT
                ).show()

                // Automatically reconnect if it was enabled before.
                if (wasCloudEnabled) {
                    configManager.isCloudConnectionEnabled = true
                    syncGatewayService()
                    updateCloudGatewayStatus()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =============================================================
    // PERMISSIONS
    // =============================================================

    private fun checkPermissions() {

        if (!PermissionHelper.hasSmsPermissions(this)) {

            if (PermissionHelper.shouldShowSmsPermissionRationale(this)) {
                showPermissionRationaleDialog()
            } else {
                PermissionHelper.requestSmsPermissions(this)
            }
        }
    }

    private fun showPermissionRationaleDialog() {

        AlertDialog.Builder(this)
            .setTitle("SMS Permissions Required")
            .setMessage(
                "This app needs SMS permissions to send messages through the device. " +
                        "Please grant the permissions to continue."
            )
            .setPositiveButton("Grant Permissions") { _, _ ->
                PermissionHelper.requestSmsPermissions(this)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()

                Toast.makeText(
                    this,
                    "SMS permissions are required for the app to function",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        PermissionHelper.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onSmsPermissionGranted = {
                Toast.makeText(
                    this,
                    "SMS permissions granted",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onSmsPermissionDenied = {
                Toast.makeText(
                    this,
                    "SMS permissions denied. App functionality will be limited.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // =============================================================
    // LOCAL SERVER STATUS
    // =============================================================

    private fun updateServerStatus() {

        val isRunning = configManager.isServerEnabled

        serverStatusText.text =
            if (isRunning) {
                "Server Status: Running on port ${configManager.serverPort}"
            } else {
                "Server Status: Stopped"
            }
    }

    // =============================================================
    // LOCAL API KEY
    // =============================================================

    private fun updateApiKeyDisplay() {

        val apiKey = configManager.apiKey

        apiKeyText.text =
            "API Key: ${apiKey.take(10)}... (tap to view/change)"
    }

    // =============================================================
    // PORT
    // =============================================================

    private fun updatePortDisplay() {

        portText.text =
            "Port: ${configManager.serverPort} (tap to change)"
    }

    // =============================================================
    // API URLS
    // =============================================================

    private fun updateApiUrls() {

        val deviceIp = getDeviceIpAddress()

        val baseUrl =
            configManager.getApiBaseUrl(deviceIp)

        val accessType =
            if (configManager.useExternalDomain) {
                "External Domain"
            } else {
                "Local Network"
            }

        val urlsText = """
            API Base URL ($accessType): $baseUrl

            Main Endpoints:
            • Send SMS: POST $baseUrl/api/send
            • Check Status: GET $baseUrl/api/status
            • SMS History: GET $baseUrl/api/history
            • Server Info: GET $baseUrl/api/info

            (Tap to view full details)
        """.trimIndent()

        apiUrlsText.text = urlsText
    }

    // =============================================================
    // DEVICE IP
    // =============================================================

    private fun getDeviceIpAddress(): String {

        try {

            val interfaces =
                java.net.NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {

                val networkInterface =
                    interfaces.nextElement()

                if (!networkInterface.isLoopback &&
                    networkInterface.isUp
                ) {

                    val addresses =
                        networkInterface.inetAddresses

                    while (addresses.hasMoreElements()) {

                        val address =
                            addresses.nextElement()

                        if (!address.isLoopbackAddress &&
                            address.hostAddress?.contains(':') == false
                        ) {
                            return address.hostAddress
                                ?: "192.168.1.100"
                        }
                    }
                }
            }

        } catch (_: Exception) {
            // Fallback
        }

        return "192.168.1.100"
    }

    // =============================================================
    // SEND SMS
    // =============================================================

    private fun showSendSmsDialog() {

        if (!PermissionHelper.hasSmsPermissions(this)) {

            Toast.makeText(
                this,
                "SMS permissions required",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_send_sms,
                null
            )

        val phoneEditText =
            dialogView.findViewById<EditText>(
                R.id.phoneEditText
            )

        val messageEditText =
            dialogView.findViewById<EditText>(
                R.id.messageEditText
            )

        AlertDialog.Builder(this)
            .setTitle("Send SMS")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->

                val phone =
                    phoneEditText.text.toString().trim()

                val message =
                    messageEditText.text.toString().trim()

                if (phone.isBlank() ||
                    message.isBlank()
                ) {

                    Toast.makeText(
                        this,
                        "Please fill in all fields",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                sendSms(phone, message)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSms(
        phoneNumber: String,
        message: String
    ) {

        lifecycleScope.launch {

            try {

                val result =
                    smsManager.sendSms(
                        phoneNumber,
                        message
                    )

                if (result.isSuccess) {

                    Toast.makeText(
                        this@MainActivity,
                        "SMS queued for sending",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    val error =
                        result.exceptionOrNull()

                    Toast.makeText(
                        this@MainActivity,
                        "Failed to send SMS: ${error?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // =============================================================
    // SMS DETAILS
    // =============================================================

    private fun showSmsDetails(
        sms: com.multi.encription.sms.database.SmsEntity
    ) {

        val message = """
            Phone: ${sms.phoneNumber}
            Message: ${sms.message}
            Status: ${sms.status}
            Timestamp: ${
            java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(
                java.util.Date(sms.timestamp)
            )
        }
            ${
            if (sms.deliveryTimestamp != null)
                "Delivered: ${
                    java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault()
                    ).format(
                        java.util.Date(
                            sms.deliveryTimestamp
                        )
                    )
                }"
            else
                ""
        }
            ${
            if (sms.errorMessage != null)
                "Error: ${sms.errorMessage}"
            else
                ""
        }
            ${
            if (sms.requestId != null)
                "Request ID: ${sms.requestId}"
            else
                ""
        }
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("SMS Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // =============================================================
    // LOCAL API KEY DIALOG
    // =============================================================

    private fun showApiKeyDialog() {

        val currentApiKey =
            configManager.apiKey

        val editText = EditText(this).apply {
            setText(currentApiKey)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("API Key")
            .setMessage(
                "Current API Key (copy this for API access):"
            )
            .setView(editText)
            .setPositiveButton("Generate New") { _, _ ->

                configManager.regenerateApiKey()

                updateApiKeyDisplay()

                Toast.makeText(
                    this,
                    "New API key generated",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // =============================================================
    // PORT DIALOG
    // =============================================================

    private fun showPortDialog() {

        val editText = EditText(this).apply {

            setText(
                configManager.serverPort.toString()
            )

            inputType =
                InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Server Port")
            .setMessage(
                "Enter the port number for the API server:"
            )
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->

                val port =
                    editText.text
                        .toString()
                        .toIntOrNull()

                if (port != null &&
                    port in 1024..65535
                ) {

                    configManager.serverPort = port

                    updatePortDisplay()
                    updateApiUrls()

                    if (configManager.isServerEnabled) {

                        SmsGatewayService.stopService(this)

                        SmsGatewayService.startService(this)
                    }

                    Toast.makeText(
                        this,
                        "Port updated to $port",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    Toast.makeText(
                        this,
                        "Please enter a valid port number (1024-65535)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =============================================================
    // API URL DIALOG
    // =============================================================

    private fun showApiUrlsDialog() {

        val deviceIp =
            getDeviceIpAddress()

        val port =
            configManager.serverPort

        val apiKey =
            configManager.apiKey

        val baseUrl =
            "http://$deviceIp:$port"

        val message = """
            📡 SMS Gateway API Endpoints

            Base URL: $baseUrl
            API Key: $apiKey

            🔗 Available Endpoints:

            1. Send SMS:
            POST $baseUrl/api/send

            Headers:
            X-API-Key: $apiKey

            Body:
            {"phone_number": "+1234567890", "message": "Hello!"}

            2. Check SMS Status:
            GET $baseUrl/api/status?sms_id=123

            3. Get SMS History:
            GET $baseUrl/api/history?limit=10

            4. Server Info:
            GET $baseUrl/api/info

            5. Send Bulk SMS:
            POST $baseUrl/api/send-bulk

            Headers:
            X-API-Key: $apiKey

            📋 Copy these URLs to use in your applications!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("API Endpoints")
            .setMessage(message)
            .setPositiveButton("Copy Base URL") { _, _ ->
                copyToClipboard(
                    "Base URL",
                    baseUrl
                )
            }
            .setNeutralButton("Copy API Key") { _, _ ->
                copyToClipboard(
                    "API Key",
                    apiKey
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // =============================================================
    // TEST API
    // =============================================================

    private fun showTestApiDialog() {

        if (!configManager.isServerEnabled) {

            Toast.makeText(
                this,
                "Please enable the API server first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_test_api,
                null
            )

        val phoneEditText =
            dialogView.findViewById<EditText>(
                R.id.testPhoneEditText
            )

        val messageEditText =
            dialogView.findViewById<EditText>(
                R.id.testMessageEditText
            )

        phoneEditText.setText("+1234567890")

        messageEditText.setText(
            "Test message from SMS Gateway API"
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Test API - Send SMS")
                .setView(dialogView)
                .setPositiveButton(
                    "Send Test SMS"
                ) { _, _ ->

                    val phone =
                        phoneEditText.text
                            .toString()
                            .trim()

                    val message =
                        messageEditText.text
                            .toString()
                            .trim()

                    if (phone.isBlank() ||
                        message.isBlank()
                    ) {

                        Toast.makeText(
                            this,
                            "Please fill in all fields",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setPositiveButton
                    }

                    testApiSendSms(
                        phone,
                        message
                    )
                }
                .setNeutralButton(
                    "Test Server Info"
                ) { _, _ ->
                    testApiServerInfo()
                }
                .setNegativeButton(
                    "Close",
                    null
                )
                .create()

        dialog.show()
    }

    private fun testApiSendSms(
        phoneNumber: String,
        message: String
    ) {

        lifecycleScope.launch {

            try {

                val result =
                    smsManager.sendSms(
                        phoneNumber,
                        message,
                        configManager.apiKey,
                        "test-${System.currentTimeMillis()}"
                    )

                if (result.isSuccess) {

                    val smsId =
                        result.getOrThrow()

                    showTestResult(
                        "✅ SMS Test Successful",
                        """
                        SMS queued for sending!

                        SMS ID: $smsId
                        Phone: $phoneNumber
                        Message: $message

                        Check the SMS History below to see delivery status.
                        """.trimIndent()
                    )

                } else {

                    val error =
                        result.exceptionOrNull()

                    showTestResult(
                        "❌ SMS Test Failed",
                        "Error: ${error?.message}"
                    )
                }

            } catch (e: Exception) {

                showTestResult(
                    "❌ SMS Test Failed",
                    "Exception: ${e.message}"
                )
            }
        }
    }

    private fun testApiServerInfo() {

        val deviceIp =
            getDeviceIpAddress()

        val port =
            configManager.serverPort

        val infoUrl =
            "http://$deviceIp:$port/api/info"

        showTestResult(
            "📡 Server Info Test",
            """
            Test this URL in your browser or API client:

            $infoUrl

            This endpoint doesn't require authentication and should return server information in JSON format.

            If you can access this URL, your API server is working correctly!
            """.trimIndent()
        )
    }

    private fun showTestResult(
        title: String,
        message: String
    ) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // =============================================================
    // CLIPBOARD
    // =============================================================

    private fun copyToClipboard(
        label: String,
        text: String
    ) {

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager

        val clip =
            android.content.ClipData.newPlainText(
                label,
                text
            )

        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            this,
            "$label copied to clipboard",
            Toast.LENGTH_SHORT
        ).show()
    }

    // =============================================================
    // EXTERNAL DOMAIN
    // =============================================================

    private fun updateExternalDomainButton() {

        val buttonText =
            if (configManager.useExternalDomain) {
                "External Domain: ${configManager.externalDomain}"
            } else {
                "Setup External Domain"
            }

        externalDomainButton.text =
            buttonText
    }

    private fun showExternalDomainDialog() {

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_external_domain,
                null
            )

        val domainEditText =
            dialogView.findViewById<EditText>(
                R.id.domainEditText
            )

        val enableSwitch =
            dialogView.findViewById<Switch>(
                R.id.enableExternalDomainSwitch
            )

        val instructionsText =
            dialogView.findViewById<TextView>(
                R.id.instructionsText
            )

        domainEditText.setText(
            configManager.externalDomain
        )

        enableSwitch.isChecked =
            configManager.useExternalDomain

        instructionsText.text = """
            To use a custom domain:

            1. Set up a reverse proxy (VPS + Nginx)
            2. Point your domain to the proxy server
            3. Configure proxy to forward to:
               ${getDeviceIpAddress()}:${configManager.serverPort}
            4. Enter your domain below.

            See CUSTOM_DOMAIN_SETUP.md for detailed instructions.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("External Domain Setup")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->

                val domain =
                    domainEditText.text
                        .toString()
                        .trim()

                val enabled =
                    enableSwitch.isChecked

                if (enabled && domain.isBlank()) {

                    Toast.makeText(
                        this,
                        "Please enter a domain name",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                configManager.externalDomain =
                    domain

                configManager.useExternalDomain =
                    enabled

                updateApiUrls()
                updateExternalDomainButton()

                val message =
                    if (enabled) {
                        "External domain enabled: $domain"
                    } else {
                        "Using local network access"
                    }

                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton(
                "View Setup Guide"
            ) {
                _, _ ->
                showExternalDomainSetupGuide()
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun showExternalDomainSetupGuide() {

        val deviceIp =
            getDeviceIpAddress()

        val port =
            configManager.serverPort

        val guide = """
            🌐 External Domain Setup Guide

            Your Android device:
            $deviceIp:$port

            📋 Quick Setup Options:

            1. VPS + Nginx
            • Get a VPS
            • Install Nginx
            • Configure reverse proxy
            • Get SSL certificate

            2. Cloudflare Tunnel
            • Install cloudflared
            • Create a tunnel
            • Forward to $deviceIp:$port

            3. Dynamic DNS + Port Forwarding
            • Configure router port forwarding
            • Use DuckDNS or No-IP
            • Forward port $port

            📖 See CUSTOM_DOMAIN_SETUP.md for details.

            ⚠️ Security Notes:
            • Use strong API keys
            • Enable rate limiting
            • Monitor access logs
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Setup Guide")
            .setMessage(guide)
            .setPositiveButton("OK", null)
            .show()
    }
}