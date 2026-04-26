# HiClaw 综合调研报告

> **调研日期**: 2026-04-26  
> **版本范围**: v1.1.0  
> **目的**: 为基于 HiClaw 的应用级研发提供深度技术参考

---

## 一、项目定位与概览

### 1.1 是什么

HiClaw 是一个**开源的协作式多智能体运行平台（Multi-Agent OS）**，由阿里云 Higress 社区发起，现归属于 **agentscope-ai** 组织。

核心定位：**HiClaw 不实现 Agent 逻辑本身，而是编排和管理多个 Agent 容器。** 它是 OpenClaw 的"团队版"。

### 1.2 GitHub 基本信息

| 项 | 值 |
|---|---|
| 仓库 | `https://github.com/alibaba/hiclaw`（原）→ `https://github.com/agentscope-ai/hiclaw` |
| ⭐ Stars | 4,268+ |
| 许可证 | Apache 2.0 |
| 语言 | Go（控制器）、Shell（Manager/Worker 脚本）、Python（CoPaw/Hermes Worker）、Node.js（OpenClaw Worker） |
| 首次开源 | 2026-03-04 |
| 最新版本 | v1.1.0（2026-04-24） |

### 1.3 官网

- **全球**: https://hiclaw.io/
- **中文**: https://hiclaw.org/
- **Discord**: https://discord.gg/NVjNA4BAVw
- **DeepWiki**: https://deepwiki.com/higress-group/hiclaw

### 1.4 核心价值主张

| 问题 | HiClaw 方案 |
|------|------------|
| 多 Agent 如何安全协作？ | Higress AI Gateway 代理凭证，Worker 仅持有 Consumer Token |
| 人类如何监督 Agent？ | 基于 Matrix 协议，所有通信在 Room 中透明可见 |
| 如何管理 Agent 生命周期？ | K8s 原生控制面 + 声明式 YAML API |
| 如何快速上手？ | `curl \| bash` 一条命令，内置 IM 客户端零配置接入 |

---

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                 hiclaw-controller                      │
│           (Kubernetes 原生控制平面)                        │
│  ┌─────────┐ ┌────────┐ ┌───────┐ ┌────────────┐        │
│  │ Higress │ │Tuwunel │ │ MinIO │ │Element Web │        │
│  │AI Gateway│ │(Matrix)│ │(S3)   │ │ (IM 客户端) │        │
│  └────┬────┘ └───┬────┘ └───┬───┘ └─────┬──────┘        │
│       │          │          │           │                │
└───────┼──────────┼──────────┼───────────┼────────────────┘
        │          │          │           │
        ▼          ▼          ▼           ▼
┌───────────────────────────────────────────┐
│          hiclaw-manager-agent                │
│     Manager (OpenClaw / CoPaw / Hermes)     │
│     • 任务接收与分解                          │
│     • Worker 生命周期管理                    │
│     • 心跳健康监控                          │
│     • 凭证与权限管理                        │
└──────────────────┬───────────────────────┘
                   │
     ┌─────────────┼─────────────────────┐
     │             │                     │
     ▼             ▼                     ▼
 Worker Alice   Worker Bob          Worker Charlie
 (OpenClaw)     (CoPaw)           (Hermes)
 (Node.js)      (Python)           (Python)
