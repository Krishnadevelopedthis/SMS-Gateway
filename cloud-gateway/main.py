import os
import uuid
import asyncio
from typing import Optional

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, Header
from pydantic import BaseModel


app = FastAPI(title="CampusNetra SMS Cloud Gateway")


# ---------------------------------------------------------
# Configuration
# ---------------------------------------------------------

API_KEY = os.getenv("SMS_GATEWAY_API_KEY", "")

if not API_KEY:
    print("WARNING: SMS_GATEWAY_API_KEY is not configured")


# ---------------------------------------------------------
# Connected Android phones
# ---------------------------------------------------------

connected_phone: Optional[WebSocket] = None
phone_lock = asyncio.Lock()

pending_requests: dict[str, asyncio.Future] = {}


# ---------------------------------------------------------
# Models
# ---------------------------------------------------------

class SMSRequest(BaseModel):
    phone_number: str
    message: str


# ---------------------------------------------------------
# Authentication
# ---------------------------------------------------------

def verify_api_key(provided_key: Optional[str]):
    if not API_KEY:
        raise HTTPException(
            status_code=503,
            detail="SMS gateway API key is not configured"
        )

    if provided_key != API_KEY:
        raise HTTPException(
            status_code=401,
            detail="Invalid API key"
        )


# ---------------------------------------------------------
# Basic endpoints
# ---------------------------------------------------------

@app.get("/")
async def root():
    return {
        "service": "CampusNetra SMS Cloud Gateway",
        "status": "online"
    }


@app.get("/api/info")
async def info():
    return {
        "service": "CampusNetra SMS Cloud Gateway",
        "status": "active",
        "android_connected": connected_phone is not None
    }


# ---------------------------------------------------------
# Android WebSocket
# ---------------------------------------------------------

@app.websocket("/ws/phone")
async def phone_connection(websocket: WebSocket):

    global connected_phone

    await websocket.accept()

    async with phone_lock:

        # Disconnect previous phone if one exists
        if connected_phone is not None:
            try:
                await connected_phone.close()
            except Exception:
                pass

        connected_phone = websocket

    print("Android phone connected")

    try:

        while True:

            message = await websocket.receive_json()

            print("Android response:", message)

            request_id = message.get("request_id")

            if request_id and request_id in pending_requests:

                future = pending_requests.pop(request_id)

                if not future.done():
                    future.set_result(message)

    except WebSocketDisconnect:

        print("Android phone disconnected")

    except Exception as exc:

        print("Android WebSocket error:", exc)

    finally:

        async with phone_lock:

            if connected_phone is websocket:
                connected_phone = None


# ---------------------------------------------------------
# Send SMS
# ---------------------------------------------------------

@app.post("/api/send")
async def send_sms(
    request: SMSRequest,
    x_api_key: Optional[str] = Header(default=None)
):

    verify_api_key(x_api_key)

    if connected_phone is None:

        raise HTTPException(
            status_code=503,
            detail="No Android phone connected"
        )

    request_id = str(uuid.uuid4())

    loop = asyncio.get_running_loop()

    future = loop.create_future()

    pending_requests[request_id] = future

    payload = {
        "type": "send_sms",
        "request_id": request_id,
        "phone_number": request.phone_number,
        "message": request.message
    }

    try:

        await connected_phone.send_json(payload)

        try:

            result = await asyncio.wait_for(
                future,
                timeout=30
            )

        except asyncio.TimeoutError:

            pending_requests.pop(request_id, None)

            raise HTTPException(
                status_code=504,
                detail="Android phone did not respond"
            )

        return {
            "request_id": request_id,
            "status": result.get("status", "unknown"),
            "message": result.get(
                "message",
                "Android phone processed request"
            )
        }

    except HTTPException:
        raise

    except Exception as exc:

        pending_requests.pop(request_id, None)

        raise HTTPException(
            status_code=502,
            detail=f"Failed to communicate with Android phone: {exc}"
        )
