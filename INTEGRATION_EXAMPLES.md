# SMS Gateway Integration Examples

This document provides code examples for integrating with the SMS Gateway API in various programming languages and platforms.

## Configuration

Before using any examples, make sure you have:
- SMS Gateway app installed and running on an Android device
- API server enabled in the app
- API key copied from the app
- Device IP address and port number

## JavaScript/Node.js

### Basic SMS Sending

```javascript
const axios = require('axios');

class SmsGateway {
    constructor(baseUrl, apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.headers = {
            'Content-Type': 'application/json',
            'X-API-Key': apiKey
        };
    }

    async sendSms(phoneNumber, message, requestId = null) {
        try {
            const response = await axios.post(`${this.baseUrl}/api/send`, {
                phone_number: phoneNumber,
                message: message,
                request_id: requestId
            }, { headers: this.headers });

            return response.data;
        } catch (error) {
            throw new Error(`SMS sending failed: ${error.response?.data?.error || error.message}`);
        }
    }

    async sendBulkSms(messages) {
        try {
            const response = await axios.post(`${this.baseUrl}/api/send-bulk`, {
                messages: messages
            }, { headers: this.headers });

            return response.data;
        } catch (error) {
            throw new Error(`Bulk SMS sending failed: ${error.response?.data?.error || error.message}`);
        }
    }

    async getSmsStatus(smsId = null, requestId = null) {
        try {
            let url = `${this.baseUrl}/api/status`;
            if (smsId) {
                url += `?sms_id=${smsId}`;
            } else if (requestId) {
                url += `?request_id=${requestId}`;
            } else {
                throw new Error('Either smsId or requestId must be provided');
            }

            const response = await axios.get(url, { headers: this.headers });
            return response.data;
        } catch (error) {
            throw new Error(`Status check failed: ${error.response?.data?.error || error.message}`);
        }
    }

    async getSmsHistory(limit = 50, offset = 0, status = null, phoneNumber = null) {
        try {
            let url = `${this.baseUrl}/api/history?limit=${limit}&offset=${offset}`;
            if (status) url += `&status=${status}`;
            if (phoneNumber) url += `&phone_number=${encodeURIComponent(phoneNumber)}`;

            const response = await axios.get(url, { headers: this.headers });
            return response.data;
        } catch (error) {
            throw new Error(`History retrieval failed: ${error.response?.data?.error || error.message}`);
        }
    }
}

// Usage example
async function example() {
    const gateway = new SmsGateway('http://192.168.1.100:8080', 'your_api_key_here');

    try {
        // Send single SMS
        const result = await gateway.sendSms('+1234567890', 'Hello from Node.js!');
        console.log('SMS sent:', result);

        // Check status
        const status = await gateway.getSmsStatus(result.sms_id);
        console.log('SMS status:', status);

        // Send bulk SMS
        const bulkResult = await gateway.sendBulkSms([
            { phone_number: '+1234567890', message: 'Bulk message 1' },
            { phone_number: '+0987654321', message: 'Bulk message 2' }
        ]);
        console.log('Bulk SMS result:', bulkResult);

    } catch (error) {
        console.error('Error:', error.message);
    }
}

example();
```

### Express.js Webhook Integration

```javascript
const express = require('express');
const app = express();

app.use(express.json());

const gateway = new SmsGateway('http://192.168.1.100:8080', 'your_api_key_here');

// Endpoint to send SMS via webhook
app.post('/send-sms', async (req, res) => {
    try {
        const { phone_number, message } = req.body;
        
        if (!phone_number || !message) {
            return res.status(400).json({ error: 'phone_number and message are required' });
        }

        const result = await gateway.sendSms(phone_number, message);
        res.json(result);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

app.listen(3000, () => {
    console.log('Webhook server running on port 3000');
});
```

## Python

### Basic SMS Client

