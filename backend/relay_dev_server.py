from __future__ import annotations

import asyncio
import secrets
import time
import uuid
from typing import Any

from fastapi import FastAPI, HTTPException, Response, WebSocket, WebSocketDisconnect
from pydantic import BaseModel


app = FastAPI(
    title="Relay Development Backend",
    version="0.1.0",
)


# ----------------------------------------------------------------------
# In-memory development state
# ----------------------------------------------------------------------

access_tokens: dict[str, dict[str, Any]] = {}
refresh_tokens: dict[str, dict[str, Any]] = {}

incidents: dict[str, dict[str, Any]] = {}

incident_event_history: dict[str, list[dict[str, Any]]] = {}

processed_commands: dict[str, dict[str, Any]] = {}

timeline_events: dict[str, dict[str, Any]] = {}

connected_sockets: list[WebSocket] = []

state_lock = asyncio.Lock()


# ----------------------------------------------------------------------
# Models
# ----------------------------------------------------------------------

class LoginRequest(BaseModel):
    username: str
    password: str


class RefreshRequest(BaseModel):
    refreshToken: str


class RevokeRequest(BaseModel):
    refreshToken: str


# ----------------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------------

def now_ms() -> int:
    return int(time.time() * 1000)


def new_event_id() -> str:
    return f"EVT-{uuid.uuid4().hex[:12].upper()}"


def issue_session(
    username: str,
    user_id: str | None = None,
) -> dict[str, Any]:

    resolved_user_id = (
        user_id
        if user_id is not None
        else f"USER-{uuid.uuid4().hex[:8].upper()}"
    )

    access_token = secrets.token_urlsafe(32)
    refresh_token = secrets.token_urlsafe(40)

    expires_at = now_ms() + (60 * 60 * 1000)

    principal = {
        "userId": resolved_user_id,
        "userName": username,
    }

    access_tokens[access_token] = {
        **principal,
        "expiresAt": expires_at,
    }

    refresh_tokens[refresh_token] = principal

    return {
        "userId": resolved_user_id,
        "userName": username,
        "accessToken": access_token,
        "refreshToken": refresh_token,
        "accessTokenExpiresAt": expires_at,
    }


def bearer_token(
    websocket: WebSocket,
) -> str | None:

    authorization = websocket.headers.get(
        "authorization"
    )

    if not authorization:
        return None

    prefix = "Bearer "

    if not authorization.startswith(prefix):
        return None

    token = authorization[len(prefix):].strip()

    return token or None


def authenticated_principal(
    token: str | None,
) -> dict[str, Any] | None:

    if not token:
        return None

    principal = access_tokens.get(token)

    if principal is None:
        return None

    expires_at = int(
        principal.get(
            "expiresAt",
            0,
        )
    )

    if expires_at <= now_ms():
        access_tokens.pop(
            token,
            None,
        )

        return None

    return principal


async def safe_send_json(
    websocket: WebSocket,
    payload: dict[str, Any],
) -> bool:

    try:
        await websocket.send_json(payload)
        return True

    except Exception:
        return False


async def broadcast(
    payload: dict[str, Any],
    exclude: WebSocket | None = None,
) -> None:

    stale: list[WebSocket] = []

    for socket in list(connected_sockets):

        if socket is exclude:
            continue

        if not await safe_send_json(
            socket,
            payload,
        ):
            stale.append(socket)

    for socket in stale:

        if socket in connected_sockets:
            connected_sockets.remove(socket)


def next_sequence(
    incident_id: str,
) -> int:

    current = incidents.get(
        incident_id
    )

    if current is None:
        return 1

    return int(
        current.get(
            "sequence",
            0,
        )
    ) + 1


def remember_incident_event(
    incident_id: str,
    event: dict[str, Any],
) -> None:

    history = incident_event_history.setdefault(
        incident_id,
        [],
    )

    history.append(event)


def accepted_ack(
    command: str,
    command_id: str,
    incident_id: str,
) -> dict[str, Any]:

    return {
        "type": "command.accepted",
        "command": command,
        "commandId": command_id,
        "incidentId": incident_id,
    }


def rejected_ack(
    command: str,
    command_id: str,
    incident_id: str,
    reason: str,
) -> dict[str, Any]:

    return {
        "type": "command.rejected",
        "command": command,
        "commandId": command_id,
        "incidentId": incident_id,
        "reason": reason,
    }


# ----------------------------------------------------------------------
# HTTP
# ----------------------------------------------------------------------

