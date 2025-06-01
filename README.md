# SMS Gateway Android Application

A comprehensive Android application that turns your Android device into an SMS gateway, allowing you to send SMS messages through a REST API using the device's cellular connection.

## Features

### Core SMS Functionality
- ✅ Send SMS messages using Android's SmsManager
- ✅ Track SMS delivery status (Pending, Sent, Delivered, Failed)
- ✅ SMS history with database storage
- ✅ Support for long messages (automatic splitting)
- ✅ Real-time status updates via broadcast receivers

### REST API Server
- ✅ Built-in HTTP server (NanoHTTPD)
- ✅ RESTful API endpoints for SMS operations
- ✅ JSON request/response format
- ✅ CORS support for web applications
- ✅ Comprehensive error handling

### Security & Authentication
- ✅ API key authentication
- ✅ Rate limiting (configurable per minute/hour)
- ✅ Request validation and sanitization
- ✅ Secure API key generation

### User Interface
- ✅ Modern Material Design UI
- ✅ SMS sending interface with floating action button
- ✅ Real-time SMS history display
- ✅ Server status and configuration management
- ✅ API key and port configuration
- ✅ **Complete API URLs display with device IP**
- ✅ **Built-in API testing interface**
- ✅ **Copy-to-clipboard functionality for URLs and API keys**
- ✅ **Detailed endpoint documentation in-app**
- ✅ **External domain configuration and setup**
- ✅ **Automatic URL switching (local ↔ external)**

### Background Services
- ✅ Foreground service for API server
- ✅ Automatic SMS cleanup (configurable)
- ✅ Persistent notifications
- ✅ Service lifecycle management

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/info` | Get API information (no auth required) |
| POST | `/api/send` | Send single SMS message |
| POST | `/api/send-bulk` | Send multiple SMS messages |
| GET | `/api/status` | Check SMS delivery status |
| GET | `/api/history` | Get SMS history with filtering |
| GET | `/api/config` | Get server configuration |

## Quick Start

### 1. Installation
```bash
# Clone the repository
git clone <repository-url>
cd sms-gateway

# Build and install
./gradlew installDebug
```

### 2. Setup
1. Grant SMS permissions when prompted
2. Enable the API server in the app
3. Note the API key and device IP address
4. Configure port if needed (default: 8080)

### 3. Send Your First SMS

**Local Network:**
```bash
curl -X POST http://192.168.1.100:8080/api/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your_api_key_here" \
  -d '{
    "phone_number": "+1234567890",
    "message": "Hello from SMS Gateway!"
  }'
```

**External Domain (after setup):**
```bash
curl -X POST https://sms.yourdomain.com/api/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your_api_key_here" \
  -d '{
    "phone_number": "+1234567890",
    "message": "Hello from anywhere!"
  }'
```

> 💡 **Want external access?** See the [Custom Domain Setup](#custom-domain-setup-external-access) section below to make your SMS Gateway accessible from anywhere on the internet!

## Architecture

### Core Components

1. **SmsManager** - Handles SMS sending and status tracking
2. **SmsApiServer** - HTTP server with REST API endpoints
3. **SmsGatewayService** - Background service for API server
4. **SmsStatusReceiver** - Broadcast receiver for SMS status updates
5. **SmsDatabase** - Room database for SMS history storage

### Database Schema

```kotlin
@Entity(tableName = "sms_history")
data class SmsEntity(
    val id: Long,
    val phoneNumber: String,
    val message: String,
    val timestamp: Long,
    val status: SmsStatus,
    val apiKey: String?,
    val requestId: String?,
    val errorMessage: String?,
    val deliveryTimestamp: Long?
)
```

### API Authentication

- **Header**: `X-API-Key: your_api_key`
- **Bearer Token**: `Authorization: Bearer your_api_key`
- **Rate Limiting**: 10 requests/minute, 100 requests/hour (configurable)

## Configuration

### App Settings
- **API Key**: Auto-generated, can be regenerated
- **Server Port**: Default 8080, configurable 1024-65535
- **Rate Limiting**: Configurable limits per minute/hour
- **Auto Cleanup**: Automatic deletion of old SMS records

### Permissions Required
- `SEND_SMS` - Send SMS messages
- `READ_SMS` - Read SMS for status tracking
- `RECEIVE_SMS` - Receive delivery confirmations
- `INTERNET` - API server functionality
- `FOREGROUND_SERVICE` - Background service

## Integration Examples

### JavaScript/Node.js
```javascript
const axios = require('axios');

const gateway = {
    baseUrl: 'http://192.168.1.100:8080',
    apiKey: 'your_api_key_here'
};

