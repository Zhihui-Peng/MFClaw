package io.github.mfclaw.demo;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mfclaw.demo.db.H2Database;
import io.github.mfclaw.demo.hiclaw.HiClawClient;
import io.github.mfclaw.demo.matrix.MatrixClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 登录 Servlet
 * 
 * 流程:
 * 1. 接收 UserID
 * 2. 检查数据库是否已存在用户
 * 3. 若不存在，创建独立 Worker + Matrix 账号 + 独立 Room
 * 4. 重定向到聊天页面
 * 
 * 每个用户获得完全隔离的环境:
 * - 独立的 Worker（命名为 worker-{userId})
 * - 独立的 Matrix Room（Worker 创建时自动生成）
 * - 独立的 Matrix 账号（以用户身份发送消息）
 */
public class LoginServlet extends HttpServlet {

    private final H2Database database;
    private final HiClawClient hiClawClient;
    private final MatrixClient matrixClient;

    // Matrix 配置
    private static final String REGISTRATION_TOKEN = "0e8e78fd879d1b13d21136ad0ff5849d91be8f9d91fa8572403f531cf955d61c";
    private static final String MATRIX_DOMAIN = "matrix-local.hiclaw.io:18080";
    
    // Admin 凭证（用于邀请用户加入房间）
    private static final String ADMIN_TOKEN = "fguVlXbGrxXgEVuUIGbxroER6q2KVpCC";
    
    // 默认 LLM 模型（与 Higress MCPBridge 中注册的模型一致）
    private static final String DEFAULT_MODEL = "step-3.5-flash";
    
    // Worker 创建超时时间
    private static final long WORKER_CREATE_TIMEOUT_MS = 120000;