```python
import requests
import json
from typing import Optional, List, Dict, Any

class SmsGateway:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.rstrip('/')
        self.api_key = api_key
        self.headers = {
            'Content-Type': 'application/json',
            'X-API-Key': api_key
        }

    def send_sms(self, phone_number: str, message: str, request_id: Optional[str] = None) -> Dict[str, Any]:
        """Send a single SMS message."""
        url = f"{self.base_url}/api/send"
        data = {
            'phone_number': phone_number,
            'message': message
        }
        if request_id:
            data['request_id'] = request_id

        response = requests.post(url, headers=self.headers, json=data)
        response.raise_for_status()
        return response.json()

    def send_bulk_sms(self, messages: List[Dict[str, str]]) -> Dict[str, Any]:
        """Send multiple SMS messages."""
        url = f"{self.base_url}/api/send-bulk"
        data = {'messages': messages}

        response = requests.post(url, headers=self.headers, json=data)
        response.raise_for_status()
        return response.json()

    def get_sms_status(self, sms_id: Optional[int] = None, request_id: Optional[str] = None) -> Dict[str, Any]:
        """Get SMS delivery status."""
        if not sms_id and not request_id:
            raise ValueError("Either sms_id or request_id must be provided")

        url = f"{self.base_url}/api/status"
        params = {}
        if sms_id:
            params['sms_id'] = sms_id
        elif request_id:
            params['request_id'] = request_id

        response = requests.get(url, headers=self.headers, params=params)
        response.raise_for_status()
        return response.json()

    def get_sms_history(self, limit: int = 50, offset: int = 0, 
                       status: Optional[str] = None, phone_number: Optional[str] = None) -> Dict[str, Any]:
        """Get SMS history with optional filtering."""
        url = f"{self.base_url}/api/history"
        params = {'limit': limit, 'offset': offset}
        
        if status:
            params['status'] = status
        if phone_number:
            params['phone_number'] = phone_number

        response = requests.get(url, headers=self.headers, params=params)
        response.raise_for_status()
        return response.json()

# Usage example
def main():
    gateway = SmsGateway('http://192.168.1.100:8080', 'your_api_key_here')

    try:
        # Send single SMS
        result = gateway.send_sms('+1234567890', 'Hello from Python!')
        print(f"SMS sent: {result}")

        # Check status
        status = gateway.get_sms_status(sms_id=result['sms_id'])
        print(f"SMS status: {status}")

        # Send bulk SMS
        messages = [
            {'phone_number': '+1234567890', 'message': 'Bulk message 1'},
            {'phone_number': '+0987654321', 'message': 'Bulk message 2'}
        ]
        bulk_result = gateway.send_bulk_sms(messages)
        print(f"Bulk SMS result: {bulk_result}")

        # Get history
        history = gateway.get_sms_history(limit=10, status='DELIVERED')
        print(f"SMS history: {history}")

    except requests.exceptions.RequestException as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
```

### Django Integration

```python
# views.py
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
import json

@csrf_exempt
@require_http_methods(["POST"])
def send_sms_view(request):
    try:
        data = json.loads(request.body)
        phone_number = data.get('phone_number')
        message = data.get('message')
        
        if not phone_number or not message:
            return JsonResponse({'error': 'phone_number and message are required'}, status=400)

        gateway = SmsGateway('http://192.168.1.100:8080', 'your_api_key_here')
        result = gateway.send_sms(phone_number, message)
        
        return JsonResponse(result)
    except Exception as e:
        return JsonResponse({'error': str(e)}, status=500)

# urls.py
from django.urls import path
from . import views

urlpatterns = [
    path('send-sms/', views.send_sms_view, name='send_sms'),
]
```

## PHP

### Basic SMS Client