async function sendSms(phoneNumber, message) {
    const response = await axios.post(`${gateway.baseUrl}/api/send`, {
        phone_number: phoneNumber,
        message: message
    }, {
        headers: { 'X-API-Key': gateway.apiKey }
    });
    return response.data;
}
```

### Python
```python
import requests

def send_sms(phone_number, message):
    response = requests.post(
        'http://192.168.1.100:8080/api/send',
        json={'phone_number': phone_number, 'message': message},
        headers={'X-API-Key': 'your_api_key_here'}
    )
    return response.json()
```

### PHP
```php
$data = json_encode([
    'phone_number' => '+1234567890',
    'message' => 'Hello from PHP!'
]);

$context = stream_context_create([
    'http' => [
        'method' => 'POST',
        'header' => [
            'Content-Type: application/json',
            'X-API-Key: your_api_key_here'
        ],
        'content' => $data
    ]
]);

$result = file_get_contents('http://192.168.1.100:8080/api/send', false, $context);
```

## Documentation

This README contains comprehensive documentation including:
- ✅ **Complete setup guide** (above)
- ✅ **Custom domain configuration** (above)
- ✅ **API reference** (above)
- ✅ **Security considerations** (above)
- ✅ **Troubleshooting guide** (above)

Additional detailed documentation:
- 📖 [API Documentation](API_DOCUMENTATION.md) - Complete API reference with examples
- 🛠️ [Setup Instructions](SETUP_INSTRUCTIONS.md) - Detailed setup guide with troubleshooting
- 💻 [Integration Examples](INTEGRATION_EXAMPLES.md) - Code examples for various programming languages

## Technical Specifications

- **Minimum Android Version**: Android 6.0 (API 23)
- **Target Android Version**: Android 14 (API 35)
- **Language**: Kotlin
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite)
- **HTTP Server**: NanoHTTPD
- **JSON Processing**: Gson
- **Async Operations**: Kotlin Coroutines

## Security Considerations

1. **Network Security**: API server binds to all interfaces - use firewall rules for production
2. **API Key Protection**: Store API keys securely, rotate regularly
3. **Rate Limiting**: Built-in protection against abuse
4. **Input Validation**: All inputs are validated and sanitized
5. **Permissions**: Minimal required permissions requested

## Troubleshooting

### Common Issues
- **SMS not sending**: Check SIM card, network, and permissions
- **API not accessible**: Verify IP address, port, and firewall settings
- **Authentication errors**: Ensure correct API key is being used
- **Rate limiting**: Check current usage against configured limits

### Logs
```bash
# View app logs
adb logcat | grep "SMS"

# View specific component logs
adb logcat | grep "SmsManager\|SmsApiServer\|SmsGatewayService"
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Custom Domain Setup (External Access)

### Overview
By default, the SMS Gateway API only works on your local network. To make it accessible from anywhere on the internet via a custom domain (e.g., `https://sms.yourdomain.com`), you need to set up a reverse proxy.

### Architecture
```
Internet → Your Domain → VPS/Server → Android Device (Local Network)
```

### Why You Need This
- **Local Limitation**: The API server runs on your device's local IP (e.g., 192.168.1.100:8080)
- **No External Access**: External networks can't reach your device directly
- **Solution**: Use a reverse proxy to forward requests from your domain to your Android device

### Built-in App Support
The app now includes built-in support for external domains:
- **"Setup External Domain"** button in the main interface
- **Automatic URL switching** between local and external access
- **Built-in setup guides** with step-by-step instructions
- **Real-time URL updates** when switching modes

### Setup Options

#### Option 1: Cloudflare Tunnel (Recommended - Free & Easy)

**Requirements**: Any server/VPS, Cloudflare account

```bash
# 1. Install cloudflared on any server
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared-linux-amd64.deb

# 2. Authenticate with Cloudflare
cloudflared tunnel login

# 3. Create tunnel
cloudflared tunnel create sms-gateway

# 4. Configure tunnel (replace YOUR_ANDROID_IP with your device's IP)
cat > ~/.cloudflared/config.yml << EOF
tunnel: sms-gateway
credentials-file: /home/user/.cloudflared/YOUR_TUNNEL_ID.json

ingress:
  - hostname: sms.yourdomain.com
    service: http://YOUR_ANDROID_IP:8080
  - service: http_status:404
EOF

# 5. Add DNS record
cloudflared tunnel route dns sms-gateway sms.yourdomain.com

# 6. Run tunnel
cloudflared tunnel run sms-gateway
```

#### Option 2: VPS + Nginx (Advanced Users)

**Requirements**: VPS with public IP, domain pointing to VPS

