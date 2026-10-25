# RemoteClient Review Report

## Summary

The current Android client is not ready for integration with the Remote Server in `D:\Project\Auto\Auto_MU`.

Main blockers:

- Retrofit base URL is invalid because it has no trailing slash.
- Android network permission is missing.
- `127.0.0.1` is not a reachable Windows-host address from Android.
- The server binds to `127.0.0.1` by default, so external Android devices cannot reach it.
- The client handshake payload is incomplete because it does not send `target_hwnd`.
- Client models do not match the actual server JSON contract.
- Session close does not send the required Bearer session token.
- The main activity does not integrate the Login or Dashboard screens.
- WebSocket/streaming, permissions, game state, chat, heartbeat, reconnect, and stale-state handling are not implemented.

## Reference Server Contract

Source reviewed in `D:\Project\Auto\Auto_MU`:

- `remote/src/application/lifecycle.py`
- `remote/src/infrastructure/http_server.py`
- `remote/src/domain/models.py`

Current HTTP endpoints:

| Method | Endpoint | Contract |
|---|---|---|
| GET | `/status` | Returns `status`, `endpoint`, numeric `sessions`, and `targets` array. |
| POST | `/handshake` | Requires valid credentials and a valid `target_hwnd`; returns a full session object with HTTP `201`. |
| POST | `/sessions/{id}/close` | Requires `Authorization: Bearer {session_id}`. |

The server currently defaults to `127.0.0.1:8765`. The server must be configured to bind a LAN-reachable address for a physical Android device.

## Findings

### BLOCKER-01: Invalid Retrofit base URL

Files:

- `app/src/main/java/com/example/remoteclient/viewmodel/LoginViewModel.kt:20`
- `app/src/main/java/com/example/remoteclient/viewmodel/DashboardViewModel.kt:23`

Current value:

```text
http://127.0.0.1:8765
```

Retrofit requires the base URL to end with `/`.

Required action:

- Use a configurable endpoint.
- Ensure the value ends with `/`, for example `http://10.0.2.2:8765/` for the Android Emulator.

### BLOCKER-02: Android cannot reach the Windows server through `127.0.0.1`

The client hardcodes `127.0.0.1`. On Android this points to the Android device/emulator itself, not the Windows host.

Required action:

- Android Emulator: use `10.0.2.2` for the Windows host.
- Physical device: use the Windows machine LAN IP, for example `192.168.1.10`.
- Make the endpoint editable or configurable instead of hardcoding it.
- Configure the Windows Remote Server to bind to a LAN-reachable address (`0.0.0.0` or the LAN IP) and allow the port through Windows Firewall.

### BLOCKER-03: Missing Android Internet permission

File:

- `app/src/main/AndroidManifest.xml`

The manifest has no `android.permission.INTERNET` declaration.

Required action:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

If HTTP cleartext is retained, configure the Android network security policy appropriately. Prefer HTTPS for non-local deployments.

### BLOCKER-04: Handshake request is incomplete

Client:

- `app/src/main/java/com/example/remoteclient/network/ApiService.kt:12`
- `app/src/main/java/com/example/remoteclient/model/Models.kt:32-35`

The client sends only `username` and `password`.

The server requires:

```json
{
  "username": "1234",
  "password": "5678",
  "device_name": "Android",
  "target_hwnd": 12345
}
```

Without `target_hwnd`, the server returns `409 target_unavailable`.

Required action:

- Add a handshake request model containing `username`, `password`, `device_name`, and `target_hwnd`.
- Load targets from `/status`.
- Add UI for selecting exactly one target before or during handshake.
- Keep the selected target bound to the session; do not silently switch targets after handshake.

### BLOCKER-05: `/status` response model does not match the server

Client model:

- `app/src/main/java/com/example/remoteclient/model/Models.kt:11-16`

The client expects:

```text
state: RemoteServerState
sessions: List<RemoteSession>
```

The server returns:

```json
{
  "status": "running",
  "endpoint": "http://127.0.0.1:8765",
  "sessions": 0,
  "targets": [
    {
      "hwnd": 12345,
      "pid": 678,
      "window_title": "...",
      "process_name": "...",
      "creation_time": "..."
    }
  ]
}
```

Required action:

- Rename/map `status` correctly.
- Model `sessions` as an integer for `/status`.
- Add `targets: List<RemoteTarget>`.
- Add `creation_time` to `RemoteTarget`.
- Use a separate session-list/session-detail model if a future endpoint returns session objects.

### BLOCKER-06: Handshake response model is incomplete

Client:

- `app/src/main/java/com/example/remoteclient/model/Models.kt:38-40`

The client expects only `session_id`.