```

### 2.2 组件详解

| 组件 | 技术 | 端口 | 职责 |
|------|------|------|------|
| **hiclaw-controller** | Go + controller-runtime | :8090 | K8s 原生控制平面，reconcile Worker/Team/Manager CR |
| **Higress AI Gateway** | CNCF Sandbox，Envoy 内核 | :8080/:8001 | LLM 代理、MCP Server 托管、Consumer 认证 |
| **Tuwunel** | Matrix 服务器（conduwuit fork, Rust） | :6167 | IM 通信：Agent 间 + Human ↔ Agent |
| **Element Web** | TypeScript (Matrix 客户端) | :8088 | 浏览器 IM 界面 |
| **MinIO** | S3 兼容对象存储 (Go) | :9000/:9001 | 集中式文件存储，Agent 配置和共享状态 |
| **OpenClaw** | Node.js Agent 运行时 | — | 通用 Agent，擅长任务编排和工具调用 |
| **CoPaw** | Python Agent 运行时 | — | 轻量级，内存占用低 80% |
| **Hermes** | Python Agent 运行时 (hermes-agent) | — | 自主编程 Agent，终端沙箱 + 持久记忆 |
| **mcporter** | Go CLI | — | Worker 调用 MCP Server 工具 |

### 2.3 三层组织结构

```
Admin (人类管理员)
  │
  ├── Manager (AI 协调者)
  │     ├── Team Leader A (特殊 Worker，队内调度)
  │     │     ├── Worker A1
  │     │     └── Worker A2
  │     ├── Team Leader B
  │     │     └── Worker B1
  │     └── Worker C (独立 Worker，不属于任何 Team)
  │
  └── Human 用户 (真实人物，权限分级)
        ├── Level 1: 管理员等价，可对话所有角色
        ├── Level 2: 可对话指定 Team 的 Leader + Workers + 独立 Workers
        └── Level 3: 只能对话指定的 Workers
```

### 2.4 通信模型

所有通信在 Matrix Room 中进行，支持 Human-in-the-Loop：

```
Room: "Worker: Alice"
├── 成员: @admin, @manager, @alice
├── Manager 分配任务 → 所有人可见
├── Alice 汇报进度 → 所有人可见
├── 人类随时可以介入 → 所有人可见
└── Manager 和 Worker 之间没有隐藏通信
```

**Team 房间拓扑**：
```
Leader Room:  Manager + Global Admin + Leader    ← Manager 只跟 Leader 对话
Team Room:    Leader + Team Admin + W1 + W2 + …   ← Leader ↔ Workers 协作空间 (Manager 不在此)
Worker Room:  Leader + Team Admin + Worker           ← Leader ↔ 成员私聊
Leader DM:    Team Admin ↔ Leader                    ← 管理通道
```

---

## 三、Kubernetes 原生控制面 (CRD API)

### 3.1 四大资源类型

所有资源共享 `apiVersion: hiclaw.io/v1beta1`。

#### Worker — 执行单元

```yaml
apiVersion: hiclaw.io/v1beta1
kind: Worker
metadata:
  name: alice
spec:
  model: claude-sonnet-4-6
  runtime: openclaw              # openclaw | copaw | hermes
  skills: [github-operations]
  mcpServers: [github]
  soul: |
    You are a frontend-focused engineer...
  identity: |
    - Name: Alice
    - Specialization: React, TypeScript
  package: file://./alice-pkg.zip  # file://, http(s)://, nacos://
  expose:                        # 通过 Gateway 暴露端口
    - port: 3000
      protocol: http
  channelPolicy:
    groupAllowExtra: ["@human:domain"]
  state: Running                 # Running | Sleeping | Stopped
```

Worker 底层实体 = Docker 容器 + Matrix 账号 + MinIO 空间 + Gateway Consumer Token。

#### Team — 协作单元

```yaml
apiVersion: hiclaw.io/v1beta1
kind: Team
metadata:
  name: frontend-team
spec:
  description: "Frontend development team"
  peerMentions: true
  admin:                         # 可选 Team Admin
    name: pm-zhang
  leader:
    name: frontend-lead
    model: claude-sonnet-4-6
    heartbeat:
      enabled: true
      every: 30m
    workerIdleTimeout: 12h
  workers:
    - name: alice
      model: claude-sonnet-4-6
      skills: [github-operations]
    - name: bob
      model: qwen3.5-plus
      runtime: copaw
```

#### Human — 真实用户

```yaml
apiVersion: hiclaw.io/v1beta1
kind: Human
metadata:
  name: john
spec:
  displayName: John Doe
  email: john@example.com
  permissionLevel: 2          # 1=Admin | 2=Team-scoped | 3=Worker-only
  accessibleTeams: [frontend-team]
  accessibleWorkers: []
```

#### Manager — 协调者

```yaml
apiVersion: hiclaw.io/v1beta1
kind: Manager
metadata:
  name: default
