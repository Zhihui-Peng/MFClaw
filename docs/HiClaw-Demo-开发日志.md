# HiClaw Demo 开发日志

> **目的**: 记录 Demo 开发过程中的部署、踩坑、解决方案，为后续真实项目提供参考。

---

## 一、环境准备

### 1.1 开发环境

| 项目 | 版本/配置 |
|------|----------|
| 操作系统 | Windows |
| Java | 8 |
| 构建工具 | Maven |
| Web 容器 | Jetty（嵌入式） |
| 数据库 | H2（嵌入式） |

### 1.2 外部依赖

| 项目 | 版本 | 说明 |
|------|------|------|
| HiClaw | v1.1.0 | 多 Agent 编排平台 |
| Docker Desktop | — | HiClaw 运行环境 |

---

## 二、HiClaw 部署

### 2.1 部署要求

| 项目 | 要求 | 说明 |
|------|------|------|
| 操作系统 | Windows 10 (1903+) 64 位 或 Windows 11 | 不支持虚拟机中的 Windows |
| WSL 2 | 必须启用 | Docker Desktop 安装时会自动提示 |
| Docker Desktop | 4.20+ | https://www.docker.com/products/docker-desktop/ |
| PowerShell | 7.0+ (推荐) | Windows 自带 5.1 也可用 |
| CPU | 最低 2 核，推荐 4 核+ | OpenClaw Worker 约 500MB/个 |
| 内存 | 最低 4 GB，推荐 8 GB+ | |
| 磁盘 | 10+ GB 可用空间 | 首次拉取镜像约 2-3 GB |
| LLM API Key | 必须 | 阿里云百炼 / OpenAI / DeepSeek 等 |

**重要说明**：HiClaw 完全基于 Docker 运行，Java 8/Maven/Jetty 是 Demo 项目的依赖，不是 HiClaw 的依赖。

### 2.2 部署步骤

#### 步骤 1：安装并启动 Docker Desktop
1. 下载 Docker Desktop for Windows 并安装
2. 启动 Docker Desktop，等待左下角图标变绿（Engine running）
3. 验证：PowerShell 执行 `docker info`

#### 步骤 2：准备 LLM API Key
推荐阿里云百炼 CodingPlan（国内用户免费编程专用）：
- 访问 https://www.aliyun.com/benefit/scene/codingplan 激活
- 获取 API Key

#### 步骤 3：运行安装脚本
打开 PowerShell，执行：
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; $wc=New-Object Net.WebClient; $wc.Encoding=[Text.Encoding]::UTF8; iex $wc.DownloadString('https://higress.ai/hiclaw/install.ps1')
```

#### 步骤 4：交互式配置
脚本会引导完成约 10 步配置：
- 语言：选 1 (中文)
- 安装模式：选 1 (快速开始)
- LLM 提供商：按需选择
- 模型接口：CodingPlan 选 1
- API Key：粘贴 Key
- 网络模式：选 1 (仅本机)
- 端口域名：全部默认回车
- Worker 运行时：选 1 (OpenClaw)

#### 步骤 5：等待安装完成
脚本自动：
- 按时区选择镜像仓库（国内 → 杭州节点）
- 拉取 hiclaw-controller + hiclaw-manager 镜像
- 创建并启动容器
- 等待 Manager Agent 就绪（超时 300s）

安装成功后会输出：
```
http://127.0.0.1:18088/#/login
用户名: admin
密码: admin<随机字符串>    ← 务必记录！
```

### 2.3 部署验证

#### 检查容器运行
```powershell
docker ps
# 应看到: hiclaw-controller 和 hiclaw-manager
```

#### 访问 Web 控制台
| 控制台 | URL |
|--------|-----|
| Element Web (IM 客户端) | http://127.0.0.1:18088 |
| Higress 控制台 (AI 网关) | http://localhost:18001 |
| OpenClaw 控制台 | http://localhost:18888 |

#### 查看配置
```powershell
# 查看所有配置（含密码、API Key）
notepad "$env:USERPROFILE\hiclaw-manager.env"
```

### 2.4 Demo 对接关键信息

HiClaw Controller API 入口：
- **Higress Gateway**: `http://127.0.0.1:18080`
- **Matrix API**: `http://matrix-local.hiclaw.io:18080/_matrix/client/...`
- **MinIO API**: `http://fs-local.hiclaw.io:18080`