Create `/etc/nginx/sites-available/sms-gateway`:
```nginx
server {
    listen 80;
    server_name sms.yourdomain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name sms.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/sms.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sms.yourdomain.com/privkey.pem;

    # Security headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=sms_api:10m rate=10r/m;
    limit_req zone=sms_api burst=20 nodelay;

    location /api/ {
        # Forward to Android device (replace YOUR_ANDROID_IP)
        proxy_pass http://YOUR_ANDROID_IP:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeout settings
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;

        # CORS headers
        add_header Access-Control-Allow-Origin *;
        add_header Access-Control-Allow-Methods "GET, POST, OPTIONS";
        add_header Access-Control-Allow-Headers "Content-Type, Authorization, X-API-Key";
    }
}
```

**SSL Certificate Setup:**
```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx

# Get SSL certificate
sudo certbot --nginx -d sms.yourdomain.com

# Enable site
sudo ln -s /etc/nginx/sites-available/sms-gateway /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

#### Option 3: Dynamic DNS + Port Forwarding (Home Users)

**Requirements**: Router with port forwarding, dynamic DNS service

1. **Router Configuration**:
   - Set static IP for Android device
   - Forward port 8080 to Android device IP
   - Enable UPnP if available

2. **Dynamic DNS Setup** (example with DuckDNS):
   ```bash
   # Update IP automatically
   curl "https://www.duckdns.org/update?domains=yourdomain&token=YOUR_TOKEN&ip="

   # Add to crontab for auto-update
   */5 * * * * curl "https://www.duckdns.org/update?domains=yourdomain&token=YOUR_TOKEN&ip=" >/dev/null 2>&1
   ```

### App Configuration

1. **In the Android App**:
   - Tap **"Setup External Domain"**
   - Enter your domain (e.g., `sms.yourdomain.com`)
   - Enable **"Use External Domain"**
   - App automatically updates all URLs

2. **Test External Access**:
   ```bash
   # Test server info (no auth required)
   curl https://sms.yourdomain.com/api/info

   # Test SMS sending
   curl -X POST https://sms.yourdomain.com/api/send \
     -H "Content-Type: application/json" \
     -H "X-API-Key: your_api_key" \
     -d '{"phone_number": "+1234567890", "message": "Hello from custom domain!"}'
   ```

### Security Considerations

1. **API Key Security**:
   - Use strong, unique API keys
   - Rotate keys regularly
   - Monitor usage logs

2. **Rate Limiting**:
   - App has built-in rate limiting
   - Add proxy-level rate limiting for extra protection

3. **IP Whitelisting** (optional):
   ```nginx
   # In Nginx config
   allow 1.2.3.4;    # Your trusted IPs
   deny all;
   ```

4. **Request Logging**:
   ```nginx
   # Custom log format for monitoring
   log_format sms_api '$remote_addr - $remote_user [$time_local] '
                      '"$request" $status $body_bytes_sent '
                      '"$http_referer" "$http_user_agent" '
                      '"$http_x_api_key"';

   access_log /var/log/nginx/sms_api.log sms_api;
   ```

### Cost Comparison

| Option | Monthly Cost | Difficulty | Features |
|--------|-------------|------------|----------|
| **Cloudflare Tunnel** | Free | Easy | SSL, DDoS protection, global CDN |
| **VPS + Nginx** | $5-10 | Medium | Full control, custom features |
| **Dynamic DNS** | Free-$2 | Hard | Basic access, no SSL by default |
| **Domain** | $10-15/year | - | Required for all options |

### Troubleshooting

**Common Issues**:
1. **Connection Refused**: Check if Android device is reachable from proxy server
2. **SSL Errors**: Verify certificate installation and renewal
3. **API Key Issues**: Ensure headers are forwarded correctly
4. **Timeout Errors**: Adjust proxy timeout settings

**Network Tests**:
```bash
# From proxy server, test Android device
curl -I http://ANDROID_IP:8080/api/info

# Check if port is open
nmap -p 8080 ANDROID_IP

# Monitor logs
sudo tail -f /var/log/nginx/sms_api.log
adb logcat | grep "SMS"
```

### Benefits of External Access

✅ **Global Access**: Send SMS from anywhere on the internet
✅ **HTTPS Security**: Encrypted communication with SSL certificates
✅ **Professional URLs**: Use your own domain instead of IP addresses
✅ **Stable Access**: No dependency on changing local IP addresses
✅ **Easy Integration**: Standard HTTPS endpoints for any application

## Support

For support and questions:
- Check the troubleshooting section above
- Review the API documentation
- Check device logs for errors
- Ensure all permissions are granted
- For external domain issues, verify proxy configuration