spec:
  model: claude-sonnet-4-6
  runtime: openclaw
  skills: [worker-management, team-management]
  config:
    heartbeatInterval: 15m
    workerIdleTimeout: 720m
    notifyChannel: admin-dm
```

### 3.2 Controller Reconcile 循环

```
YAML 声明
    ↓ hiclaw apply (CLI/REST API)
MinIO hiclaw-config/{kind}/{name}.yaml
    ↓ mc mirror (10s 间隔)
fsnotify 检测文件变更
    ↓ kine (SQLite) / K8s etcd
controller-runtime Informer
    ↓ Reconciler
┌─────────────────────────────────────────────┐
│ Provisioner                                 │
│ - Matrix 注册 & 房间创建                    │
│ - MinIO 用户 & Bucket 创建                 │
│ - Higress Consumer & Route 配置            │
│ - K8s ServiceAccount                      │
├─────────────────────────────────────────────┤
│ Deployer                                    │
│ - 拉取 package (file/http/nacos)           │
│ - 生成 openclaw.json                       │
│ - 推送 SOUL.md / AGENTS.md / skills        │
│ - 启动容器 / 创建 Pod                      │
├─────────────────────────────────────────────┤
│ Worker Backend 抽象                          │
│ - Docker (embedded 模式)                    │
│ - Kubernetes (incluster 模式)                │
└─────────────────────────────────────────────┘
```

### 3.3 部署模式

| 模式 | 状态存储 | Worker 运行方式 | 适用场景 |
|------|---------|---------------|---------|
| **Embedded** | kine + SQLite | Docker 容器 | 开发 / 小团队 |
| **Incluster** | K8s etcd | Pod | 企业 / 云 |

---

## 四、三种 Worker Runtime 详解

### 4.1 OpenClaw (Node.js)

- **定位**: 通用 Agent 运行时，适合任务编排和工具调用
- **内存**: ~500MB
- **启动**: 启动后从 MinIO 拉取 `openclaw.json`，直接加载
- **技能**: 社区 skills.sh 80,000+ 个技能
- **配置**: 原生读取 `openclaw.json`
- **镜像**: `hiclaw/hiclaw-worker`

### 4.2 CoPaw (Python)

- **定位**: 轻量级运行时，适合浏览器自动化和快速任务
- **内存**: ~100MB（比 OpenClaw 低 80%）
- **启动流程**:
  1. 从 MinIO 拉取所有配置
  2. `bridge.py` 将 `openclaw.json` 转换为 CoPaw 原生格式 (`config.json` + `agent.json` + `providers.json`)
  3. 安装 MatrixChannel
  4. 启动 CoPaw AgentRunner + ChannelManager
- **关键代码**: `copaw/src/copaw_worker/worker.py` → `Worker` 类
- **镜像**: `hiclaw/copaw-worker`

### 4.3 Hermes (Python)

- **定位**: 自主编程 Agent，具备终端沙箱、自我进化 Skill 和持久化记忆
- **来源**: 基于 [hermes-agent](https://github.com/NousResearch/hermes-agent)
- **特点**: 终端沙箱执行代码，自改进 Skill 系统，持久化记忆
- **配置**: `bridge.py` 设置 `OPENAI_BASE_URL` + `OPENAI_API_KEY` 环境变量
- **镜像**: `hiclaw/hermes-worker`

### 4.4 运行时共存

三种 Worker 可在**同一个 IM Room 中协作**。推荐模式：
- 确定性 Agent（OpenClaw/QwenPaw）做 Leader → 任务分解和调度
- Hermes Worker 做执行者 → 自主编程任务

```bash
# 原地切换 Worker 运行时
hiclaw update worker --name alice --runtime hermes
```

---

## 五、技能系统 (Skills)

### 5.1 设计理念

HiClaw 采用 **Skills as Documentation** 模式——每个 Skill 是一个自包含的 SKILL.md 文件 + 可选的 scripts/ 和 references/ 目录。Agent 运行时根据描述匹配加载。

### 5.2 Manager 技能清单（16 个）

| 技能 | 用途 |
|------|------|
| `worker-management` | 创建/销毁/启停 Worker、切换运行时、管理技能 |
| `team-management` | 创建/管理 Team（Leader + Workers） |
| `task-management` | 任务分配、完成确认、定期任务、状态跟踪 |
| `task-coordination` | 多 Worker 任务协调、依赖管理 |
| `project-management` | 多 Worker 项目管理、Project Room 创建 |
| `human-management` | 人类用户管理、权限分级 |
| `channel-management` | 多渠道通信（Discord/飞书/Telegram） |
| `matrix-server-management` | Matrix 服务器管理、房间创建 |
| `mcp-server-management` | MCP Server 配置、Worker 授权 |
| `file-sync-management` | MinIO 文件同步管理 |
| `model-switch` | 运行时切换 LLM 模型 |
| `worker-model-switch` | 远程修改 Worker 模型 |
| `git-delegation-management` | Git 操作委派 |
| `hiclaw-find-worker` | Nacos 市场导入 / Worker 发现 |
| `service-publishing` | Worker 服务暴露 |
| `mcporter` | MCP CLI 使用参考 |

### 5.3 Worker 技能（推送式）

| 技能 | 用途 |
|------|------|
| `github-operations` | PR/Issue 管理（创建、审查、合并、评论） |
| `git-delegation` | Git 操作（clone、commit、push、branch） |

### 5.4 Skill 文件结构

```
skills/<skill-name>/
├── SKILL.md           # 技能描述（YAML front matter + 文档正文）
├── references/        # 详细操作文档
│   ├── create-worker.md
│   ├── lifecycle.md
│   └── skills-management.md
└── scripts/           # Agent 可执行的 Shell 脚本
    ├── create-worker.sh
    ├── lifecycle-worker.sh
    └── push-worker-skills.sh