### 2.5 踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| PowerShell 闪退 | Docker Desktop 未运行 | 先启动 Docker Desktop，等图标变绿 |
| "Docker is not running" | Docker 引擎未就绪 | 启动 Docker Desktop，等待就绪 |
| API 连通性测试失败 | Key 不完整/未开通服务 | 检查 Key 无空格；CodingPlan 需单独激活 |
| Manager Agent 启动超时 | WSL 2 内存不足 | 编辑 `.wslconfig` 设 `memory=8GB` |
| Element Web 无法访问 | 开了系统代理 | 关闭代理，或将 `*-local.hiclaw.io` 加入绕过列表 |
| 端口 18088 被占用 | 其他程序占用 | `netstat -ano \| findstr "18088"` 查找关闭

---

## 三、Demo 实现

### 3.1 项目结构

```
mfclaw-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/mfclaw/
│   │   │       ├── servlet/
│   │   │       ├── hiclaw/
│   │   │       └── matrix/
│   │   ├── webapp/
│   │   │   ├── WEB-INF/
│   │   │   └── chat.jsp
│   │   └── resources/
│   └── test/
└── docs/
```

### 3.2 实现进度

| 模块 | 状态 | 备注 |
|------|------|------|
| HiClaw 部署 | ✅ 完成 | Element Web 可登录，能与 Manager 交流 |
| 项目初始化 | ✅ 完成 | Maven 项目结构 |
| H2 数据库 | ✅ 完成 | H2Database.java 用户表 CRUD |
| HiClaw API 客户端 | ✅ 完成 | HiClawClient.java (通过 docker exec curl) |
| Matrix API 客户端 | ✅ 完成 | MatrixClient.java (登录/发送/同步) |
| Servlet | ✅ 完成 | LoginServlet + ChatServlet + ApiServlet |
| 聁天界面 | ✅ 完成 | login.html + chat.html (静态 HTML) |
| Jetty 嵌入式启动 | ✅ 完成 | DemoApplication.java |
| 编译运行验证 | ✅ 完成 | 所有 API 测试通过 |

### 3.3 实现架构

```
用户浏览器 (localhost:9090)
       │
       ├─ login.html ─→ POST /login ─→ LoginServlet
       │                                 │
       │                                 ├─ 查询 H2Database (用户是否存在)
       │                                 ├─ 调用 HiClawClient (分配 Worker)
       │                                 └─ 创建 session，重定向 /chat
       │
       └─ chat.html ─→ /chat/info ─→ ChatServlet (获取会话信息)
                     ─→ POST /chat ─→ ChatServlet (发送消息)
                     ─→ /chat/messages ─→ ChatServlet (轮询消息)
```

### 3.4 踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| HiClaw Controller API (8090) 未暴露 | 默认只暴露 8001/8080/8088 | 通过 docker exec 在容器内调用 curl |
| Worker 创建返回 HTTP 500 | AI route authorization 配置问题 | 暂用现有 worker-test-user 演示 |
| Worker API 字段名不一致 | 返回 `roomID` 而非 `room_id` | 代码中检查 `roomID` 字段 |
| Matrix 用户无法创建 | Dendrite 不支持 Synapse Admin API | 使用 Admin 账号作为系统账号 |
| JSP 编译 NullPointerException | Jetty apache-jsp 缺少完整配置 | 改用静态 HTML + JavaScript |
| shade 插件签名冲突 | 多个 JAR 含签名文件 | 添加过滤器排除 .SF/.DSA/.RSA |
| servlet-api ClassNotFoundException | scope=provided 未打包 | 改为 compile scope |
| 工作目录错误 | 相对路径解析到父目录 | 从项目目录启动：`java -jar target/xxx.jar` |
| Servlet 映射不匹配 | `/chat` 未覆盖 `/chat/info` | 改为 `/chat/*` |

### 3.5 关键发现

#### HiClaw Controller API
- Token 位置：`/var/run/hiclaw/cli-token`
- API 基础 URL：`http://127.0.0.1:8090/api/v1`
- Worker 创建：`POST /workers` — 当前有配置问题
- Worker 查询：`GET /workers/{name}` — 返回 `roomID`, `matrixUserID`
- Worker 命令：`POST /workers/{name}/wake`, `/sleep`

#### Matrix 凭证
- Admin 用户：`admin` / 密码在环境变量 `HICLAW_ADMIN_PASSWORD`
- Matrix Domain：`matrix-local.hiclaw.io:18080`
- Admin API 通过 Gateway：`http://127.0.0.1:18080/_matrix/client/...`
- **Registration Token**：`HICLAW_REGISTRATION_TOKEN` 环境变量，用于注册新用户

#### 现有可用资源
- Worker: `worker-test-user` (Running)
- Room: `!wLMhJj3uHEYMfETrvx:matrix-local.hiclaw.io:18080`
- Room 成员：admin, manager, worker-test-user

