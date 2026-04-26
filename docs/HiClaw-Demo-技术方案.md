# HiClaw Demo 技术方案

> **目标**: 演示 JSP 页面与 HiClaw Worker 的 1:1 聊天  
> **核心难点**: HiClaw API 集成、Worker 生命周期管理、Matrix 通信

---

## 一、架构概览

```
┌─ JSP 页面 (用户入口) ──────────────────────────────┐
│  参数: userId                                      │
│  功能: 用户登录 → 分配 Worker → 1:1 聊天            │
│  UI: 自定义聊天界面 (JavaScript + Matrix REST API) │
└────────────────────────────────────────────────────┘
         ↕ HTTP
┌─ Java 后端 (Servlet + H2) ─────────────────────────┐
│  /login           → 用户登录/注册                   │
│  /api/chat/send   → 发送消息                        │
│  /api/chat/sync   → 获取新消息                      │
│  H2: users 表 (userId → workerName → roomId)       │
└────────────────────────────────────────────────────┘
         ↕ REST API
┌─ HiClaw Controller ────────────────────────────────┐
│  /api/v1/workers              → 创建 Worker         │
│  /api/v1/workers/{name}/wake  → 唤醒                │
│  /api/v1/workers/{name}/sleep → 休眠                │
└────────────────────────────────────────────────────┘
         ↕
┌─ HiClaw 基础设施 ──────────────────────────────────┐
│  Tuwunel (Matrix Homeserver)                       │
│  MinIO (文件存储)                                   │
│  Manager Agent (Worker 协调者)                      │
│  OpenClaw Workers (AI Agent 容器, ~500MB/个)        │
└────────────────────────────────────────────────────┘
```

---

## 二、核心流程

### 2.1 用户登录流程

```
用户访问 /chat.jsp?userId=abc123
    ↓
后端查 H2: SELECT * FROM users WHERE user_id='abc123'
    ↓
┌─ 新用户 ───────────────────────────────────────────┐
│  1. 调用 HiClaw: POST /api/v1/workers              │
│     Body: {"name":"worker-abc123","model":"..."}   │
│  2. 等待 Worker 就绪 (status.phase=Running)        │
│  3. 获取 Worker 的 Matrix Room ID                   │
│  4. INSERT INTO users (user_id, worker_name, room_id)│
│  5. 创建 Matrix 用户账号 (可选)                      │
└────────────────────────────────────────────────────┘
┌─ 老用户 ───────────────────────────────────────────┐
│  1. 查询 worker_name, room_id                       │
│  2. 调用 HiClaw: POST /api/v1/workers/{name}/wake  │
│  3. 等待 Worker 就绪                                │
│  4. 返回 roomId 给前端                              │
└────────────────────────────────────────────────────┘
    ↓
前端初始化 Matrix 连接，进入聊天界面
```

### 2.2 聊天消息流程

```
用户发送消息
    ↓
前端: PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
    ↓
Matrix Homeserver (Tuwunel) 接收并广播
    ↓
Worker (OpenClaw) 通过 Matrix Channel 收到消息
    ↓
Worker 调用 LLM → 生成回复
    ↓
Worker 发送回复到 Matrix Room
    ↓
前端通过 /sync 长轮询收到回复 → 显示
```

### 2.3 Worker 回收流程

```
用户退出 / 空闲超时触发
    ↓
调用 HiClaw: POST /api/v1/workers/{name}/sleep
    ↓
Worker 容器停止 (spec.state=Sleeping)
    ↓
┌─ 数据保留 ─────────────────────────────────────────┐
│  Matrix Room 历史: 完整保留在 Tuwunel               │
│  MinIO 数据: 完整保留                               │
│  容器销毁: 仅 OpenClaw 容器                          │
└────────────────────────────────────────────────────┘
下次唤醒 → 容器重建 → mc mirror 同步 → 聊天历史完整恢复
```

---

## 三、关键 API

### 3.1 HiClaw Controller API

| 操作 | 方法 | 端点 | Body |
|------|------|------|------|
| 创建 Worker | POST | `/api/v1/workers` | `{"name":"worker-{userId}","model":"qwen-max","runtime":"openclaw"}` |
| 唤醒 Worker | POST | `/api/v1/workers/{name}/wake` | 无 |
| 休眠 Worker | POST | `/api/v1/workers/{name}/sleep` | 无 |
| 查询状态 | GET | `/api/v1/workers/{name}` | - |