```

**SKILL.md 格式**：
```markdown
---
name: worker-management
description: Use when admin requests creating a Worker, starting/stopping, or managing skills.
---

# Worker Management

## Quick Create (1 command)
...
```

---

## 六、安全模型

### 6.1 凭证隔离

```
Worker (仅持有 Consumer Token)
    → Higress AI Gateway (持有真实 API Key、GitHub PAT)
        ├── key-auth WASM 验证 Token
        ├── 检查 allowedConsumers
        ├── 注入真实凭证
        └── 代理上游
            ├── LLM APIs (OpenAI, Qwen, Claude, DeepSeek...)
            ├── MCP Servers (GitHub, Jira...)
            └── 其他服务
```

### 6.2 细粒度控制

| 维度 | 机制 | 示例 |
|------|------|------|
| Per-Worker LLM | AI Route allowedConsumers | Worker A: GPT-4; Worker B: Qwen3.5 |
| Per-Worker MCP | MCP allowedConsumers | Worker A: GitHub MCP; Worker B: none |
| 运行时变更 | 编辑 allowedConsumers | 秒级 Worker 访问权限 |
| 快速撤销 | 从列表移除 | WASM 热加载 (~秒级) |

类比 K8s: Consumer token ≈ ServiceAccount token; `allowedConsumers` ≈ RBAC 策略。

### 6.3 ChannelPolicy 通信权限

```yaml
channelPolicy:
  groupAllowExtra: ["@human:domain"]   # 额外允许群组 @mention
  groupDenyExtra: ["@blocked:domain"]   # 拒绝
  dmAllowExtra: ["@friend:domain"]       # 额外允许 DM
  dmDenyExtra: ["@spammer:domain"]      # 拒绝 DM