#### Worker 自动回复配置

要让 Worker 自动回复房间内的所有消息（无需 @mention），需要修改 `openclaw.json`：

```json
{
  "channels": {
    "matrix": {
      "groupPolicy": "open",           // 允许所有用户
      "groups": {
        "*": {
          "allow": true,
          "autoReply": true            // 自动回复，无需 @mention
        }
      }
    }
  }
}
```

**配置存储位置**：
- MinIO: `hiclaw-storage/agents/{worker-name}/openclaw.json`
- 容器内: `/root/hiclaw-fs/agents/{worker-name}/openclaw.json`

**配置更新流程**：
1. 修改 MinIO 中的配置文件
2. 复制到 Worker 容器
3. 发送 `kill -HUP 1` 让 OpenClaw 重新加载配置

**注意**：重启 Worker 容器会重新生成配置，覆盖手动修改！

#### 用户注册流程

新用户登录时需要：
1. 使用 Registration Token 注册 Matrix 账号
2. Admin 邀请新用户加入 Worker 房间
3. 新用户加入房间
4. 之后用户可用自己的账号发送消息

---

## 四、关键代码片段

### 4.1 HiClawClient - 通过 docker exec 调用 API

```java
// 由于 Controller API (8090) 未暴露到宿主机，通过 docker exec 在容器内调用
private JsonNode executeRequest(String method, String path, String body) {
    List<String> cmd = new ArrayList<>();
    cmd.add("docker");
    cmd.add("exec");
    cmd.add(CONTROLLER_CONTAINER);  // hiclaw-controller
    cmd.add("curl");
    cmd.add("-s");
    cmd.add("-X");
    cmd.add(method);
    cmd.add("-H");
    cmd.add("Authorization: Bearer " + token);
    // ... 添加请求体和 URL
    ProcessBuilder pb = new ProcessBuilder(cmd);
    // 执行并解析 JSON 响应
}
```

### 4.2 MatrixClient - 发送消息

```java
public String sendTextMessage(String roomId, String text) throws Exception {
    String txnId = "txn" + System.currentTimeMillis();
    String url = homeserverUrl + "/_matrix/client/v3/rooms/" + 
                 URLEncoder.encode(roomId, "UTF-8") + 
                 "/send/m.room.message/" + txnId + 
                 "?access_token=" + accessToken;

    String body = String.format("{\"msgtype\":\"m.text\",\"body\":\"%s\"}", escapeJson(text));
    HttpPut put = new HttpPut(url);
    // 执行请求，返回 event_id
}
```

### 4.3 LoginServlet - 用户登录流程

```java
// 简化版：使用现有 Worker 和 Admin 凭证演示
if (user == null) {
    // 新用户：使用现有 Worker
    String workerName = DEFAULT_WORKER;  // worker-test-user
    String roomId = DEFAULT_ROOM;        // !wLMhJj3uHEYMfETrvx:...

    // 确保 Worker 正在运行
    JsonNode workerInfo = hiClawClient.getWorker(workerName);
    if (!"Running".equals(workerInfo.get("phase").asText())) {
        hiClawClient.wakeWorker(workerName);
    }

    // 使用 Admin Matrix 凭证
    user.matrixUserId = "@admin:matrix-local.hiclaw.io:18080";
    user.matrixAccessToken = "<admin_access_token>";
    database.createUser(user);
}
```

### 4.4 ChatServlet - 消息轮询

```java
// 短同步获取新消息
JsonNode syncResp = matrixClient.sync(5000);  // 5秒超时

// 解析 rooms.join.{roomId}.timeline.events
JsonNode timeline = syncResp.path("rooms").path("join")
    .path(roomId).path("timeline").path("events");
for (JsonNode event : timeline) {
    if ("m.room.message".equals(event.get("type").asText())) {
        // 提取 sender, content, timestamp
    }
}
```

---

## 五、测试验证

### 5.1 API 测试结果

```powershell
# 登录
POST /login userId=test-user-001
→ 302 Redirect to /chat

# 获取会话信息
GET /chat/info
→ {"userId":"test-user-001","workerName":"worker-test-user","roomId":"!wLMhJj3uHEYMfETrvx:..."}

# 发送消息
POST /chat message=Hello from MFClaw Demo!
→ {"success":true,"event_id":"$8dYm88IuY4gmRefsqAd8I0P5rBQy4hCHfz8ETMvJZP4"}

# 轮询消息
GET /chat/messages
→ {"messages":[{"eventId":"$vgicaQ28C0HbFKqtpAOumysw4N0qfM0gkE1bgHtm2wY","sender":"@admin:...","content":"Hello from Demo App!",...}]}
```