```php
<?php

class SmsGateway {
    private $baseUrl;
    private $apiKey;
    private $headers;

    public function __construct($baseUrl, $apiKey) {
        $this->baseUrl = rtrim($baseUrl, '/');
        $this->apiKey = $apiKey;
        $this->headers = [
            'Content-Type: application/json',
            'X-API-Key: ' . $apiKey
        ];
    }

    public function sendSms($phoneNumber, $message, $requestId = null) {
        $url = $this->baseUrl . '/api/send';
        $data = [
            'phone_number' => $phoneNumber,
            'message' => $message
        ];
        
        if ($requestId) {
            $data['request_id'] = $requestId;
        }

        return $this->makeRequest('POST', $url, $data);
    }

    public function sendBulkSms($messages) {
        $url = $this->baseUrl . '/api/send-bulk';
        $data = ['messages' => $messages];

        return $this->makeRequest('POST', $url, $data);
    }

    public function getSmsStatus($smsId = null, $requestId = null) {
        if (!$smsId && !$requestId) {
            throw new Exception('Either smsId or requestId must be provided');
        }

        $url = $this->baseUrl . '/api/status';
        if ($smsId) {
            $url .= '?sms_id=' . $smsId;
        } elseif ($requestId) {
            $url .= '?request_id=' . urlencode($requestId);
        }

        return $this->makeRequest('GET', $url);
    }

    public function getSmsHistory($limit = 50, $offset = 0, $status = null, $phoneNumber = null) {
        $url = $this->baseUrl . '/api/history?limit=' . $limit . '&offset=' . $offset;
        
        if ($status) {
            $url .= '&status=' . urlencode($status);
        }
        if ($phoneNumber) {
            $url .= '&phone_number=' . urlencode($phoneNumber);
        }

        return $this->makeRequest('GET', $url);
    }

    private function makeRequest($method, $url, $data = null) {
        $ch = curl_init();
        
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $this->headers);
        
        if ($method === 'POST' && $data) {
            curl_setopt($ch, CURLOPT_POST, true);
            curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($data));
        }

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($httpCode >= 400) {
            throw new Exception('HTTP Error: ' . $httpCode . ' - ' . $response);
        }

        return json_decode($response, true);
    }
}

// Usage example
try {
    $gateway = new SmsGateway('http://192.168.1.100:8080', 'your_api_key_here');

    // Send single SMS
    $result = $gateway->sendSms('+1234567890', 'Hello from PHP!');
    echo "SMS sent: " . json_encode($result) . "\n";

    // Check status
    $status = $gateway->getSmsStatus($result['sms_id']);
    echo "SMS status: " . json_encode($status) . "\n";

    // Send bulk SMS
    $messages = [
        ['phone_number' => '+1234567890', 'message' => 'Bulk message 1'],
        ['phone_number' => '+0987654321', 'message' => 'Bulk message 2']
    ];
    $bulkResult = $gateway->sendBulkSms($messages);
    echo "Bulk SMS result: " . json_encode($bulkResult) . "\n";

} catch (Exception $e) {
    echo "Error: " . $e->getMessage() . "\n";
}
?>
```

## Java

### Basic SMS Client

```java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class SmsGateway {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;

    public SmsGateway(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    public JsonObject sendSms(String phoneNumber, String message, String requestId) throws IOException, InterruptedException {
        JsonObject data = new JsonObject();
        data.addProperty("phone_number", phoneNumber);
        data.addProperty("message", message);
        if (requestId != null) {
            data.addProperty("request_id", requestId);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/send"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(data)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP Error: " + response.statusCode() + " - " + response.body());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    public JsonObject getSmsStatus(Long smsId, String requestId) throws IOException, InterruptedException {
        if (smsId == null && requestId == null) {
            throw new IllegalArgumentException("Either smsId or requestId must be provided");
        }

        String url = baseUrl + "/api/status";
        if (smsId != null) {
            url += "?sms_id=" + smsId;
        } else {
            url += "?request_id=" + requestId;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP Error: " + response.statusCode() + " - " + response.body());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    // Usage example
    public static void main(String[] args) {
        try {
            SmsGateway gateway = new SmsGateway("http://192.168.1.100:8080", "your_api_key_here");

            // Send SMS
            JsonObject result = gateway.sendSms("+1234567890", "Hello from Java!", null);
            System.out.println("SMS sent: " + result);

            // Check status
            Long smsId = result.get("sms_id").getAsLong();
            JsonObject status = gateway.getSmsStatus(smsId, null);
            System.out.println("SMS status: " + status);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

## cURL Examples

### Send Single SMS
```bash
curl -X POST http://192.168.1.100:8080/api/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your_api_key_here" \
  -d '{
    "phone_number": "+1234567890",
    "message": "Hello from cURL!"
  }'
```

### Send Bulk SMS
```bash
curl -X POST http://192.168.1.100:8080/api/send-bulk \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your_api_key_here" \
  -d '{
    "messages": [
      {
        "phone_number": "+1234567890",
        "message": "Bulk message 1"
      },
      {
        "phone_number": "+0987654321",
        "message": "Bulk message 2"
      }
    ]
  }'
```

### Check SMS Status
```bash
curl -X GET "http://192.168.1.100:8080/api/status?sms_id=123" \
  -H "X-API-Key: your_api_key_here"
```

### Get SMS History
```bash
curl -X GET "http://192.168.1.100:8080/api/history?limit=10&status=DELIVERED" \
  -H "X-API-Key: your_api_key_here"
```

## Error Handling Best Practices

1. **Always check HTTP status codes**
2. **Parse error responses for detailed error information**
3. **Implement retry logic for transient failures**
4. **Log API requests and responses for debugging**
5. **Handle rate limiting gracefully**

## Security Best Practices

1. **Store API keys securely (environment variables, secure storage)**
2. **Use HTTPS when possible (with reverse proxy)**
3. **Validate phone numbers before sending**
4. **Implement your own rate limiting if needed**
5. **Monitor API usage and costs**
