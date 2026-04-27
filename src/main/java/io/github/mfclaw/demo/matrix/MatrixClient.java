package io.github.mfclaw.demo.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Matrix Client-Server API 客户端
 * 
 * 用于:
 * 1. 注册新用户（使用 registration token）
 * 2. 登录获取 access_token
 * 3. 发送消息到 Room
 * 4. 同步获取新消息
 */
public class MatrixClient {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final String homeserverUrl;
    private final String registrationToken;
    private final String matrixDomain;
    private CloseableHttpClient httpClient;
    private String accessToken;
    private String userId;
    private String deviceId;
    private AtomicReference<String> syncToken = new AtomicReference<>();

    public MatrixClient(String homeserverUrl) {
        this(homeserverUrl, null, null);
    }

    public MatrixClient(String homeserverUrl, String registrationToken, String matrixDomain) {
        this.homeserverUrl = homeserverUrl;
        this.registrationToken = registrationToken;
        this.matrixDomain = matrixDomain;
        this.httpClient = HttpClients.createDefault();
    }

    // ========== 认证 ==========

    /**
     * 注册新用户，若已存在则用相同密码登录
     * @param username 用户名（不含 @ 和 domain）
     * @param password 密码
     * @return 注册/登录结果 JSON（包含 user_id, access_token, device_id）
     */
    public JsonNode register(String username, String password) throws Exception {
        String url = homeserverUrl + "/_matrix/client/v3/register";
        
        String body = String.format(
            "{\"username\":\"%s\",\"password\":\"%s\",\"auth\":{\"type\":\"m.login.registration_token\",\"token\":\"%s\"}}",
            username, password, registrationToken
        );

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body, "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());
        int status = response.getStatusLine().getStatusCode();

        if (status == 200) {
            // 注册成功
            JsonNode json = mapper.readTree(responseBody);
            if (json.has("access_token")) {
                this.accessToken = json.get("access_token").asText();
                this.userId = json.get("user_id").asText();
            }
            post.releaseConnection();
            return json;
        }

        // 检查是否是用户已存在
        if (responseBody.contains("M_USER_IN_USE")) {
            System.out.println("Matrix 用户已存在，尝试登录: " + username);
            post.releaseConnection();
            return login(username, password);
        }

        post.releaseConnection();
        throw new RuntimeException("Registration failed: " + responseBody);
    }