### 5.2 浏览器测试

1. 访问 http://localhost:9090/login.html
2. 输入任意用户 ID，点击"开始对话"
3. 进入聊天页面，显示 Worker 名称
4. 发送消息，等待 Worker 回复
5. 点击"退出对话"，Worker 休眠

---

## 六、总结与后续

### 6.1 已完成

- ✅ Maven 项目结构（Jetty 嵌入式 + H2 + Jackson + HttpClient）
- ✅ HiClaw Controller API 集成（通过 docker exec，支持 stdin 传递 JSON）
- ✅ Matrix Client API 集成（登录/发送/同步/邀请/加入）
- ✅ 用户登录注册流程
- ✅ 消息发送和轮询
- ✅ 静态 HTML 聊天界面
- ✅ **每用户独立 Worker**（登录时动态创建）
- ✅ **每用户独立 Matrix Room**（Worker 创建时自动生成）
- ✅ **每用户独立 Matrix 账号**（以用户身份发送消息）

### 6.2 已知限制

1. **Worker 创建耗时**：创建新 Worker 需 30-60 秒，用户需等待
2. **消息轮询**：使用短同步轮询，效率较低（可改用 WebSocket）
3. **错误处理**：Worker 创建失败时前端无友好提示

### 6.3 后续优化

1. ~~修复 HiClaw AI route 配置，支持动态创建 Worker~~ ✅ 已完成
2. ~~为每个用户创建独立 Matrix 账号（需要 Dendrite 支持）~~ ✅ 已完成
3. 改用 Matrix WebSocket (Sync API v3) 实现实时推送
4. 添加消息持久化和会话历史

---

## 七、2026-04-26 进展：每用户独立 Worker + Room

### 7.1 问题诊断：AI routes 500 错误

**现象**：调用 `POST /api/v1/workers` 创建 Worker 时，Controller 返回 500 错误：
```
provision worker: AI route authorization failed: list AI routes: HTTP 500
```

**根因**：Higress Console 的 `/data/configmaps/` 目录下有一个错误的文件：
- 文件名：`hiclaw-controller-api.yaml`
- 内容类型：`kind: Ingress`
- 问题：该目录用于加载 ConfigMap，Ingress 资源被错误放置导致 K8s API 尝试将 Ingress 转换为 ConfigMap 失败

**解决**：删除错误文件：
```bash
docker exec hiclaw-controller rm /data/configmaps/hiclaw-controller-api.yaml
```

### 7.2 问题诊断：Windows ProcessBuilder JSON 参数拆分

**现象**：Java ProcessBuilder 执行 `docker exec ... curl -d '{"name":"xxx"}'` 时，JSON body 被错误拆分，curl 报错：
```
invalid JSON: invalid character 'n' looking for beginning of object key string
```

**根因**：Windows 上 ProcessBuilder 传递包含双引号的参数时，双引号被 shell 拆分。

**解决**：使用 `curl -d @-` 从 stdin 读取 JSON body：
```java
cmd.add("-d");
cmd.add("@-");  // 从 stdin 读取
// ...
Process p = pb.start();
OutputStream stdin = p.getOutputStream();
stdin.write(jsonBody.getBytes("UTF-8"));
stdin.close();
```

### 7.3 完整流程实现

**LoginServlet 新流程**：
```
用户输入 userId
    ↓
检查数据库是否存在
    ↓ (不存在)
1. 创建 Worker: worker-{userId}
2. 等待 Worker 就绪 (phase=Running)
3. 获取 Worker 的 roomID
4. 注册 Matrix 账号: demo-{userId}
5. Admin 邀请用户加入 Room
6. 用户加入 Room
7. 保存到 H2 数据库
    ↓
跳转到 /chat
```

**关键代码变更**：
- `HiClawClient.executeRequest()`：改用 stdin 传递 JSON body
- `HiClawClient.waitForWorkerReady()`：轮询 `phase=Running`，最长 120 秒
- `LoginServlet`：完整实现独立 Worker + Room 流程
- `ChatServlet.handleGetInfo()`：返回 `matrixUserId` 用于前端消息过滤
- `chat.html`：用完整 Matrix User ID 过滤用户自己的消息

### 7.4 Higress Console 登录方式

**路径**：`/session/login` (POST)

**请求**：
```json
{"username": "admin", "password": "xxx"}
```

**响应**：Set-Cookie: `_hi_sess=...`

