package io.github.mfclaw.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mfclaw.demo.db.H2Database;
import io.github.mfclaw.demo.hiclaw.HiClawClient;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * REST API Servlet
 * 
 * 端点:
 * - GET /api/user/{userId} - 获取用户信息
 * - GET /api/worker/{name} - 获取 Worker 状态
 * - POST /api/worker/{name}/wake - 唤醒 Worker
 * - POST /api/worker/{name}/sleep - 休眠 Worker
 */
public class ApiServlet extends HttpServlet {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final H2Database database;
    private final HiClawClient hiClawClient;

    public ApiServlet(H2Database database, HiClawClient hiClawClient) {
        this.database = database;
        this.hiClawClient = hiClawClient;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            // /api/user/{userId}
            if (pathInfo.startsWith("/user/")) {
                String userId = pathInfo.substring("/user/".length());
                H2Database.User user = database.findUser(userId);
                
                if (user == null) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"User not found\"}");
                } else {
                    resp.getWriter().write(mapper.writeValueAsString(user));
                }
                return;
            }

            // /api/worker/{name}
            if (pathInfo.startsWith("/worker/")) {
                String name = pathInfo.substring("/worker/".length());
                JsonNode worker = hiClawClient.getWorker(name);
                resp.getWriter().write(mapper.writeValueAsString(worker));
                return;
            }

            // /api/workers - 列出所有 Worker
            if ("/workers".equals(pathInfo)) {
                JsonNode workers = hiClawClient.listWorkers();
                resp.getWriter().write(mapper.writeValueAsString(workers));
                return;
            }

            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Endpoint not found\"}");

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            // /api/worker/{name}/wake
            if (pathInfo.startsWith("/worker/") && pathInfo.endsWith("/wake")) {
                String name = pathInfo.substring("/worker/".length(), pathInfo.length() - "/wake".length());
                JsonNode result = hiClawClient.wakeWorker(name);
                resp.getWriter().write(mapper.writeValueAsString(result));
                return;
            }

            // /api/worker/{name}/sleep
            if (pathInfo.startsWith("/worker/") && pathInfo.endsWith("/sleep")) {
                String name = pathInfo.substring("/worker/".length(), pathInfo.length() - "/sleep".length());
                JsonNode result = hiClawClient.sleepWorker(name);
                resp.getWriter().write(mapper.writeValueAsString(result));
                return;
            }

            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Endpoint not found\"}");

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}