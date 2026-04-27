package io.github.mfclaw.demo.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * H2 数据库管理
 * 
 * 用户表结构:
 * - user_id: 用户唯一标识
 * - worker_name: 对应的 Worker 名称
 * - room_id: Matrix Room ID
 * - matrix_user_id: Matrix 用户 ID
 * - matrix_access_token: Matrix 访问令牌
 * - last_active: 最后活跃时间
 */
public class H2Database {

    private static final String DB_URL = "jdbc:h2:~/mfclaw-demo;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private Connection connection;

    public H2Database() {
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            createTables();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize H2 database", e);
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS users (" +
            "user_id VARCHAR(64) PRIMARY KEY, " +
            "worker_name VARCHAR(64) NOT NULL, " +
            "room_id VARCHAR(128) NOT NULL, " +
            "matrix_user_id VARCHAR(128), " +
            "matrix_access_token VARCHAR(256), " +
            "last_active TIMESTAMP, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );
        stmt.close();
    }

    // ========== 用户操作 ==========

    /**
     * 查询用户
     */
    public User findUser(String userId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM users WHERE user_id = ?"
        );
        ps.setString(1, userId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            User user = new User();
            user.userId = rs.getString("user_id");
            user.workerName = rs.getString("worker_name");
            user.roomId = rs.getString("room_id");
            user.matrixUserId = rs.getString("matrix_user_id");
            user.matrixAccessToken = rs.getString("matrix_access_token");
            user.lastActive = rs.getTimestamp("last_active");
            user.createdAt = rs.getTimestamp("created_at");
            rs.close();
            ps.close();
            return user;
        }
        rs.close();
        ps.close();
        return null;
    }

    /**
     * 创建用户
     */
    public void createUser(User user) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO users (user_id, worker_name, room_id, matrix_user_id, matrix_access_token, last_active) " +
            "VALUES (?, ?, ?, ?, ?, ?)"
        );
        ps.setString(1, user.userId);
        ps.setString(2, user.workerName);
        ps.setString(3, user.roomId);
        ps.setString(4, user.matrixUserId);
        ps.setString(5, user.matrixAccessToken);
        ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
        ps.executeUpdate();
        ps.close();
    }

    /**
     * 更新用户活跃时间
     */
    public void updateLastActive(String userId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE users SET last_active = ? WHERE user_id = ?"
        );
        ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
        ps.setString(2, userId);
        ps.executeUpdate();
        ps.close();
    }

    /**
     * 更新用户的 Matrix 访问令牌
     */
    public void updateMatrixToken(String userId, String matrixUserId, String accessToken) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE users SET matrix_user_id = ?, matrix_access_token = ?, last_active = ? WHERE user_id = ?"
        );
        ps.setString(1, matrixUserId);
        ps.setString(2, accessToken);
        ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
        ps.setString(4, userId);
        ps.executeUpdate();
        ps.close();
    }

    /**
     * 检查用户是否存在
     */
    public boolean userExists(String userId) throws SQLException {
        return findUser(userId) != null;
    }

    // ========== 资源管理 ==========

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            // 忽略
        }
    }

    // ========== 用户实体类 ==========

    public static class User {
        public String userId;
        public String workerName;
        public String roomId;
        public String matrixUserId;
        public String matrixAccessToken;
        public Timestamp lastActive;
        public Timestamp createdAt;
    }
}