    public LoginServlet(H2Database database, HiClawClient hiClawClient, MatrixClient matrixClient) {
        this.database = database;
        this.hiClawClient = hiClawClient;
        this.matrixClient = matrixClient;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 显示登录页面
        resp.sendRedirect("/login.html");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String userId = req.getParameter("userId");
        
        if (userId == null || userId.trim().isEmpty()) {
            resp.sendError(400, "请输入用户 ID");
            return;
        }

        // 清理用户名：只保留字母数字下划线
        userId = userId.trim().replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase();
        if (userId.length() > 32) {
            userId = userId.substring(0, 32);
        }
        
        HttpSession session = req.getSession(true);

        try {
            // 检查用户是否已存在
            H2Database.User user = database.findUser(userId);
            
            if (user == null) {
                // 新用户：创建完全隔离的环境
                
                // 1. 创建独立 Worker（若已存在则跳过）
                String workerName = "worker-" + userId;
                System.out.println("为用户 " + userId + " 创建独立 Worker: " + workerName);
                
                JsonNode existingWorker = null;
                try {
                    existingWorker = hiClawClient.getWorker(workerName);
                } catch (Exception e) {
                    // Worker 不存在，正常
                }
                
                if (existingWorker != null && existingWorker.has("phase")) {
                    System.out.println("Worker 已存在: " + workerName + ", phase=" + existingWorker.get("phase").asText());
                    // 确保 Worker 处于 Running 状态
                    if (!"Running".equals(existingWorker.get("phase").asText())) {
                        hiClawClient.wakeWorker(workerName);
                        hiClawClient.waitForWorkerReady(workerName, WORKER_CREATE_TIMEOUT_MS);
                    }
                } else {
                    // 创建新 Worker
                    JsonNode createResult = hiClawClient.createWorker(workerName, DEFAULT_MODEL);
                    String phase = createResult.has("phase") ? createResult.get("phase").asText() : "Pending";
                    System.out.println("Worker 创建响应: phase=" + phase);
                    
                    // 等待 Worker 就绪
                    boolean ready = hiClawClient.waitForWorkerReady(workerName, WORKER_CREATE_TIMEOUT_MS);
                    if (!ready) {
                        resp.sendError(500, "Worker 创建超时，请稍后重试");
                        return;
                    }
                }
                
                // 配置 Worker 自动回复（通过 MinIO 持久化配置）
                boolean configured = hiClawClient.configureWorkerAutoReply(workerName);
                if (!configured) {
                    System.out.println("警告: Worker autoReply 配置失败，Worker 可能需要 @mention 才回复");
                }
                
                // 3. 获取 Worker 信息（包含 roomID）
                JsonNode workerInfo = hiClawClient.getWorker(workerName);
                String roomId = workerInfo.has("roomID") ? workerInfo.get("roomID").asText() : "";
                String workerMatrixUserId = workerInfo.has("matrixUserID") ? workerInfo.get("matrixUserID").asText() : "";
                
                if (roomId.isEmpty()) {
                    resp.sendError(500, "Worker 创建成功但未获取到 Room ID");
                    return;
                }
                System.out.println("Worker 就绪: roomID=" + roomId + ", matrixUserID=" + workerMatrixUserId);
                
                // 4. 创建独立的 Matrix 用户账号（固定密码，方便重复登录）
                String matrixUsername = "demo-" + userId;
                String matrixPassword = "DemoPass@" + userId;
                String matrixUserId;
                String matrixAccessToken;
                
                try {
                    JsonNode regResult = matrixClient.register(matrixUsername, matrixPassword);
                    matrixUserId = regResult.get("user_id").asText();
                    matrixAccessToken = regResult.get("access_token").asText();
                    System.out.println("Matrix 用户创建成功: " + matrixUserId);
                } catch (Exception e) {
                    // Matrix 用户已存在但密码不匹配，使用 Admin 身份作为后备
                    System.out.println("Matrix 用户创建/登录失败: " + e.getMessage() + "，使用 Admin 身份");
                    matrixUserId = "@admin:" + MATRIX_DOMAIN;
                    matrixAccessToken = ADMIN_TOKEN;
                }
                
                // 5. 使用 Admin 身份邀请用户加入 Worker 的 Room
                MatrixClient adminClient = new MatrixClient(
                    "http://127.0.0.1:18080", REGISTRATION_TOKEN, MATRIX_DOMAIN);
                adminClient.setAccessToken(ADMIN_TOKEN, "@admin:" + MATRIX_DOMAIN);
                if (!matrixUserId.equals("@admin:" + MATRIX_DOMAIN)) {
                    adminClient.inviteToRoom(roomId, matrixUserId);
                }
                
                // 6. 用户加入 Room
                MatrixClient userClient = new MatrixClient(
                    "http://127.0.0.1:18080", REGISTRATION_TOKEN, MATRIX_DOMAIN);
                userClient.setAccessToken(matrixAccessToken, matrixUserId);
                if (!matrixUserId.equals("@admin:" + MATRIX_DOMAIN)) {
                    userClient.joinRoom(roomId);
                    System.out.println("用户已加入 Worker Room: " + roomId);
                } else {
                    System.out.println("使用 Admin 身份，已在 Room 中");
                }

                // 7. 保存用户信息到数据库
                user = new H2Database.User();
                user.userId = userId;
                user.workerName = workerName;
                user.roomId = roomId;
                user.matrixUserId = matrixUserId;
                user.matrixAccessToken = matrixAccessToken;
                database.createUser(user);
                
                System.out.println("新用户创建成功: userId=" + userId + ", worker=" + workerName + ", room=" + roomId);
            } else {
                // 已存在用户：唤醒其专属 Worker
                JsonNode workerInfo = hiClawClient.getWorker(user.workerName);
                String phase = workerInfo.has("phase") ? workerInfo.get("phase").asText() : "Unknown";
                System.out.println("用户已存在，唤醒 Worker: userId=" + userId + ", worker=" + user.workerName + ", phase=" + phase);

                if (!"Running".equals(phase)) {
                    hiClawClient.wakeWorker(user.workerName);
                    boolean ready = hiClawClient.waitForWorkerReady(user.workerName, WORKER_CREATE_TIMEOUT_MS);
                    if (!ready) {
                        resp.sendError(500, "Worker 唤醒超时，请稍后重试");
                        return;
                    }
                }

                // 重新配置 autoReply（Worker 容器重启后配置可能被 MinIO 覆盖）
                boolean configured = hiClawClient.configureWorkerAutoReply(user.workerName);
                if (!configured) {
                    System.out.println("警告: Worker autoReply 配置失败");
                }
            }

            // 设置 session
            session.setAttribute("userId", userId);
            session.setAttribute("workerName", user.workerName);
            session.setAttribute("roomId", user.roomId);
            session.setAttribute("matrixUserId", user.matrixUserId);

            // 重定向到聊天页面
            resp.sendRedirect("/chat");

        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, "登录失败: " + e.getMessage());
        }
    }
}