**测试命令**：
```bash
docker exec hiclaw-controller sh -c '
curl -s -c /tmp/cookie.txt -X POST -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin46c1cab2ae4a\"}" \
  http://127.0.0.1:8001/session/login

curl -s -b /tmp/cookie.txt http://127.0.0.1:8001/v1/ai/routes
'
```

### 7.5 待验证

- [x] 新用户登录 → Worker 创建成功 → Room ID 正确 ✅
- [x] 发送消息 → Worker 自动回复 ✅（已解决，见第八章）
- [x] 多用户隔离验证（不同用户进入不同 Room） ✅

---

## 八、2026-04-27 进展：解决 Worker autoReply 配置问题

### 8.1 已解决问题汇总

| 问题 | 根因 | 解决方案 |
|------|------|----------|
| Higress Console HTTP 500 | `/data/configmaps/hiclaw-controller-api.yaml` 是 Ingress 资源错放 ConfigMap 目录 | `docker exec hiclaw-controller rm /data/configmaps/hiclaw-controller-api.yaml` |
| Windows ProcessBuilder JSON 双引号拆分 | Windows shell 会拆分双引号 | 改用 `curl -d @-` 从 stdin 读取 JSON |
| Matrix 用户已存在时注册失败 | Dendrite 返回 `M_USER_IN_USE` | `register()` 检测错误码自动 fallback 到 `login()` |
| 用户重复登录主键冲突 | H2 DB 删除重建后旧 Worker 存在但 DB 无记录 | LoginServlet 先查 Worker API，已存在则跳过创建 |
| Matrix mention 格式不一致 | JSP 发送的 mention 缺少英文冒号 | `sendMentionMessage()` 用结构化 `m.mentions` + `formatted_body` + 冒号分隔符 |
| 模型名称错误 | 使用 `step-1-8k` 导致 Worker 启动失败 | 改为 `step-3.5-flash` |
| 前端消息过滤错误 | 用 `userId` 而非 `matrixUserId` 过滤 | `chat.html` 用完整 Matrix User ID 过滤 |
| Worker autoReply 配置不持久化 | Worker 容器内修改配置 + `kill -HUP` 被 MinIO 覆盖 | 通过 MinIO 修改配置源文件 + 重启 Worker 容器 |
| 老用户回来 Worker 不回复 | 只调用 `wakeWorker()` 未重新配置 autoReply | 老用户回来时也执行完整的 MinIO 配置修改 + 容器重启流程 |
| Worker 回复不显示在 JSP | （Poll 逻辑正常，实际是 autoReply 配置没生效导致 Worker 没回复） | 同上 |

### 8.2 Matrix 结构化 Mention 格式

**问题**：通过 JSP 发送的 `@worker-xxx 你好` 与 admin 直接发送的消息格式不一致，Worker 无法识别。

**解决方案**：使用 Matrix 结构化 mention（`m.mentions` 字段）：

```java
// MatrixClient.sendMentionMessage() 构建
Map<String, Object> bodyMap = new LinkedHashMap<>();
bodyMap.put("msgtype", "m.text");
bodyMap.put("body", "@worker-xxx:matrix-local.hiclaw.io:18080: 你好");  // 纯文本
bodyMap.put("format", "org.matrix.custom.html");
bodyMap.put("formatted_body", "<a href=\"https://matrix.to/#/@worker-xxx:...\">worker-xxx:...</a>: 你好");

Map<String, Object> mentionsMap = new LinkedHashMap<>();
mentionsMap.put("user_ids", Arrays.asList("@worker-xxx:matrix-local.hiclaw.io:18080"));
bodyMap.put("m.mentions", mentionsMap);  // 结构化 mention
```

**关键点**：
- `body` 纯文本格式：`@workerID:domain: 你好`（冒号+空格分隔）
- `formatted_body` HTML 格式：`<a href="matrix.to/#/...">...</a>: 你好`
- `m.mentions.user_ids` 数组：明确指定被 mention 的用户

### 8.3 OpenClaw mention 检测逻辑（源码级分析）

通过阅读 OpenClaw 源码 `copaw/src/matrix/channel.py`，发现 Worker 通过 **三层检测** 判断消息是否包含有效的 @mention：

#### 检测方法 `_was_mentioned()`（第 720-754 行）

| 层级 | 检测方式 | 示例 |
|------|----------|------|
| 1 | `m.mentions.user_ids` 数组包含 Worker 的 Matrix ID | `{"m.mentions": {"user_ids": ["@worker-xxx:..."]}}` |
| 2 | `formatted_body` 中的 `matrix.to` 链接 | `<a href="https://matrix.to/#/@worker-xxx:...">` |
| 3 | 纯文本 `body` 中的完整 Matrix ID | `@worker-xxx:matrix-local.hiclaw.io:18080` |