**创建 Worker 返回示例**:
```json
{
  "name": "worker-abc123",
  "phase": "Pending",
  "matrix_user_id": "@worker-abc123:matrix-local.hiclaw.io",
  "room_id": "!abc123:matrix-local.hiclaw.io"
}
```

### 3.2 Matrix Client-Server API

| 操作 | 方法 | 端点 |
|------|------|------|
| 登录 | POST | `/_matrix/client/v3/login` |
| 发送消息 | PUT | `/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}` |
| 同步消息 | GET | `/_matrix/client/v3/sync?timeout=30000&since={token}` |
| 获取历史 | GET | `/_matrix/client/v3/rooms/{roomId}/messages?dir=b&limit=50` |

---

## 四、数据模型

### H2 用户表

```sql
CREATE TABLE users (
    user_id VARCHAR(64) PRIMARY KEY,
    worker_name VARCHAR(64) NOT NULL,
    room_id VARCHAR(128) NOT NULL,
    matrix_user_id VARCHAR(128),
    matrix_access_token VARCHAR(256),
    last_active TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Worker 命名规则

| 项目 | 规则 | 示例 |
|------|------|------|
| Worker 名称 | `worker-{userId}` | `worker-abc123` |
| Matrix 用户 | `@worker-{userId}:{domain}` | `@worker-abc123:matrix-local.hiclaw.io` |
| Room 别名 | `#hiclaw-worker-{userId}:{domain}` | `#hiclaw-worker-abc123:matrix-local.hiclaw.io` |

---

## 五、技术决策

| 决策点 | 方案 | 理由 |
|--------|------|------|
| 用户管理 | H2 数据库 | Human CRD 不支持 Web 认证/活跃时间 |
| 聊天 UI | 自定义 JSP + Matrix REST API | 不依赖 Element Web，最大控制权 |
| Matrix SDK | 直接 REST API (MVP) | 零依赖，最快实现；后续可升级 matrix-js-sdk |
| Worker 回收 | sleep/wake API | 聊天历史完整保留在 Matrix Server |
| 空闲超时 | HiClaw 内置 720min | 可配置缩短至 30-60min |

---

## 六、踩坑要点

### 6.1 Worker 创建等待

```
POST /api/v1/workers → phase="Pending"
需要轮询 GET /api/v1/workers/{name} 直到 phase="Running"
等待时间: 30-60秒 (容器启动 + MinIO 同步 + Matrix 连接)
```

### 6.2 Matrix 认证

```
Tuwunel 默认认证方式: m.login.password
用户名: worker-{userId}
密码: 由 HiClaw Controller 在创建 Worker 时生成
存储位置: MinIO agents/{name}/credentials/matrix_password
```

**获取密码**:
```bash
mc cat minio/hiclaw-storage/agents/worker-abc123/credentials/matrix_password
```

或通过 Controller API 获取初始密码字段。

### 6.3 Room ID vs Room Alias

```
Room Alias: #hiclaw-worker-abc123:matrix-local.hiclaw.io  (人类可读)
Room ID:    !abc123xyz:matrix-local.hiclaw.io              (内部 ID)

发送消息必须用 Room ID (叹号开头)
可通过 Room Alias 查询 Room ID:
GET /_matrix/client/v3/directory/room/%23hiclaw-worker-abc123:matrix-local.hiclaw.io
```

### 6.4 Worker 回收后历史恢复

```
Worker Sleep → Wake 后，Matrix /sync 会返回完整 Room Timeline
OpenClaw 通过 historyLimit 配置决定加载多少历史消息
默认 historyLimit=50，可在 openclaw.json 中调整
```

---

## 七、实现优先级

| 优先级 | 任务 | 依赖 |
|--------|------|------|
| P0 | Java 后端: 用户登录 + Worker 创建 | HiClaw Controller API |
| P0 | Java 后端: Worker wake/sleep | HiClaw Controller API |
| P1 | JSP: 聊天 UI + Matrix REST API | Matrix 登录 + Room ID |
| P2 | H2 数据库: 用户表 + 查询 | 无 |
| P3 | 空闲超时 + 自动回收 | Worker lifecycle API |

---

## 八、后续扩展

- **matrix-js-sdk**: 打包后替换纯 REST API，获得内置 sync/重连/E2EE
- **多 Worker**: 一个用户对应多个专业 Worker
- **Team 协作**: Team Leader 协调多个 Worker
- **SSO 登录**: Matrix OIDC 支持