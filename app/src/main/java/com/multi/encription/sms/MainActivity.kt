package com.multi.encription.sms

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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

    // UI Components
    private lateinit var serverStatusText: TextView
    private lateinit var serverToggle: SwitchMaterial
    private lateinit var apiKeyText: TextView
    private lateinit var portText: TextView
    private lateinit var apiUrlsText: TextView
    private lateinit var testApiButton: Button
    private lateinit var externalDomainButton: Button
    private lateinit var smsHistoryRecyclerView: RecyclerView
    private lateinit var sendSmsFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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

        // Initialize UI components
        serverStatusText = findViewById(R.id.serverStatusText)
        serverToggle = findViewById(R.id.serverToggle)
        apiKeyText = findViewById(R.id.apiKeyText)
        portText = findViewById(R.id.portText)
        apiUrlsText = findViewById(R.id.apiUrlsText)
        testApiButton = findViewById(R.id.testApiButton)
        externalDomainButton = findViewById(R.id.externalDomainButton)
        smsHistoryRecyclerView = findViewById(R.id.smsHistoryRecyclerView)
        sendSmsFab = findViewById(R.id.sendSmsFab)
    }

    private fun setupUI() {
        // Setup RecyclerView
        smsHistoryAdapter = SmsHistoryAdapter { sms ->
            // Handle SMS item click - show details
            showSmsDetails(sms)
        }

        smsHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = smsHistoryAdapter
        }

        // Observe SMS history
        database.smsDao().getAllSmsLiveData().observe(this) { smsList ->
            smsHistoryAdapter.submitList(smsList)
        }

        // Setup server toggle
        serverToggle.isChecked = configManager.isServerEnabled
        serverToggle.setOnCheckedChangeListener { _, isChecked ->
            configManager.isServerEnabled = isChecked
            if (isChecked) {
                SmsGatewayService.startService(this)
            } else {
                SmsGatewayService.stopService(this)
            }
            updateServerStatus()
            updateApiUrls()
        }

        // Setup FAB for sending SMS
        sendSmsFab.setOnClickListener {
            showSendSmsDialog()
        }

        // Setup API key display
        updateApiKeyDisplay()

        // Setup port display
        updatePortDisplay()

        // Setup click listeners for configuration
        apiKeyText.setOnClickListener { showApiKeyDialog() }
        portText.setOnClickListener { showPortDialog() }
        apiUrlsText.setOnClickListener { showApiUrlsDialog() }

        // Setup test API button
        testApiButton.setOnClickListener { showTestApiDialog() }

        // Setup external domain button
        externalDomainButton.setOnClickListener { showExternalDomainDialog() }

        // Update displays
        updateServerStatus()
        updateApiUrls()
        updateExternalDomainButton()
    }

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
            .setMessage("This app needs SMS permissions to send messages through the device. Please grant the permissions to continue.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                PermissionHelper.requestSmsPermissions(this)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "SMS permissions are required for the app to function", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        PermissionHelper.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onSmsPermissionGranted = {
                Toast.makeText(this, "SMS permissions granted", Toast.LENGTH_SHORT).show()
            },
            onSmsPermissionDenied = {
                Toast.makeText(this, "SMS permissions denied. App functionality will be limited.", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun updateServerStatus() {
        val isRunning = configManager.isServerEnabled
        serverStatusText.text = if (isRunning) {
            "Server Status: Running on port ${configManager.serverPort}"
        } else {
            "Server Status: Stopped"
        }
    }

    private fun updateApiKeyDisplay() {
        val apiKey = configManager.apiKey
        apiKeyText.text = "API Key: ${apiKey.take(10)}... (tap to view/change)"
    }

    private fun updatePortDisplay() {
        portText.text = "Port: ${configManager.serverPort} (tap to change)"
    }

    private fun updateApiUrls() {
        val deviceIp = getDeviceIpAddress()
        val baseUrl = configManager.getApiBaseUrl(deviceIp)
        val accessType = if (configManager.useExternalDomain) "External Domain" else "Local Network"

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

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                            return address.hostAddress ?: "192.168.1.100"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to a common local IP pattern
        }
        return "192.168.1.100" // Fallback IP
    }

    private fun showSendSmsDialog() {
        if (!PermissionHelper.hasSmsPermissions(this)) {
            Toast.makeText(this, "SMS permissions required", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_send_sms, null)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.phoneEditText)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)

        AlertDialog.Builder(this)
            .setTitle("Send SMS")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val phone = phoneEditText.text.toString().trim()
                val message = messageEditText.text.toString().trim()

                if (phone.isBlank() || message.isBlank()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendSms(phone, message)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(phoneNumber, message)
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, "SMS queued for sending", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Failed to send SMS: ${error?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSmsDetails(sms: com.multi.encription.sms.database.SmsEntity) {
        val message = """
            Phone: ${sms.phoneNumber}
            Message: ${sms.message}
            Status: ${sms.status}
            Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(sms.timestamp))}
            ${if (sms.deliveryTimestamp != null) "Delivered: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(sms.deliveryTimestamp))}" else ""}
            ${if (sms.errorMessage != null) "Error: ${sms.errorMessage}" else ""}
            ${if (sms.requestId != null) "Request ID: ${sms.requestId}" else ""}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("SMS Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val currentApiKey = configManager.apiKey
        val editText = EditText(this).apply {
            setText(currentApiKey)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("API Key")
            .setMessage("Current API Key (copy this for API access):")
            .setView(editText)
            .setPositiveButton("Generate New") { _, _ ->
                val newApiKey = configManager.regenerateApiKey()
                updateApiKeyDisplay()
                Toast.makeText(this, "New API key generated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPortDialog() {
        val editText = EditText(this).apply {
            setText(configManager.serverPort.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Server Port")
            .setMessage("Enter the port number for the API server:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val portText = editText.text.toString()
                val port = portText.toIntOrNull()

                if (port != null && port in 1024..65535) {
                    configManager.serverPort = port
                    updatePortDisplay()
                    updateApiUrls()

                    if (configManager.isServerEnabled) {
                        // Restart server with new port
                        SmsGatewayService.stopService(this)
                        SmsGatewayService.startService(this)
                    }

                    Toast.makeText(this, "Port updated to $port", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid port number (1024-65535)", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiUrlsDialog() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort
        val apiKey = configManager.apiKey
        val baseUrl = "http://$deviceIp:$port"

        val message = """
            📡 SMS Gateway API Endpoints

            Base URL: $baseUrl
            API Key: $apiKey

            🔗 Available Endpoints:

            1. Send SMS:
            POST $baseUrl/api/send
            Headers: X-API-Key: $apiKey
            Body: {"phone_number": "+1234567890", "message": "Hello!"}

            2. Check SMS Status:
            GET $baseUrl/api/status?sms_id=123
            Headers: X-API-Key: $apiKey

            3. Get SMS History:
            GET $baseUrl/api/history?limit=10
            Headers: X-API-Key: $apiKey

            4. Server Info (No Auth):
            GET $baseUrl/api/info

            5. Send Bulk SMS:
            POST $baseUrl/api/send-bulk
            Headers: X-API-Key: $apiKey
            Body: {"messages": [{"phone_number": "+1234567890", "message": "Hello!"}]}

            📋 Copy these URLs to use in your applications!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("API Endpoints")
            .setMessage(message)
            .setPositiveButton("Copy Base URL") { _, _ ->
                copyToClipboard("Base URL", baseUrl)
            }
            .setNeutralButton("Copy API Key") { _, _ ->
                copyToClipboard("API Key", apiKey)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTestApiDialog() {
        if (!configManager.isServerEnabled) {
            Toast.makeText(this, "Please enable the API server first", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_test_api, null)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.testPhoneEditText)
        val messageEditText = dialogView.findViewById<EditText>(R.id.testMessageEditText)
        val resultTextView = dialogView.findViewById<TextView>(R.id.testResultTextView)

        // Pre-fill with example data
        phoneEditText.setText("+1234567890")
        messageEditText.setText("Test message from SMS Gateway API")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Test API - Send SMS")
            .setView(dialogView)
            .setPositiveButton("Send Test SMS") { _, _ ->
                val phone = phoneEditText.text.toString().trim()
                val message = messageEditText.text.toString().trim()

                if (phone.isBlank() || message.isBlank()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                testApiSendSms(phone, message)
            }
            .setNeutralButton("Test Server Info") { _, _ ->
                testApiServerInfo()
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }

    private fun testApiSendSms(phoneNumber: String, message: String) {
        lifecycleScope.launch {
            try {
                val result = smsManager.sendSms(phoneNumber, message, configManager.apiKey, "test-${System.currentTimeMillis()}")
                if (result.isSuccess) {
                    val smsId = result.getOrThrow()
                    showTestResult("✅ SMS Test Successful", """
                        SMS queued for sending!

                        SMS ID: $smsId
                        Phone: $phoneNumber
                        Message: $message

                        Check the SMS History below to see delivery status.
                    """.trimIndent())
                } else {
                    val error = result.exceptionOrNull()
                    showTestResult("❌ SMS Test Failed", "Error: ${error?.message}")
                }
            } catch (e: Exception) {
                showTestResult("❌ SMS Test Failed", "Exception: ${e.message}")
            }
        }
    }

    private fun testApiServerInfo() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort
        val infoUrl = "http://$deviceIp:$port/api/info"

        showTestResult("📡 Server Info Test", """
            Test this URL in your browser or API client:

            $infoUrl

            This endpoint doesn't require authentication and should return server information in JSON format.

            If you can access this URL, your API server is working correctly!
        """.trimIndent())
    }

    private fun showTestResult(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun updateExternalDomainButton() {
        val buttonText = if (configManager.useExternalDomain) {
            "External Domain: ${configManager.externalDomain}"
        } else {
            "Setup External Domain"
        }
        externalDomainButton.text = buttonText
    }

    private fun showExternalDomainDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_external_domain, null)
        val domainEditText = dialogView.findViewById<EditText>(R.id.domainEditText)
        val enableSwitch = dialogView.findViewById<Switch>(R.id.enableExternalDomainSwitch)
        val instructionsText = dialogView.findViewById<TextView>(R.id.instructionsText)

        // Pre-fill current values
        domainEditText.setText(configManager.externalDomain)
        enableSwitch.isChecked = configManager.useExternalDomain

        instructionsText.text = """
            To use a custom domain:

            1. Set up a reverse proxy (VPS + Nginx)
            2. Point your domain to the proxy server
            3. Configure proxy to forward to: ${getDeviceIpAddress()}:${configManager.serverPort}
            4. Enter your domain below (e.g., sms.yourdomain.com)

            See CUSTOM_DOMAIN_SETUP.md for detailed instructions.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("External Domain Setup")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val domain = domainEditText.text.toString().trim()
                val enabled = enableSwitch.isChecked

                if (enabled && domain.isBlank()) {
                    Toast.makeText(this, "Please enter a domain name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                configManager.externalDomain = domain
                configManager.useExternalDomain = enabled

                updateApiUrls()
                updateExternalDomainButton()

                val message = if (enabled) {
                    "External domain enabled: $domain"
                } else {
                    "Using local network access"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("View Setup Guide") { _, _ ->
                showExternalDomainSetupGuide()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExternalDomainSetupGuide() {
        val deviceIp = getDeviceIpAddress()
        val port = configManager.serverPort

        val guide = """
            🌐 External Domain Setup Guide

            Your Android device: $deviceIp:$port

            📋 Quick Setup Options:

            1. VPS + Nginx (Recommended)
            • Get a VPS (DigitalOcean, Linode, etc.)
            • Install Nginx
            • Configure reverse proxy to $deviceIp:$port
            • Get SSL certificate (Let's Encrypt)

            2. Cloudflare Tunnel (Free)
            • Install cloudflared on any server
            • Create tunnel to $deviceIp:$port
            • Zero configuration needed

            3. Dynamic DNS + Port Forwarding
            • Configure router port forwarding
            • Use DuckDNS or No-IP for dynamic DNS
            • Forward port $port to $deviceIp

            📖 See CUSTOM_DOMAIN_SETUP.md for detailed instructions.

            ⚠️ Security Notes:
            • Use strong API keys
            • Enable rate limiting
            • Monitor access logs
            • Consider IP whitelisting
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Setup Guide")
            .setMessage(guide)
            .setPositiveButton("OK", null)
            .show()
    }
}