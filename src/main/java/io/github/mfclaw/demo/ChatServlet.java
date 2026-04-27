package io.github.mfclaw.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mfclaw.demo.db.H2Database;
import io.github.mfclaw.demo.hiclaw.HiClawClient;
import io.github.mfclaw.demo.matrix.MatrixClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天 Servlet
 * 
 * GET /chat - 显示聊天页面
 * POST /chat - 发送消息
 * GET /chat/messages - SSE 轮询新消息
 */
public class ChatServlet extends HttpServlet {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final H2Database database;
    private final HiClawClient hiClawClient;
    private final MatrixClient matrixClient;

    public ChatServlet(H2Database database, HiClawClient hiClawClient, MatrixClient matrixClient) {
        this.database = database;
        this.hiClawClient = hiClawClient;
        this.matrixClient = matrixClient;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.sendRedirect("/login.html");
            return;
        }

        String pathInfo = req.getPathInfo();
        
        if ("/info".equals(pathInfo)) {
            // 返回会话信息 JSON
            handleGetInfo(req, resp);
        } else if ("/messages".equals(pathInfo)) {
            // 轮询消息
            handleMessagesPoll(req, resp);
        } else if ("/exit".equals(pathInfo)) {
            // 退出聊天
            handleExit(req, resp);
        } else {
            // 显示聊天页面（静态 HTML）
            resp.sendRedirect("/chat.html");
        }
    }

    /**
     * 返回会话信息
     */
    private void handleGetInfo(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String matrixUserId = (String) session.getAttribute("matrixUserId");
        resp.getWriter().write(String.format(
            "{\"userId\":\"%s\",\"workerName\":\"%s\",\"roomId\":\"%s\",\"matrixUserId\":\"%s\"}",
            session.getAttribute("userId"),
            session.getAttribute("workerName"),
            session.getAttribute("roomId"),
            matrixUserId != null ? matrixUserId : ""
        ));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.sendError(401, "未登录");
            return;
        }

        String roomId = (String) session.getAttribute("roomId");
        String workerName = (String) session.getAttribute("workerName");
        String matrixDomain = "matrix-local.hiclaw.io:18080";
        String message = req.getParameter("message");

        if (message == null || message.trim().isEmpty()) {
            resp.sendError(400, "消息不能为空");
            return;
        }

        try {
            // 发送消息到 Matrix Room（带 @worker mention）
            H2Database.User user = database.findUser((String) session.getAttribute("userId"));
            
            if (user.matrixAccessToken == null) {
                resp.sendError(500, "未配置 Matrix 账号");
                return;
            }

            matrixClient.setAccessToken(user.matrixAccessToken, user.matrixUserId);
            
            // 构建 Worker 的 Matrix User ID，发送带结构化 mention 的消息
            String workerMatrixId = "@" + workerName + ":" + matrixDomain;
            String plainText = message.trim();
            // 去掉前端可能已加的 @worker 前缀，我们自己构建结构化 mention
            String atPrefix = "@" + workerName + " ";
            if (plainText.startsWith(atPrefix)) {
                plainText = plainText.substring(atPrefix.length());
            }
            String eventId = matrixClient.sendMentionMessage(roomId, plainText, new String[]{workerMatrixId});

            // 返回成功响应
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":true,\"event_id\":\"" + eventId + "\"}");

        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, "发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 轮询新消息
     */
    private void handleMessagesPoll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String roomId = (String) session.getAttribute("roomId");
        String userId = (String) session.getAttribute("userId");

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            H2Database.User user = database.findUser(userId);
            
            if (user.matrixAccessToken == null) {
                resp.getWriter().write("{\"messages\":[],\"error\":\"未配置 Matrix 账号\"}");
                return;
            }

            matrixClient.setAccessToken(user.matrixAccessToken, user.matrixUserId);

            // 执行短时间同步获取新消息
            JsonNode syncResp = matrixClient.sync(3000);

            // 调试：打印 sync 关键信息
            System.out.println("[Poll] roomId=" + roomId);
            System.out.println("[Poll] next_batch=" + syncResp.path("next_batch").asText("(none)"));
            JsonNode rooms = syncResp.path("rooms").path("join");
            java.util.List<String> roomKeys = new java.util.ArrayList<>();
            java.util.Iterator<String> it = rooms.fieldNames();
            while (it.hasNext()) { roomKeys.add(it.next()); }
            System.out.println("[Poll] rooms.join keys=" + mapper.writeValueAsString(roomKeys));
            if (rooms.has(roomId)) {
                JsonNode tl = rooms.path(roomId).path("timeline");
                System.out.println("[Poll] timeline.limited=" + tl.path("limited").asBoolean() + " events=" + tl.path("events").size());
            } else {
                System.out.println("[Poll] roomId 不在 join 中！");
            }

            // 解析消息
            List<Message> messages = parseMessagesFromSync(syncResp, roomId);

            System.out.println("[Poll] parsed " + messages.size() + " messages");

            // 返回 JSON
            resp.getWriter().write("{\"messages\":" + mapper.writeValueAsString(messages) + "}");

        } catch (Exception e) {
            System.out.println("[Poll] 异常: " + e.getMessage());
            e.printStackTrace();
            resp.getWriter().write("{\"messages\":[],\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 退出聊天（休眠 Worker）
     */
    private void handleExit(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            String workerName = (String) session.getAttribute("workerName");
            String userId = (String) session.getAttribute("userId");

            try {
                // 休眠 Worker
                hiClawClient.sleepWorker(workerName);
                System.out.println("Worker 已休眠: " + workerName);

                // 更新数据库
                database.updateLastActive(userId);

            } catch (Exception e) {
                System.err.println("休眠 Worker 失败: " + e.getMessage());
            }

            session.invalidate();
        }

        resp.sendRedirect("/login");
    }

    /**
     * 从同步响应解析消息
     */
    private List<Message> parseMessagesFromSync(JsonNode syncResp, String roomId) {
        List<Message> messages = new ArrayList<>();

        try {
            JsonNode rooms = syncResp.path("rooms").path("join");
            if (rooms.has(roomId)) {
                JsonNode timeline = rooms.path(roomId).path("timeline").path("events");
                if (timeline.isArray()) {
                    for (JsonNode event : timeline) {
                        String type = event.path("type").asText();
                        if ("m.room.message".equals(type)) {
                            Message msg = new Message();
                            msg.eventId = event.path("event_id").asText();
                            msg.sender = event.path("sender").asText();
                            msg.content = event.path("content").path("body").asText();
                            msg.msgtype = event.path("content").path("msgtype").asText();
                            msg.timestamp = event.path("origin_server_ts").asLong();
                            messages.add(msg);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析消息失败: " + e.getMessage());
        }

        return messages;
    }

    /**
     * 消息对象
     */
    public static class Message {
        public String eventId;
        public String sender;
        public String content;
        public String msgtype;
        public long timestamp;
    }
}