#### 触发条件 `_require_mention()`（第 709-717 行）

```python
def _require_mention(self, room_id: str) -> bool:
    room_cfg = self._cfg.groups.get(room_id) or self._cfg.groups.get("*")
    if room_cfg:
        if room_cfg.get("autoReply") is True:
            return False  # autoReply=true 时不需 mention
        if "requireMention" in room_cfg:
            return bool(room_cfg["requireMention"])
    return True  # 默认：群聊需要 mention
```

**结论**：只要 `requireMention: false` 或 `autoReply: true`，Worker 就会响应所有消息，不需要 @mention。

### 8.4 Worker autoReply 配置修改（最终方案）

**目标**：让 Worker 自动回复房间内所有消息，无需 @mention。

#### 配置存储层级关系

```
generator.go（硬编码默认值：allowlist + requireMention:true）
    ↓ Worker 创建时写入
MinIO（hiclaw-storage/agents/{name}/openclaw.json）
    ↓ Worker 启动时拉取
Worker 容器内（/root/hiclaw-fs/agents/{name}/openclaw.json）
    ↓ OpenClaw 启动时读取
OpenClaw 运行时配置
```

**关键发现**：
1. MinIO 是配置的真正来源，Worker 容器内的修改会被覆盖
2. Worker 容器内修改 + `kill -HUP` 不生效，因为 HUP 触发重新从 MinIO 拉取
3. 不发 HUP 也不会自动重载配置（OpenClaw 没有定时轮询机制）
4. **必须修改 MinIO + 重启 Worker 容器**才能生效
5. Controller 在 Worker 创建时用 `generator.go` 硬编码值生成默认配置

#### 最终解决方案：MinIO 修改 + 容器重启

```java
// HiClawClient.configureWorkerAutoReply() 完整流程：
// 1. mc alias set local http://127.0.0.1:9000 admin xxx
// 2. mc cp local/hiclaw-storage/agents/{name}/openclaw.json /tmp/oc.json
// 3. python3 修改 groupPolicy="open", requireMention=false
// 4. mc cp /tmp/oc.json local/hiclaw-storage/agents/{name}/openclaw.json
// 5. docker restart hiclaw-worker-{name}
// 6. 等待 Worker 就绪（phase=Running）
```

#### 手动验证命令

```bash
# 1. 在 Controller 容器内通过 MinIO 修改配置
docker exec hiclaw-controller sh -c '
mc alias set local http://127.0.0.1:9000 admin admin46c1cab2ae4a
mc cp local/hiclaw-storage/agents/worker-xxx/openclaw.json /tmp/oc.json
python3 -c "
import json
with open(\"/tmp/oc.json\") as f: d=json.load(f)
d[\"channels\"][\"matrix\"][\"groupPolicy\"]=\"open\"
d[\"channels\"][\"matrix\"][\"groups\"][\"*\"][\"requireMention\"]=False
with open(\"/tmp/oc.json\",\"w\") as f: json.dump(d,f,indent=2)
"
mc cp /tmp/oc.json local/hiclaw-storage/agents/worker-xxx/openclaw.json
'

# 2. 重启 Worker 容器
docker restart hiclaw-worker-worker-xxx

# 3. 验证配置
docker exec hiclaw-worker-worker-xxx python3 -c "
import json
with open('/root/hiclaw-fs/agents/worker-xxx/openclaw.json') as f:
    d=json.load(f)
print('groupPolicy:', d['channels']['matrix']['groupPolicy'])
print('requireMention:', d['channels']['matrix']['groups']['*']['requireMention'])
"
# 期望输出：groupPolicy: open  requireMention: False

# 4. 测试：发送不带 @mention 的消息，Worker 应该自动回复
```

### 8.5 关键凭证信息

| 项目 | 值 |
|------|-----|
| Matrix Domain | `matrix-local.hiclaw.io:18080` |
| Registration Token | `0e8e78fd879d1b13d21136ad0ff5849d91be8f9d91fa8572403f531cf955d61c` |
| Admin 用户 | `admin` / `admin46c1cab2ae4a` |
| Admin Matrix Token | `fguVlXbGrxXgEVuUIGbxroER6q2KVpCC` |
| Demo 用户密码格式 | `DemoPass@{userId}` |
| Demo Matrix 用户名格式 | `demo-{userId}` |
| Demo Worker 名称格式 | `worker-{userId}` |
| MinIO Endpoint | `http://127.0.0.1:9000`（Controller 容器内） |
| MinIO 凭证 | `admin` / `admin46c1cab2ae4a` |
| MinIO Bucket | `hiclaw-storage` |
| MinIO 配置路径 | `agents/{worker-name}/openclaw.json` |