```

---

## 七、文件系统布局

### 7.1 Manager 工作空间（宿主机）

```
~/hiclaw-manager/            # 宿主机路径 (bind mount 到容器)
├── SOUL.md                  # Manager 身份
├── AGENTS.md                # 工作空间指南
├── HEARTBEAT.md             # 心跳检查清单
├── openclaw.json            # 生成配置（每次启动重新生成）
├── skills/                  # Manager 自身技能 (16 个)
├── worker-skills/           # Worker 技能定义（推送给 Worker）
├── workers-registry.json    # Worker 技能分配和房间 ID
├── state.json               # 活跃任务状态
├── worker-lifecycle.json    # Worker 容器状态和空闲跟踪
├── primary-channel.json     # 管理员首选通知渠道
├── trusted-contacts.json    # 受信任的非管理员联系人
└── memory/                  # Manager 记忆文件
```

### 7.2 MinIO 对象存储（共享）

```
MinIO bucket: hiclaw-storage/
├── agents/
│   ├── alice/               # Worker Alice 配置
│   │   ├── SOUL.md
│   │   ├── openclaw.json
│   │   ├── skills/
│   │   ├── credentials/      # Matrix 密码（MinIO cat 直读，不落盘）
│   │   └── config/mcporter.json
│   └── bob/
├── shared/
│   ├── tasks/
│   │   └── task-{id}/
│   │       ├── meta.json    # 任务元数据
│   │       ├── spec.md      # 完整任务规格
│   │       ├── base/        # 参考文件
│   │       ├── result.md    # 任务结果
│   │       └── progress/    # 进度日志
│   └── knowledge/           # 共享参考资料
└── teams/                   # Team 共享空间
    └── {team-name}/shared/