@app.get("/")
async def root() -> dict[str, Any]:

    return {
        "service": "relay-dev-backend",
        "status": "ok",
        "incidents": len(incidents),
        "webSockets": len(connected_sockets),
    }


@app.get("/health")
async def health() -> dict[str, str]:

    return {
        "status": "ok",
    }


@app.post("/auth/login")
async def login(
    request: LoginRequest,
) -> dict[str, Any]:

    username = request.username.strip()
    password = request.password

    if not username or not password.strip():
        raise HTTPException(
            status_code=401,
            detail="Invalid credentials",
        )

    # Development backend:
    # any non-empty username/password pair is accepted.
    session = issue_session(
        username=username,
    )

    print(
        f"AUTH LOGIN user={username}",
        flush=True,
    )

    return session


@app.post("/auth/refresh")
async def refresh(
    request: RefreshRequest,
) -> dict[str, Any]:

    principal = refresh_tokens.pop(
        request.refreshToken,
        None,
    )

    if principal is None:
        raise HTTPException(
            status_code=401,
            detail="Invalid refresh token",
        )

    session = issue_session(
        username=principal["userName"],
        user_id=principal["userId"],
    )

    print(
        f"AUTH REFRESH user={principal['userName']}",
        flush=True,
    )

    return session


@app.post(
    "/auth/revoke",
    status_code=204,
)
async def revoke(
    request: RevokeRequest,
) -> Response:

    principal = refresh_tokens.pop(
        request.refreshToken,
        None,
    )

    if principal is not None:

        user_id = principal["userId"]

        stale_access_tokens = [
            token
            for token, value
            in access_tokens.items()
            if value.get("userId") == user_id
        ]

        for token in stale_access_tokens:
            access_tokens.pop(
                token,
                None,
            )

        print(
            f"AUTH REVOKE user={principal['userName']}",
            flush=True,
        )

    return Response(
        status_code=204
    )


# ----------------------------------------------------------------------
# WebSocket command handling
# ----------------------------------------------------------------------

async def handle_incident_create(
    websocket: WebSocket,
    message: dict[str, Any],
) -> None:

    command = "incident.create"

    command_id = str(
        message.get(
            "commandId",
            "",
        )
    )

    incident_id = str(
        message.get(
            "incidentId",
            "",
        )
    )

    title = str(
        message.get(
            "title",
            "",
        )
    ).strip()

    status = str(
        message.get(
            "status",
            "",
        )
    ).strip()

    severity = str(
        message.get(
            "severity",
            "MEDIUM",
        )
    ).upper()

    if (
        not command_id
        or not incident_id
        or not title
        or not status
    ):
        await websocket.send_json(
            rejected_ack(
                command=command,
                command_id=command_id,
                incident_id=incident_id,
                reason="invalid_payload",
            )
        )
        return

    previous = processed_commands.get(
        command_id
    )

    if previous is not None:
        await websocket.send_json(
            previous
        )
        return

    async with state_lock:

        if incident_id in incidents:

            ack = rejected_ack(
                command=command,
                command_id=command_id,
                incident_id=incident_id,
                reason="incident_already_exists",
            )

            processed_commands[
                command_id
            ] = ack

            await websocket.send_json(
                ack
            )

            return

        sequence = 1
        occurred_at = now_ms()

        incident = {
            "id": incident_id,
            "title": title,
            "status": status,
            "severity": severity,
            "sequence": sequence,
        }

        incidents[
            incident_id
        ] = incident

        event = {
            "type": "incident.created",
            "eventId": new_event_id(),
            "incidentId": incident_id,
            "occurredAt": occurred_at,
            "title": title,
            "status": status,
            "severity": severity,
            "sequence": sequence,
        }

        remember_incident_event(
            incident_id,
            event,
        )

        ack = accepted_ack(
            command=command,
            command_id=command_id,
            incident_id=incident_id,
        )

        processed_commands[
            command_id
        ] = ack

    await websocket.send_json(
        ack
    )

    await broadcast(
        event
    )

    print(
        f"INCIDENT CREATE id={incident_id} sequence={sequence}",
        flush=True,
    )