    /**
     * 使用用户名密码登录
     * @param username 用户名
     * @param password 密码
     * @return 登录结果 JSON
     */
    public JsonNode login(String username, String password) throws Exception {
        String url = homeserverUrl + "/_matrix/client/v3/login";
        
        String body = String.format(
            "{\"type\":\"m.login.password\",\"identifier\":{\"type\":\"m.id.user\",\"user\":\"%s\"},\"password\":\"%s\"}",
            username, password
        );

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body, "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Login failed: " + responseBody);
        }

        JsonNode json = mapper.readTree(responseBody);
        this.accessToken = json.get("access_token").asText();
        this.userId = json.get("user_id").asText();
        this.deviceId = json.has("device_id") ? json.get("device_id").asText() : null;

        post.releaseConnection();
        return json;
    }

    /**
     * 构建完整的 Matrix User ID
     */
    public String buildUserId(String username) {
        return "@" + username + ":" + matrixDomain;
    }

    // ========== Room 操作 ==========

    /**
     * 邀请用户加入房间
     * @param roomId 房间 ID
     * @param userId 被邀请的用户 ID
     */
    public void inviteToRoom(String roomId, String userId) throws Exception {
        // 需要管理员权限的 access token 来邀请
        String url = homeserverUrl + "/_matrix/client/v3/rooms/" + 
                     URLEncoder.encode(roomId, "UTF-8") + 
                     "/invite?access_token=" + accessToken;

        String body = String.format("{\"user_id\":\"%s\"}", userId);

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(body, "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(post);
        EntityUtils.consume(response.getEntity());
        post.releaseConnection();

        if (response.getStatusLine().getStatusCode() != 200) {
            // 邀请失败可能因为用户已在房间中，忽略错误
            System.out.println("Invite response: " + response.getStatusLine().getStatusCode());
        }
    }

    /**
     * 加入房间
     * @param roomId 房间 ID
     */
    public void joinRoom(String roomId) throws Exception {
        String url = homeserverUrl + "/_matrix/client/v3/rooms/" + 
                     URLEncoder.encode(roomId, "UTF-8") + 
                     "/join?access_token=" + accessToken;

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity("{}", "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(post);
        EntityUtils.consume(response.getEntity());
        post.releaseConnection();

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Join room failed: " + response.getStatusLine());
        }
    }

    /**
     * 使用已有的 access_token 设置客户端
     */
    public void setAccessToken(String accessToken, String userId) {
        this.accessToken = accessToken;
        this.userId = userId;
    }

    // ========== 消息发送 ==========

    /**
     * 发送文本消息到 Room（带 Matrix 结构化 mention）
     * @param roomId Room ID
     * @param text 消息文本
     * @param mentionUserIds 需要 @mention 的 Matrix User ID 列表
     * @return event_id
     */
    public String sendMentionMessage(String roomId, String text, String[] mentionUserIds) throws Exception {
        String txnId = "txn" + System.currentTimeMillis() + (int)(Math.random() * 10000);
        String url = homeserverUrl + "/_matrix/client/v3/rooms/" +
                     URLEncoder.encode(roomId, "UTF-8") +
                     "/send/m.room.message/" + txnId +
                     "?access_token=" + accessToken;

        // 构建 formatted_body（HTML）
        StringBuilder formattedBody = new StringBuilder();
        for (String uid : mentionUserIds) {
            formattedBody.append("<a href=\"https://matrix.to/#/")
                       .append(uid).append("\">")
                       .append(uid.substring(1)).append("</a>");
        }
        formattedBody.append(": ").append(text);

        // 构建 body 纯文本
        StringBuilder plainBody = new StringBuilder();
        for (String uid : mentionUserIds) {
            plainBody.append(uid);
        }
        plainBody.append(": ").append(text);

        // 用 ObjectMapper 构建 JSON（自动处理转义）
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("msgtype", "m.text");
        bodyMap.put("body", plainBody.toString());
        bodyMap.put("format", "org.matrix.custom.html");
        bodyMap.put("formatted_body", formattedBody.toString());
        
        Map<String, Object> mentionsMap = new LinkedHashMap<>();
        mentionsMap.put("user_ids", Arrays.asList(mentionUserIds));
        bodyMap.put("m.mentions", mentionsMap);

        String body = mapper.writeValueAsString(bodyMap);

        HttpPut put = new HttpPut(url);
        put.setHeader("Content-Type", "application/json");
        put.setEntity(new StringEntity(body, "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(put);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Send message failed: " + responseBody);
        }

        JsonNode json = mapper.readTree(responseBody);
        put.releaseConnection();
        return json.get("event_id").asText();
    }

    /**
     * 发送文本消息到 Room
     * @param roomId Room ID (如 !abc123:server)
     * @param text 文本内容
     * @return event_id
     */
    public String sendTextMessage(String roomId, String text) throws Exception {
        String txnId = "txn" + System.currentTimeMillis();
        String url = homeserverUrl + "/_matrix/client/v3/rooms/" + 
                     URLEncoder.encode(roomId, "UTF-8") + 
                     "/send/m.room.message/" + txnId + 
                     "?access_token=" + accessToken;

        String body = String.format("{\"msgtype\":\"m.text\",\"body\":\"%s\"}", escapeJson(text));

        HttpPut put = new HttpPut(url);
        put.setHeader("Content-Type", "application/json");
        put.setEntity(new StringEntity(body, "UTF-8"));

        CloseableHttpResponse response = httpClient.execute(put);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Send message failed: " + responseBody);
        }

        JsonNode json = mapper.readTree(responseBody);
        put.releaseConnection();
        return json.get("event_id").asText();
    }

    // ========== 消息同步 ==========

    /**
     * 同步消息（长轮询）
     * @param timeoutMs 超时时间（毫秒）
     * @return 同步响应 JSON
     */
    public JsonNode sync(long timeoutMs) throws Exception {
        StringBuilder url = new StringBuilder(homeserverUrl);
        url.append("/_matrix/client/v3/sync?access_token=").append(accessToken);
        url.append("&timeout=").append(timeoutMs);
        url.append("&filter=").append(URLEncoder.encode(
            "{\"room\":{\"timeline\":{\"limit\":50}}}", "UTF-8"
        ));
        
        String since = syncToken.get();
        if (since != null && !since.isEmpty()) {
            url.append("&since=").append(since);
        }

        HttpGet get = new HttpGet(url.toString());
        CloseableHttpResponse response = httpClient.execute(get);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Sync failed: " + responseBody);
        }

        JsonNode json = mapper.readTree(responseBody);
        
        // 更新 sync token
        if (json.has("next_batch")) {
            syncToken.set(json.get("next_batch").asText());
        }

        get.releaseConnection();
        return json;
    }

    /**
     * 获取 Room 消息历史
     * @param roomId Room ID
     * @param from 起始点（可选）
     * @param limit 数量限制
     */
    public JsonNode getRoomMessages(String roomId, String from, int limit) throws Exception {
        StringBuilder url = new StringBuilder(homeserverUrl);
        url.append("/_matrix/client/v3/rooms/");
        url.append(URLEncoder.encode(roomId, "UTF-8"));
        url.append("/messages?access_token=").append(accessToken);
        url.append("&dir=b&limit=").append(limit);
        if (from != null) {
            url.append("&from=").append(from);
        }

        HttpGet get = new HttpGet(url.toString());
        CloseableHttpResponse response = httpClient.execute(get);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Get messages failed: " + responseBody);
        }

        JsonNode json = mapper.readTree(responseBody);
        get.releaseConnection();
        return json;
    }

    // ========== Room 操作 ==========

    /**
     * 通过 Room Alias 获取 Room ID
     * @param roomAlias Room Alias (如 #room:server)
     * @return Room ID
     */
    public String getRoomIdByAlias(String roomAlias) throws Exception {
        String url = homeserverUrl + "/_matrix/client/v3/directory/room/" + 
                     URLEncoder.encode(roomAlias, "UTF-8");

        HttpGet get = new HttpGet(url);
        CloseableHttpResponse response = httpClient.execute(get);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Get room ID failed: " + responseBody);
        }

        JsonNode json = mapper.readTree(responseBody);
        get.releaseConnection();
        return json.get("room_id").asText();
    }

    // ========== 工具方法 ==========

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUserId() {
        return userId;
    }

    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}