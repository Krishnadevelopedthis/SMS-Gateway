import os
import uuid
import asyncio
from typing import Optional

from fastapi import (
    FastAPI,
    HTTPException,
    WebSocket,
    WebSocketDisconnect,
    Header,
)
from pydantic import BaseModel


# =========================================================
# APPLICATION
# =========================================================

app = FastAPI(
    title="CampusNetra SMS Cloud Gateway",
    version="1.0.0",
)


# =========================================================
# CONFIGURATION
# =========================================================

API_KEY = os.getenv("SMS_GATEWAY_API_KEY", "")

if not API_KEY:
    print("WARNING: SMS_GATEWAY_API_KEY is not configured")


# =========================================================
# CONNECTION STATE
# =========================================================

# Only one Android SMS gateway phone is supported.
connected_phone: Optional[WebSocket] = None

# Protects connected_phone changes.
phone_lock = asyncio.Lock()

# request_id -> Future
#
# When Cloud Gateway sends an SMS request to Android,
# it waits here for Android's response.
pending_requests: dict[str, asyncio.Future] = {}


# =========================================================
# REQUEST MODELS
# =========================================================

class SMSRequest(BaseModel):
    phone_number: str
    message: str


# =========================================================
# API KEY AUTHENTICATION
# =========================================================

def verify_api_key(provided_key: Optional[str]):
    """
    Validate API requests coming from CampusNetra.
    """

    if not API_KEY:
        raise HTTPException(
            status_code=503,
            detail="SMS gateway API key is not configured",
        )

    if provided_key != API_KEY:
        raise HTTPException(
            status_code=401,
            detail="Invalid API key",
        )


# =========================================================
# HEALTH / ROOT
# =========================================================

@app.get("/")
async def root():
    return {
        "service": "CampusNetra SMS Cloud Gateway",
        "status": "online",
    }


@app.head("/")
async def root_head():
    return


@app.get("/api/info")
async def info():
    return {
        "service": "CampusNetra SMS Cloud Gateway",
        "status": "active",
        "android_connected": connected_phone is not None,
    }


# =========================================================
# ANDROID WEBSOCKET CONNECTION
# =========================================================

@app.websocket("/ws/phone")
async def phone_connection(websocket: WebSocket):

    global connected_phone

    # -----------------------------------------------------
    # Authenticate Android phone BEFORE accepting
    # -----------------------------------------------------

    provided_key = websocket.headers.get("x-api-key")

    if not API_KEY or provided_key != API_KEY:

        print(
            "Rejected Android WebSocket connection: "
            "invalid API key"
        )

        await websocket.close(code=1008)

        return

    # -----------------------------------------------------
    # Accept WebSocket
    # -----------------------------------------------------

    await websocket.accept()

    # -----------------------------------------------------
    # Register Android phone
    # -----------------------------------------------------

    async with phone_lock:

        # Only one Android gateway should be connected.
        if connected_phone is not None:

            try:
                await connected_phone.close(
                    code=1000,
                    reason="Replaced by another Android gateway",
                )
            except Exception:
                pass

        connected_phone = websocket

    print("Android phone connected")

    # -----------------------------------------------------
    # Receive messages from Android
    # -----------------------------------------------------

    try:

        while True:

            message = await websocket.receive_json()

            print("Android response:", message)

            message_type = message.get("type")

            # -------------------------------------------------
            # Android registration message
            # -------------------------------------------------

            if message_type == "phone_connected":

                print(
                    "Android gateway registered successfully"
                )

                continue

            # -------------------------------------------------
            # SMS result
            # -------------------------------------------------

            request_id = message.get("request_id")

            if not request_id:
                continue

            future = pending_requests.pop(
                request_id,
                None,
            )

            if future is not None:

                if not future.done():

                    future.set_result(message)

    except WebSocketDisconnect:

        print("Android phone disconnected")

    except Exception as exc:

        print(
            "Android WebSocket error:",
            exc,
        )

    finally:

        # -----------------------------------------------------
        # Remove disconnected phone
        # -----------------------------------------------------

        async with phone_lock:

            if connected_phone is websocket:

                connected_phone = None

        print(
            "Android phone connection cleaned up"
        )


# =========================================================
# SEND SMS
# =========================================================

@app.post("/api/send")
async def send_sms(
    request: SMSRequest,
    x_api_key: Optional[str] = Header(
        default=None
    ),
):

    # -----------------------------------------------------
    # Authenticate CampusNetra
    # -----------------------------------------------------

    verify_api_key(x_api_key)

    # -----------------------------------------------------
    # Check Android phone
    # -----------------------------------------------------

    if connected_phone is None:

        raise HTTPException(
            status_code=503,
            detail="No Android phone connected",
        )

    # -----------------------------------------------------
    # Generate unique request ID
    # -----------------------------------------------------

    request_id = str(uuid.uuid4())

    loop = asyncio.get_running_loop()

    future = loop.create_future()

    pending_requests[request_id] = future

    # -----------------------------------------------------
    # Command for Android
    # -----------------------------------------------------

    payload = {
        "type": "send_sms",
        "request_id": request_id,
        "phone_number": request.phone_number,
        "message": request.message,
    }

    try:

        # -------------------------------------------------
        # Send command to Android
        # -------------------------------------------------

        await connected_phone.send_json(payload)

        print(
            f"SMS request sent to Android: "
            f"{request_id}"
        )

        # -------------------------------------------------
        # Wait for Android response
        # -------------------------------------------------

        try:

            result = await asyncio.wait_for(
                future,
                timeout=30,
            )

        except asyncio.TimeoutError:

            pending_requests.pop(
                request_id,
                None,
            )

            raise HTTPException(
                status_code=504,
                detail="Android phone did not respond",
            )

        # -------------------------------------------------
        # Return Android result
        # -------------------------------------------------

        return {
            "request_id": request_id,
            "status": result.get(
                "status",
                "unknown",
            ),
            "message": result.get(
                "message",
                "Android phone processed request",
            ),
            "sms_id": result.get(
                "sms_id"
            ),
        }

    except HTTPException:

        raise

    except Exception as exc:

        pending_requests.pop(
            request_id,
            None,
        )

        raise HTTPException(
            status_code=502,
            detail=(
                "Failed to communicate with "
                f"Android phone: {exc}"
            ),
        )