async def handle_status_update(
    websocket: WebSocket,
    message: dict[str, Any],
) -> None:

    command = "incident.status.update"

    command_id = str(
        message.get(
            "commandId",
            "",
        )
    )

    incident_id = str(
        message.get(
            "incidentId",
            "",
        )
    )

    status = str(
        message.get(
            "status",
            "",
        )
    ).strip()

    previous = processed_commands.get(
        command_id
    )

    if previous is not None:
        await websocket.send_json(
            previous
        )
        return

    async with state_lock:

        incident = incidents.get(
            incident_id
        )

        if (
            incident is None
            or not status
        ):
            ack = rejected_ack(
                command=command,
                command_id=command_id,
                incident_id=incident_id,
                reason="incident_not_found_or_invalid_status",
            )

            processed_commands[
                command_id
            ] = ack

            await websocket.send_json(
                ack
            )

            return

        sequence = next_sequence(
            incident_id
        )

        incident["status"] = status
        incident["sequence"] = sequence

        event = {
            "type": "incident.updated",
            "eventId": new_event_id(),
            "incidentId": incident_id,
            "occurredAt": now_ms(),
            "status": status,
            "sequence": sequence,
        }

        remember_incident_event(
            incident_id,
            event,
        )

        ack = accepted_ack(
            command=command,
            command_id=command_id,
            incident_id=incident_id,
        )

        processed_commands[
            command_id
        ] = ack

    await websocket.send_json(
        ack
    )

    await broadcast(
        event
    )

    print(
        f"INCIDENT STATUS id={incident_id} status={status} sequence={sequence}",
        flush=True,
    )


async def handle_severity_update(
    websocket: WebSocket,
    message: dict[str, Any],
) -> None:

    command = "incident.severity.update"

    command_id = str(
        message.get(
            "commandId",
            "",
        )
    )

    incident_id = str(
        message.get(
            "incidentId",
            "",
        )
    )

    severity = str(
        message.get(
            "severity",
            "",
        )
    ).upper()

    previous = processed_commands.get(
        command_id
    )

    if previous is not None:
        await websocket.send_json(
            previous
        )
        return

    async with state_lock:

        incident = incidents.get(
            incident_id
        )

        if (
            incident is None
            or not severity
        ):
            ack = rejected_ack(
                command=command,
                command_id=command_id,
                incident_id=incident_id,
                reason="incident_not_found_or_invalid_severity",
            )

            processed_commands[
                command_id
            ] = ack

            await websocket.send_json(
                ack
            )

            return

        sequence = next_sequence(
            incident_id
        )

        incident["severity"] = severity
        incident["sequence"] = sequence

        event = {
            "type": "incident.updated",
            "eventId": new_event_id(),
            "incidentId": incident_id,
            "occurredAt": now_ms(),
            "severity": severity,
            "sequence": sequence,
        }

        remember_incident_event(
            incident_id,
            event,
        )

        ack = accepted_ack(
            command=command,
            command_id=command_id,
            incident_id=incident_id,
        )

        processed_commands[
            command_id
        ] = ack

    await websocket.send_json(
        ack
    )

    await broadcast(
        event
    )

    print(
        f"INCIDENT SEVERITY id={incident_id} severity={severity} sequence={sequence}",
        flush=True,
    )


async def handle_timeline_create(
    websocket: WebSocket,
    message: dict[str, Any],
) -> None:

    entry_id = str(
        message.get(
            "entryId",
            "",
        )
    )

    incident_id = str(
        message.get(
            "incidentId",
            "",
        )
    )

    text = str(
        message.get(
            "message",
            "",
        )
    ).strip()

    author = str(
        message.get(
            "author",
            "",
        )
    ).strip()

    previous = timeline_events.get(
        entry_id
    )

    if previous is not None:

        await websocket.send_json(
            previous
        )

        return

    if (
        incident_id not in incidents
        or not entry_id
        or not text
    ):
        await websocket.send_json(
            {
                "type": "timeline.entry.rejected",
                "entryId": entry_id,
                "incidentId": incident_id,
                "reason": "invalid_timeline_entry",
            }
        )

        return

    event = {
        "type": "timeline.entry.added",
        "eventId": new_event_id(),
        "incidentId": incident_id,
        "occurredAt": now_ms(),
        "entryId": entry_id,
        "message": text,
        "author": author or "Unknown",
    }

    timeline_events[
        entry_id
    ] = event

    # This event is the acknowledgement expected by
    # WebSocketTimelineSender.
    await websocket.send_json(
        event
    )

    # Other sockets, including the long-lived realtime
    # listener, receive the authoritative event too.
    await broadcast(
        event,
        exclude=websocket,
    )

    print(
        f"TIMELINE ADD incident={incident_id} entry={entry_id}",
        flush=True,
    )