The server returns:

```json
{
  "session_id": "...",
  "device_name": "Android",
  "target": { "hwnd": 12345, "pid": 678, "window_title": "...", "process_name": "...", "creation_time": "..." },
  "connected_at": "...",
  "permissions": {
    "connect": true,
    "view_stream": true,
    "view_game_state": true,
    "send_chat": true,
    "discover_tools": true,
    "call_readonly_tools": true,
    "call_mutating_tools": true
  }
}
```

Required action:

- Model the complete response.
- Persist the session ID only in secure app storage for the active session.
- Display target and permission state in the UI.

### BLOCKER-07: Close-session request lacks authorization

Client:

- `app/src/main/java/com/example/remoteclient/network/ApiService.kt:15`
- `app/src/main/java/com/example/remoteclient/viewmodel/DashboardViewModel.kt:59-64`

The server requires:

```http
Authorization: Bearer {session_id}
```

The client currently sends no header, so the server returns `401 invalid_session`.

Required action:

- Add the Authorization header to the close-session request.
- Handle `401`, `404`, and other non-success responses explicitly.

### HIGH-08: Main activity does not use the implemented screens

File:

- `app/src/main/java/com/example/remoteclient/MainActivity.kt:20-27`

The activity only renders `Greeting("Android")`.

Required action:

- Add connection/login state navigation.
- Show Login screen first.
- Navigate to target/session dashboard only after a successful handshake.
- Preserve session state across recomposition and configuration changes.

### HIGH-09: Required Remote Core features are missing

The requirement requires the following, but the current project does not implement them:

- Active-window selection and session-to-window binding.
- Permission display and permission-revocation handling.
- `/permissions` or equivalent permission snapshot integration.
- WebSocket or streaming transport.
- JPEG binary frame decoding and latest-frame display.
- Game-state JSON parsing with timestamp, sequence/frame ID, and target identity.
- Chat/event submission with server FIFO ordering.
- Heartbeat and reconnect backoff.
- `connected`, `stale`, `target_unavailable`, and `permission_denied` states.
- Cancellation when the Android app is closed or backgrounded.
- Tool catalog and tool-call permissions.

### MEDIUM-10: Error handling is too generic

Files:

- `app/src/main/java/com/example/remoteclient/viewmodel/LoginViewModel.kt:26-41`
- `app/src/main/java/com/example/remoteclient/viewmodel/DashboardViewModel.kt:34-66`

All errors are converted to generic `Error` or silently ignored.

Required action:

- Preserve HTTP status and server error code.
- Show actionable messages for authentication failure, unavailable target, permission denial, server closed, timeout, and network failure.
- Add retry behavior only for transient failures.

### MEDIUM-11: API calls use synchronous `execute()` inside coroutines

The current code uses Retrofit `Call.execute()` inside `viewModelScope.launch`.

Required action:

- Prefer Retrofit suspend functions and structured coroutine cancellation.
- Configure connect/read/write timeouts from the requirement.
- Cancel in-flight requests when the ViewModel/session is cleared.

### MEDIUM-12: No automated contract/integration tests in RemoteClient

Required tests:

- Base URL and endpoint configuration.
- `/status` JSON parsing.
- Successful handshake with `target_hwnd`.
- Invalid credentials (`401`).
- Missing/unavailable target (`409`).
- Close session with Bearer token.
- Permission mapping.
- Reconnect and stale state behavior.

## Recommended Fix Order

1. Fix endpoint configuration, Retrofit trailing slash, Internet permission, and Android-to-Windows network addressing.
2. Align `/status`, handshake request/response, target, permission, and session models with the server contract.
3. Implement target selection and connect/login navigation.
4. Add session authorization and explicit HTTP error handling.
5. Add permission state and revoke handling.
6. Implement WebSocket/stream/state/chat requirements.
7. Add reconnect, heartbeat, stale-state handling, and contract tests.

## Acceptance Checklist

- [ ] Android can reach the Windows server using a configurable endpoint.
- [ ] Server is started manually and is reachable from the selected Android environment.
- [ ] `/status` parses successfully and exposes targets.
- [ ] User can select one target before handshake.
- [ ] Handshake succeeds with credentials, device name, and target HWND.
- [ ] The authenticated session is bound to the selected target.
- [ ] Permissions are displayed and disable features when revoked.
- [ ] Closing a session sends the required Bearer token.
- [ ] JPEG stream and JSON game state are handled.
- [ ] Chat events preserve server ordering.
- [ ] Disconnects trigger heartbeat/backoff/stale-state behavior.
- [ ] No silent target switching or privilege escalation occurs.
- [ ] Contract and integration tests pass.

