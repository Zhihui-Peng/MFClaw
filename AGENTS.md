# MFClaw - HiClaw Demo 项目

## 项目目标

演示 JSP 页面与 HiClaw Worker 的 1:1 聊天集成。

## 文档结构

```
docs/
├── HiClaw/
│   └── HiClaw-综合调研报告.md   # HiClaw 深度调研（架构、API、机制）
└── HiClaw-Demo-技术方案.md      # Demo 架构设计和实现要点
```

**先读这两份文档再动手实现。**

## 技术栈（规划）

| 层 | 技术 |
|---|------|
| 前端 | JSP + JavaScript + Matrix REST API |
| 后端 | Java Servlet + H2 数据库 |
| 外部依赖 | HiClaw (D:\Workspaces\MF999\HiClaw) |

## 与 HiClaw 的关系

MFClaw 是 HiClaw 的上层 Demo 应用，调用 HiClaw Controller API：
- `POST /api/v1/workers` — 创建 Worker
- `POST /api/v1/workers/{name}/wake` — 唤醒
- `POST /api/v1/workers/{name}/sleep` — 休眠

HiClaw 源码在相邻目录：`D:\Workspaces\MF999\HiClaw`

## 核心难点

1. **Worker 创建等待** — 需要 30-60 秒，轮询直到 phase=Running
2. **Matrix 认证** — Worker 密码在 MinIO，需通过 mc 或 Controller API 获取
3. **Room ID vs Alias** — 发送消息必须用 Room ID（叹号开头），Alias 用于查询

详见 `docs/HiClaw-Demo-技术方案.md` 第六节"踩坑要点"。