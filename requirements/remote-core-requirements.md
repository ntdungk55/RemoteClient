# Yêu cầu tính năng Remote Core

## 1. Đặc tả

**Vấn đề:**

Ứng dụng cần một Remote Server dùng chung để Android hoặc thiết bị khác có thể kết nối tới các process/window đang được quản lý trong các tab AI Chat. Remote phải bị giới hạn quyền bởi người dùng Windows và không được điều khiển nhầm window.

**Mục tiêu:**

- Tạo feature độc lập tại `remote/`, nằm ngang hàng với `mcp/` và `src/`.
- Cung cấp một Remote Server dùng chung cho toàn bộ ứng dụng.
- Server chỉ được khởi động hoặc dừng bằng thao tác thủ công của Windows user; không tự động start/stop theo target.
- Chỉ cho phép start khi có ít nhất một game window được chọn trong AI Chat.
- Cho phép nhiều remote session kết nối nhưng mỗi session chỉ gắn với một window sau handshake.
- Mặc định các permission remote đều được bật sau khi xác thực.
- Người dùng Windows là admin, có quyền cấp/revoke từng permission.
- Bảo đảm event được xử lý theo thứ tự server nhận được.

**Cấu trúc bắt buộc:**

```text
remote/
  src/
    domain/
    application/
    infrastructure/
    logic/
    ui/
```

`remote/ui/` chỉ chứa thành phần UI thuộc feature remote. Vị trí tích hợp cuối cùng vào UI chính sẽ được quyết định ở design.

**Input/Output:**

| Loại   | Mô tả                                      | Ví dụ                                      |
|--------|--------------------------------------------|--------------------------------------------|
| Input  | Kết nối, handshake, yêu cầu quyền, event   | `connect`, `subscribe_stream`, `chat`      |
| Input  | Window identity do remote chọn lúc đầu     | `pid`, `hwnd`, `creation_time`             |
| Output | Trạng thái server/session                   | `stopped`, `starting`, `running`, `stopping`, `error` |
| Output | Frame, state, tool result hoặc message      | JSON metadata và binary JPEG frame         |

**Permissions mặc định:**

| Permission             | Mặc định | Ý nghĩa                                  |
|------------------------|----------|------------------------------------------|
| `connect`              | Bật      | Đăng nhập bằng username/password server cấp |
| `view_stream`          | Bật      | Cho phép nhận frame                      |
| `view_game_state`      | Bật      | Cho phép nhận Game State                 |
| `send_chat`            | Bật      | Cho phép gửi message mới vào AI Chat     |
| `discover_tools`       | Bật      | Cho phép xem danh sách MCP tools         |

---

### **3. Project Structure**
#### **3.1 Package Organization**
- `com.example.remoteclient.network`: For Retrofit/OkHttp API interfaces.
- `com.example.remoteclient.model`: For data models (e.g., `RemoteServerState`, `RemoteTarget`).
- `com.example.remoteclient.ui`: For UI components (e.g., activities, fragments).
- `com.example.remoteclient.viewmodel`: For ViewModel classes.

#### **3.2 Gradle Dependencies**
Add the following dependencies:
- Retrofit: For HTTP communication.
- Gson/Moshi: For JSON parsing.
- LiveData/StateFlow: For reactive UI updates.

---

### **4. Implementation Steps**
#### **4.1 Define API Endpoints**
- Create a Retrofit interface for the `RemoteHttpServer` endpoints:
  - `POST /connect`
  - `POST /handshake`
  - `GET /state`
  - `POST /permissions`

#### **4.2 Create Data Models**
- Define models for:
  - `RemoteServerState`
  - `RemoteTarget`
  - `RemoteCredentials`
  - `RemoteSession`

#### **4.3 Build UI**
- **Login Screen**: For entering credentials.
- **Session List**: To display active sessions.
- **Session Details**: To manage permissions and view state.

#### **4.4 Implement ViewModel**
- Use ViewModel to:
  - Fetch data from the server.
  - Manage UI state.
  - Handle user interactions.

#### **4.5 Handle Data Parsing**
- Parse JSON responses using Gson/Moshi.
- Decode binary frames into images.

---

### **5. Next Steps**
1. **Set Up Retrofit**: Define API endpoints and test connectivity.
2. **Create Data Models**: Implement models for server state, sessions, and permissions.
3. **Build UI**: Start with the login screen and session list.
4. **Integrate ViewModel**: Connect UI with server data.
5. **Test and Debug**: Ensure all features work as expected.

---

### **6. Server Connection Details**
#### **6.1 Server Endpoints**
- **Base URL**: The server runs on `http://127.0.0.1:8765` by default.
- **Endpoints**:
  - `GET /status`: Retrieve the current server status.
  - `POST /handshake`: Perform a handshake to establish a session.
  - `POST /sessions/{id}/close`: Close a specific session.

#### **6.2 Authentication**
- **Credentials**:
  - The server generates four-digit credentials for each session.
  - Example:
    - Username: `1234`
    - Password: `5678`

#### **6.3 Data Streaming**
- **Mechanism**:
  - The server does not explicitly implement streaming in the provided code.
  - Data such as JSON metadata and binary frames are likely sent as part of HTTP responses.
- **Expected Data**:
  - JSON metadata for server state, sessions, and permissions.
  - Binary JPEG frames for visual data.

#### **6.4 Permissions**
- Default permissions include:
  - `connect`: Allows login using server-provided credentials.
  - `view_stream`: Enables receiving frame data.
  - `view_game_state`: Allows access to game state information.
  - `send_chat`: Permits sending messages to AI Chat.
  - `discover_tools`: Grants access to the list of MCP tools.

#### **6.5 Error Handling**
- **Common Errors**:
  - `404 not_found`: Returned for invalid endpoints.
  - `401 unauthorized`: Returned for invalid credentials.
  - `500 internal_server_error`: Indicates server-side issues.
- **Client Actions**:
  - Retry on transient errors.
  - Display appropriate error messages to the user.