```

---

## 八、Agent 行为定义

### 8.1 声明式 Agent 行为

HiClaw Agent 的行为不是通过代码定义的，而是通过**Markdown 文档**（系统提示词）：

| 文件 | 用途 | 加载时机 |
|------|------|---------|
| `SOUL.md` | 身份、性格、安全规则 | 每次会话首先读取 |
| `AGENTS.md` | 工作空间指南、通信协议、Gotchas | 每次会话首先读取 |
| `HEARTBEAT.md` | 心跳检查清单 | 心跳触发时读取 |
| `SKILL.md` | 技能文档 | 按需匹配加载 |
| `TOOLS.md` | 技能路由速查 | 按需加载 |

### 8.2 Manager 关键行为规则（来自 SOUL.md + AGENTS.md）

- **委派优先**: Manager 的默认操作是委派给 Worker，而非自己动手
- **团队优先**: 复杂多技能任务优先委派给 Team Leader
- **YOLO 模式**: `~/yolo-mode` 文件存在时，Manager 全自主决策不请求确认
- **记忆持久化**: 每次重大事件写入 `memory/YYYY-MM-DD.md`
- **NO_REPLY 协议**: 没有话说时发送 `NO_REPLY`（独立响应，不是后缀）
- **心跳productive**: 心跳时扫描任务状态、评估容量、检查人类通知

### 8.3 Worker 关键行为

- **无状态设计**: 所有状态在 MinIO，容器可随时销毁重建
- **文件同步**: 启动时从 MinIO 全量 mirror，运行时周期同步
- **技能热加载**: `sync_loop` 检测 MinIO 变更，自动重新同步 skills
- **结果上报**: 完成任务后写 `result.md` 到 MinIO，@mention Manager

---

## 九、部署指南

### 9.1 Docker Desktop（单机）

```bash
# 一键安装
bash <(curl -sSL https://higress.ai/hiclaw/install.sh)

# 指定版本
HICLAW_VERSION=v1.1.0 bash <(curl -sSL https://higress.ai/hiclaw/install.sh)
```

**资源需求**: 最低 2C4GB，推荐 4C8GB（多 Worker 场景）。

### 9.2 Kubernetes（Helm）

```bash
helm repo add higress.io https://higress.io/helm-charts
helm repo update

helm install hiclaw higress.io/hiclaw \
  -n hiclaw-system --create-namespace \
  --set credentials.llmApiKey=<your-key> \
  --set credentials.adminPassword=<your-password> \
  --set gateway.publicURL=http://localhost:18080
```

### 9.3 多区域镜像仓库

| 区域 | Registry |
|------|---------|
| 中国（默认） | `higress-registry.cn-hangzhou.cr.aliyuncs.com/higress` |
| 北美 | `higress-registry.us-west-1.cr.aliyuncs.com/higress` |
| 东南亚 | `higress-registry.ap-southeast-7.cr.aliyuncs.com/higress` |

### 9.4 CLI 管理命令

```bash
# 查看资源
hiclaw get workers -o json
hiclaw get teams
hiclaw get humans

# 声明式应用
hiclaw apply -f worker.yaml
hiclaw apply -f company-setup.yaml

# 删除资源
hiclaw delete worker alice
```

### 9.5 Helm Chart 关键配置

```yaml
# helm/hiclaw/values.yaml 关键项
credentials:
  llmApiKey: ""
  llmProvider: "openai-compat"    # openai-compat | qwen
  defaultModel: "gpt-5.4"

controller:
  workerBackend: "k8s"           # k8s | sae
  resourcePrefix: "hiclaw-"

manager:
  enabled: true
  model: ""
  runtime: "openclaw"            # openclaw | copaw | hermes

worker:
  defaultRuntime: "openclaw"

gateway:
  publicURL: ""
  provider: higress               # higress | ai-gateway

storage:
  provider: minio                 # minio | oss
```

---

## 十、构建与开发

### 10.1 构建体系

```bash
make build                 # 构建所有镜像
make build-manager          # 仅 Manager
make build-worker           # 仅 OpenClaw Worker
make build-copaw-worker     # 仅 CoPaw Worker
make build-hermes-worker    # 仅 Hermes Worker
make build-hiclaw-controller # 仅 Controller
make build-embedded        # 嵌入式 all-in-one

make test                  # 完整集成测试
make test-quick             # 快速冒烟测试 (test-01)
make test TEST_FILTER="01 02"

make install               # 构建 + 安装 Manager
make replay TASK="..."      # 向 Manager 发送任务
```

### 10.2 镜像依赖链

```
openclaw-base → manager / worker
copaw (独立)
hermes (独立)
hiclaw-controller → embedded
```

### 10.3 代理支持

```bash
# China mirror acceleration (no proxy)
make build DOCKER_BUILD_ARGS="--build-arg APT_MIRROR=mirrors.aliyun.com \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com/ \
  --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/"

# HTTP proxy
make build DOCKER_BUILD_ARGS="--build-arg HTTP_PROXY=http://host.docker.internal:1087 \
  --build-arg HTTPS_PROXY=http://host.docker.internal:1087"
```

### 10.4 热更新开发（不重建镜像）

| 变更类型 | 脚本 | 效果 |
|---------|------|------|
| `copaw/src/copaw_worker/*.py` | `dev-sync-copaw.sh` | 同步 Python 源码到运行中容器 |
| `manager/agent/**` | `dev-sync-agent.sh` | 同步 Agent 内容到 Manager |

---

## 十一、测试体系

### 11.1 集成测试

- 位置: `tests/test-01-manager-boot.sh` ~ `test-21-*.sh`
- 共 20+ 个测试用例
- 覆盖: Manager 启动、Worker 创建、任务分配、GitHub 集成、Team 管理、Human 管理、MCP Server 等

### 11.2 运行测试

```bash
# 完整测试
HICLAW_LLM_API_KEY=xxx make test

# 指定测试
make test TEST_FILTER="01 02 03"

# 跳过构建
make test SKIP_BUILD=1

# 对已安装 Manager 测试
make test-installed
```

---

## 十二、版本演进

| 版本 | 日期 | 里程碑 |
|------|------|--------|
| v1.0.0 | 2026-03-04 | 开源发布 |
| v1.0.4 | 2026-03-10 | CoPaw Worker 集成，内存降低 80% |
| v1.0.6 | 2026-03-14 | 企业级 MCP Server 管理，凭证零暴露 |
| v1.0.9 | 2026-04-03 | K8s 声明式资源管理、Worker 模板市场、Manager CoPaw 运行时、Nacos Skills 注册中心 |
| **v1.1.0** | **2026-04-24** | **K8s 原生控制面、Hermes 自主编程运行时、镜像瘦身 1.7GB、hiclaw CLI 替代 Shell 脚本** |

---

## 十三、AgentScope 生态定位

### 13.1 生态全景

```
AgentScope (核心 Agent 框架, ⭐ 24,000+)
├── AgentScope-Runtime (生产部署框架)
├── AgentScope-Studio (可视化观测工具)
├── OpenJudge (Agent 评估框架)
├── ReMe (Agent 记忆管理)
├── Trinity-RFT (LLM 微调框架)
├── QwenPaw/CoPaw (轻量运行时)
└── HiClaw (企业协作编排层)  ← 本项目
    ├── OpenClaw Worker (Node.js)
    ├── QwenPaw Worker (Python)
    └── Hermes Worker (自主编码)
```

### 13.2 分层关系

```
┌──────────────────────────────────────────────┐
│  应用/业务层                                      │
├──────────────────────────────────────────────┤
│  编排/协作层   │  HiClaw (容器化多Agent协作)        │  ← HiClaw 在这一层
│               │  CrewAI (角色定义式多Agent)         │
│               │  AutoGen (对话式多Agent)            │
├──────────────────────────────────────────────┤
│  Agent 框架层  │  AgentScope (Python, 生产就绪)     │  ← AgentScope 在这一层
│               │  LangChain/LangGraph (最大生态)    │
│               │  OpenClaw (Node.js, 运行时)         │
├──────────────────────────────────────────────┤
│  模型层       │  OpenAI / Anthropic / 通义千问 / ... │
└──────────────────────────────────────────────┘
```

**关键结论**: HiClaw 不与 CrewAI/AutoGen/LangGraph 直接竞争。它们是 Agent **编程框架**，HiClaw 是 Agent **运行时编排平台**。可以互补使用。

---

## 十四、扩展指南

### 14.1 添加新 Skill

```bash
# 1. 创建 Skill 目录
mkdir -p manager/agent/skills/my-skill/{references,scripts}

# 2. 编写 SKILL.md (YAML front matter 是必须的)
# 3. 添加 scripts/ 可选脚本
# 4. Manager 自动发现 (~300ms)
```

### 14.2 添加新 Worker Runtime

参考 `copaw/src/copaw_worker/bridge.py` 和 `hermes/src/hermes_worker/bridge.py`：

1. 实现 `bridge.py`: 将 `openclaw.json` 转换为新运行时原生配置
2. 实现 `sync.py`: MinIO ↔ 本地文件同步
3. 实现 `worker.py`: `Worker.start()` 编排启动流程
4. 在 `hiclaw-controller` 注册新运行时字符串

### 14.3 集成新 LLM Provider

1. `manager/configs/known-models.json` 添加模型预设
2. `hiclaw-controller/internal/agentconfig/generator.go` 的 `defaultModelSpec()` 添加默认配置
3. 通过 Higress 配置 OpenAI 兼容的 API 端点

### 14.4 添加新 IM Channel

参考 `copaw/src/matrix/channel.py`：

1. 继承 CoPaw 的 `BaseChannel`（`copaw.app.channels.base`）
2. 实现消息收发、@mention 检测
3. 在 `config.json` 的 `channels` 键注册

---

## 十五、源码入口索引

| 模块 | 关键文件 | 说明 |
|------|----------|------|
| **CRD 类型** | `hiclaw-controller/api/v1beta1/types.go` | Worker/Team/Human/Manager Go 结构体 |
| **Worker Reconciler** | `hiclaw-controller/internal/controller/worker_controller.go` | Worker K8s 调谐循环 |
| **Team Reconciler** | `hiclaw-controller/internal/controller/team_controller.go` | Team K8s 调谐循环 |
| **Human Reconciler** | `hiclaw-controller/internal/controller/human_controller.go` | Human K8s 调谐循环 |
| **Manager Reconciler** | `hiclaw-controller/internal/controller/manager_controller.go` | Manager K8s 调谐循环 |
| **Config 生成** | `hiclaw-controller/internal/agentconfig/generator.go` | 从 CR spec 生成 openclaw.json |
| **CoPaw Worker** | `copaw/src/copaw_worker/worker.py` | CoPaw Worker 启动编排 |
| **CoPaw Bridge** | `copaw/src/copaw_worker/bridge.py` | openclaw.json → CoPaw 配置转换 |
| **CoPaw Sync** | `copaw/src/copaw_worker/sync.py` | MinIO 文件同步 |
| **Matrix Channel** | `copaw/src/matrix/channel.py` | Matrix IM 通道实现 (~1300 行) |
| **Hermes Worker** | `hermes/src/hermes_worker/worker.py` | Hermes Worker 启动编排 |
| **Hermes Bridge** | `hermes/src/hermes_worker/bridge.py` | openclaw.json → Hermes 配置转换 |
| **Manager Soul** | `manager/agent/SOUL.md` | Manager 身份和安全规则 |
| **Manager 指南** | `manager/agent/AGENTS.md` | Manager 完整行为指南 |
| **Worker 技能** | `manager/agent/worker-skills/github-operations/SKILL.md` | Worker GitHub 技能参考 |

---

## 十六、关键技术注意事项

### 16.1 已验证的技术细节

| 项目 | 值 |
|------|-----|
| Tuwunel 环境变量前缀 | `CONDUWUIT_`（非 `TUWUNEL_`） |
| Higress Console 认证 | Session Cookie（非 Basic Auth） |
| MCP Server 创建 | `PUT`（非 `POST`） |
| Auth 激活延迟 | ~40 秒 |
| Skills 自动加载路径 | `workspace/skills/<name>/SKILL.md` |
| `openclaw.json` 必须字段 | `gateway.mode=local`, `gateway.auth.token` |

### 16.2 常见陷阱

1. **Worker 名称**: 必须小写且 > 3 字符（Tuwunel 存储用户名小写）
2. **`--remote` 含义**: "remote from Manager" = 从 Admin 角度看是"本地"
3. **5 分钟 re-bridge**: Worker 每 5 分钟可能触发 config re-bridge，属已知问题
4. **Matrix Channel 日志静默**: `copaw/src/matrix/channel.py` 使用 `qwenpaw.channels.matrix` logger namespace，默认不输出到 stdout
5. **Session 格式不同**: OpenClaw 用 `.jsonl`，CoPaw/Hermes 用 `.json`，不兼容
6. **SOUL.md/AGENTS.md 不在 pull allowlist**: Controller 更新 MinIO 后不会传播到运行中 Worker，需重启

---

## 附录 A：环境变量速查

### Manager 容器

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `HICLAW_LLM_API_KEY` | (必填) | LLM API Key |
| `HICLAW_LLM_PROVIDER` | `qwen` | LLM 提供商 |
| `HICLAW_DEFAULT_MODEL` | `qwen3.5-plus` | 默认模型 |
| `HICLAW_ADMIN_USER` | `admin` | Matrix 管理员用户名 |
| `HICLAW_MATRIX_DOMAIN` | `matrix-local.hiclaw.io:18080` | Matrix 服务器域名 |
| `HICLAW_PORT_GATEWAY` | `18080` | Higress 网关宿主机端口 |
| `HICLAW_WORKSPACE_DIR` | `~/hiclaw-manager` | Manager 工作空间路径 |
| `HICLAW_YOLO` | - | 设为 `1` 启用全自主模式 |

### Worker 容器

| 变量 | 说明 |
|------|------|
| `HICLAW_WORKER_NAME` | Worker 标识 |
| `HICLAW_MATRIX_URL` | Matrix 服务器 URL |
| `HICLAW_AI_GATEWAY_URL` | AI 网关 URL |
| `HICLAW_FS_ENDPOINT` | MinIO 端点 URL |
| `HICLAW_FS_ACCESS_KEY` | MinIO Access Key（Worker 专属） |
| `HICLAW_FS_SECRET_KEY` | MinIO Secret Key（Worker 专属） |

---

## 附录 B：关键文档链接

| 文档 | 链接 |
|------|------|
| 系统架构详解 | `docs/architecture.md` |
| 声明式资源管理 | `docs/declarative-resource-management.md` |
| K8s 原生编排 | `docs/k8s-native-agent-orch.md` |
| Manager 配置指南 | `docs/manager-guide.md` |
| Worker 部署指南 | `docs/worker-guide.md` |
| 贡献指南 | `docs/development.md` |
| 常见问题 | `docs/zh-cn/faq.md` |
| 代码库导航 | `AGENTS.md` |
| CoPaw 子系统导航 | `copaw/AGENTS.md` |
| Helm Chart 配置 | `helm/hiclaw/values.yaml` |