### 8.6 代码变更记录

**新增方法**：
- `HiClawClient.configureWorkerAutoReply(String name)`：通过 MinIO 持久化修改配置 + 重启容器
- `MatrixClient.sendMentionMessage(roomId, text, mentionUserIds)`：发送结构化 mention
- `MatrixClient.register()`：增加 `M_USER_IN_USE` fallback 到 `login()`

**修改逻辑**：
- `LoginServlet`：新用户创建流程增加 MinIO 配置修改 + 容器重启；老用户回来时也执行完整配置流程
- `ChatServlet.doPost()`：使用 `sendMentionMessage()` 发送结构化 mention
- `chat.html`：前端自动加 `@workerName` 前缀，用 `matrixUserId` 过滤消息

### 8.7 手动验证指南

详见下方 **九、手动验证指南**。

### 8.8 后续优化

1. **彻底解决 autoReply**：修改 `generator.go` 重新编译 Controller，将默认值改为 `groupPolicy: "open"` + `requireMention: false`
2. **消息轮询优化**：改用 Matrix WebSocket 实现实时推送
3. **会话历史持久化**：保存消息到 H2 数据库
4. **错误处理完善**：Worker 创建失败时前端友好提示

---

## 九、手动验证指南

### 9.0 服务启动步骤

#### 步骤 1：启动 HiClaw（Docker 环境）

```powershell
# 确保 Docker Desktop 已启动（左下角图标变绿）
# HiClaw 容器应该已自动启动，检查状态：
docker ps

# 如果没有运行，手动启动：
docker start hiclaw-controller hiclaw-manager

# 验证 HiClaw 网关正常：
curl http://127.0.0.1:18080/_matrix/client/versions
# 应返回: {"versions":["v1.11","v1.12","v1.13"]}
```

#### 步骤 2：启动 Demo Web 应用

```powershell
# 进入项目目录
cd D:\Workspaces\MF999\MFClaw

# 方式 A：如果已编译过，直接启动
java -jar target\mfclaw-demo-1.0.0-SNAPSHOT.jar

# 方式 B：如果需要重新编译
# 先停止可能残留的 Java 进程
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
# 清理旧数据库（可选，如果想全新开始）
Remove-Item -Path "$HOME\mfclaw-demo.mv.db" -Force -ErrorAction SilentlyContinue
# 编译并启动
mvn package -q -DskipTests
java -jar target\mfclaw-demo-1.0.0-SNAPSHOT.jar

# 看到以下输出表示启动成功：
# Demo Application started on http://localhost:9090
# Open http://localhost:9090/login.jsp to begin
```

#### 步骤 3：访问 Web 页面

```
浏览器打开: http://localhost:9090/login.html
```

#### 常见启动问题

| 问题 | 解决方案 |
|------|----------|
| `java -jar` 报错找不到文件 | 先执行 `mvn package -q -DskipTests` 编译 |
| 端口 9090 已被占用 | `Get-Process -Name java | Stop-Process -Force` 停止旧进程 |
| HiClaw 容器未运行 | `docker start hiclaw-controller hiclaw-manager` |
| Docker Desktop 未启动 | 先启动 Docker Desktop，等待图标变绿 |
| H2 数据库锁定报错 | 删除 `$HOME\mfclaw-demo.mv.db` 后重启 |

#### 一键启动脚本（可选）

保存为 `D:\Workspaces\MF999\MFClaw\start.ps1`：

```powershell
# stop old java process
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

# check HiClaw
$containers = docker ps --format "{{.Names}}" 2>$null
if ($containers -notcontains "hiclaw-controller") {
    Write-Host "Starting HiClaw containers..."
    docker start hiclaw-controller hiclaw-manager
    Start-Sleep -Seconds 10
}

# start demo
Write-Host "Starting Demo application..."
Start-Process -FilePath "java" -ArgumentList "-jar", "target\mfclaw-demo-1.0.0-SNAPSHOT.jar" -NoNewWindow
Write-Host "Demo started on http://localhost:9090"
```

运行：
```powershell
cd D:\Workspaces\MF999\MFClaw
powershell -File start.ps1
```

### 9.1 环境检查