async def handle_replay_request(
    websocket: WebSocket,
    message: dict[str, Any],
) -> None:

    incident_id = str(
        message.get(
            "incidentId",
            "",
        )
    )

    try:
        from_sequence = int(
            message.get(
                "fromSequence",
                0,
            )
        )

        through_sequence = int(
            message.get(
                "throughSequence",
                0,
            )
        )

    except (TypeError, ValueError):
        return

    history = incident_event_history.get(
        incident_id,
        [],
    )

    replayed = 0

    for event in history:

        sequence = int(
            event.get(
                "sequence",
                0,
            )
        )

        if (
            from_sequence
            <= sequence
            <= through_sequence
        ):
            await websocket.send_json(
                event
            )

            replayed += 1

    print(
        (
            f"REPLAY incident={incident_id} "
            f"from={from_sequence} "
            f"through={through_sequence} "
            f"events={replayed}"
        ),
        flush=True,
    )


# ----------------------------------------------------------------------
# Root WebSocket endpoint expected by Android:
#
# ws://127.0.0.1:9000
# ----------------------------------------------------------------------

@app.websocket("/")
async def relay_socket(
    websocket: WebSocket,
) -> None:

    token = bearer_token(
        websocket
    )

    principal = authenticated_principal(
        token
    )

    if principal is None:

        await websocket.accept()

        await websocket.send_json(
            {
                "type": "session.invalidated",
            }
        )

        await websocket.close(
            code=4001,
            reason="Session invalidated",
        )

        return

    await websocket.accept()

    connected_sockets.append(
        websocket
    )

    username = principal[
        "userName"
    ]

    print(
        (
            f"WEBSOCKET CONNECT "
            f"user={username} "
            f"active={len(connected_sockets)}"
        ),
        flush=True,
    )

    try:

        while True:

            message = await websocket.receive_json()

            message_type = str(
                message.get(
                    "type",
                    "",
                )
            )

            print(
                f"WS MESSAGE user={username} type={message_type}",
                flush=True,
            )

            if message_type == "incident.create":

                await handle_incident_create(
                    websocket,
                    message,
                )

            elif message_type == "incident.status.update":

                await handle_status_update(
                    websocket,
                    message,
                )

            elif message_type == "incident.severity.update":

                await handle_severity_update(
                    websocket,
                    message,
                )

            elif message_type == "timeline.entry.create":

                await handle_timeline_create(
                    websocket,
                    message,
                )

            elif message_type == "replay.request":

                await handle_replay_request(
                    websocket,
                    message,
                )

            else:

                await websocket.send_json(
                    {
                        "type": "command.rejected",
                        "command": message_type,
                        "commandId": str(
                            message.get(
                                "commandId",
                                "",
                            )
                        ),
                        "incidentId": str(
                            message.get(
                                "incidentId",
                                "",
                            )
                        ),
                        "reason": "unsupported_command",
                    }
                )

    except WebSocketDisconnect:
        pass

    except Exception as error:

        print(
            (
                "WEBSOCKET ERROR "
                f"user={username} "
                f"type={type(error).__name__} "
                f"message={error}"
            ),
            flush=True,
        )

    finally:

        if websocket in connected_sockets:
            connected_sockets.remove(
                websocket
            )

        print(
            (
                f"WEBSOCKET DISCONNECT "
                f"user={username} "
                f"active={len(connected_sockets)}"
            ),
            flush=True,
        )

# ----------------------------------------------------------------------
# Development-only session testing
# ----------------------------------------------------------------------

@app.post("/dev/invalidate-access")
async def dev_invalidate_access() -> dict[str, Any]:
    invalidated_access_tokens = len(access_tokens)

    access_tokens.clear()

    notified_sockets = 0

    for socket in list(connected_sockets):
        if await safe_send_json(
            socket,
            {
                "type": "session.invalidated",
            },
        ):
            notified_sockets += 1

    refresh_tokens_preserved = len(refresh_tokens)

    print(
        (
            "DEV ACCESS INVALIDATED "
            f"tokens={invalidated_access_tokens} "
            f"sockets={notified_sockets} "
            f"refresh={refresh_tokens_preserved}"
        ),
        flush=True,
    )

    return {
        "status": "ok",
        "invalidatedAccessTokens": invalidated_access_tokens,
        "notifiedSockets": notified_sockets,
        "refreshTokensPreserved": refresh_tokens_preserved,
    }

if __name__ == "__main__":

    import uvicorn

    uvicorn.run(
        "relay_dev_server:app",
        host="127.0.0.1",
        port=9000,
        reload=False,
        log_level="info",
    )