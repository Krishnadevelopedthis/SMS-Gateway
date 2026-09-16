from fastapi import FastAPI, WebSocket, WebSocketDisconnect

app = FastAPI(title="CampusNetra SMS Cloud Gateway")

connected_phones: set[WebSocket] = set()


@app.get("/")
async def root():
    return {
        "service": "CampusNetra SMS Cloud Gateway",
        "status": "online"
    }


@app.get("/api/info")
async def info():
    return {
        "status": "active",
        "connected_phones": len(connected_phones)
    }


@app.websocket("/ws/phone")
async def phone_connection(websocket: WebSocket):
    await websocket.accept()
    connected_phones.add(websocket)

    print("Android phone connected")

    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        connected_phones.discard(websocket)
        print("Android phone disconnected")