```powershell
# 1. 检查 Docker 容器运行状态
docker ps
# 应看到: hiclaw-controller, hiclaw-manager, 以及若干 hiclaw-worker-* 容器

# 2. 检查 Higress 网关
curl http://127.0.0.1:18080/_matrix/client/versions
# 应返回: {"versions":["v1.11","v1.12","v1.13"]}

# 3. 检查 Demo 应用
curl http://localhost:9090/login.html -o NUL -w "%{http_code}"
# 应返回: 200
```

### 9.2 新用户完整流程验证

```
1. 浏览器打开 http://localhost:9090/login.html
2. 输入一个新用户 ID（如 testuser99）
3. 等待 30-60 秒（创建 Worker + 配置 autoReply + 重启容器）
4. 进入聊天页面，发送消息
5. Worker 应在 10-20 秒内自动回复
```

**预期行为**：
- 新用户第一次登录：创建 Worker → 配置 MinIO → 重启容器 → 进入聊天
- 发送消息（前端自动加 @mention）→ Worker 回复
- Worker 回复显示在 JSP 页面上

### 9.3 老用户回来验证

```
1. 在 JSP 点击"退出对话"（Worker 休眠）
2. 重新输入同一个用户 ID 登录
3. 等待自动配置（MinIO 修改 + 容器重启）
4. 发送消息
5. Worker 应自动回复
```

**预期行为**：
- 老用户回来时唤醒 Worker + 重新配置 autoReply + 重启容器
- 发送消息 → Worker 正常回复

### 9.4 Element Web 对比验证

```
1. 浏览器打开 http://127.0.0.1:18088
2. 用 admin 账号登录（密码: admin46c1cab2ae4a）
3. 进入任意 Worker Room
4. 发送消息，Worker 应回复
```

### 9.5 Worker autoReply 配置验证

```powershell
# 检查某个 Worker 的配置是否正确
docker exec hiclaw-worker-worker-xxx python3 -c "
import json
with open('/root/hiclaw-fs/agents/worker-xxx/openclaw.json') as f:
    d=json.load(f)
cm = d.get('channels',{}).get('matrix',{})
print('groupPolicy:', cm.get('groupPolicy'))
g = cm.get('groups',{}).get('*',{})
print('requireMention:', g.get('requireMention'))
"
# 期望输出: groupPolicy: open  requireMention: False
```

### 9.6 手动修复 Worker autoReply

如果某个 Worker 不自动回复，手动执行：

```powershell
# 替换 worker-xxx 为实际的 Worker 名称
docker exec hiclaw-controller sh -c '
mc alias set local http://127.0.0.1:9000 admin admin46c1cab2ae4a
mc cp local/hiclaw-storage/agents/worker-xxx/openclaw.json /tmp/oc.json
python3 -c "
import json
with open(\"/tmp/oc.json\") as f: d=json.load(f)
d[\"channels\"][\"matrix\"][\"groupPolicy\"]=\"open\"
d[\"channels\"][\"matrix\"][\"groups\"][\"*\"][\"requireMention\"]=False
with open(\"/tmp/oc.json\",\"w\") as f: json.dump(d,f,indent=2)
print(\"done\")
"
mc cp /tmp/oc.json local/hiclaw-storage/agents/worker-xxx/openclaw.json
'
docker restart hiclaw-worker-worker-xxx
```

### 9.7 常见问题排查

| 现象 | 排查步骤 |
|------|----------|
| Worker 不回复 | 1. 检查配置：`groupPolicy: open` + `requireMention: False`<br>2. 检查 Worker phase 是否为 `Running`<br>3. 手动执行 9.6 修复 |
| Worker 创建超时 | 1. `docker ps` 检查容器状态<br>2. `docker logs hiclaw-controller` 查看错误 |
| 消息发送成功但无回复显示 | 1. 在 Element Web 中查看同一 Room 是否有 Worker 回复<br>2. 检查浏览器 Console 是否有 JS 错误 |
| 老用户登录报错 | 1. 删除 H2 DB: `Remove-Item "$HOME\mfclaw-demo.mv.db"`<br>2. 重启 Demo 应用 |
| Demo 启动失败 | 1. `Get-Process java` 确认没有残留进程<br>2. `mvn package -q -DskipTests` 重新编译 |

### 9.8 清理测试 Worker

```powershell
# 查看所有 Worker
docker exec hiclaw-controller curl -s -H "Authorization: Bearer $(docker exec hiclaw-controller cat /var/run/hiclaw/cli-token)" http://127.0.0.1:8090/api/v1/workers

# 删除指定 Worker
docker exec hiclaw-controller curl -s -X DELETE -H "Authorization: Bearer $(docker exec hiclaw-controller cat /var/run/hiclaw/cli-token)" http://127.0.0.1:8090/api/v1/workers/worker-xxx
```
