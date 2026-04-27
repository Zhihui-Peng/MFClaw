package io.github.mfclaw.demo;

import io.github.mfclaw.demo.db.H2Database;
import io.github.mfclaw.demo.hiclaw.HiClawClient;
import io.github.mfclaw.demo.matrix.MatrixClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;

/**
 * Demo 应用主启动类
 * 
 * 启动嵌入式 Jetty 服务器，提供：
 * - JSP 聊天页面
 * - REST API 端点
 */
public class DemoApplication {

    private static final int PORT = 9090;
    private static final String MATRIX_HOMESERVER = "http://127.0.0.1:18080";
    private static final String MATRIX_REGISTRATION_TOKEN = "0e8e78fd879d1b13d21136ad0ff5849d91be8f9d91fa8572403f531cf955d61c";
    private static final String MATRIX_DOMAIN = "matrix-local.hiclaw.io:18080";

    private Server server;
    private H2Database database;
    private HiClawClient hiClawClient;
    private MatrixClient matrixClient;

    public void start() throws Exception {
        // 初始化组件
        database = new H2Database();
        hiClawClient = new HiClawClient();
        matrixClient = new MatrixClient(MATRIX_HOMESERVER, MATRIX_REGISTRATION_TOKEN, MATRIX_DOMAIN);

        // 配置 Jetty
        server = new Server(PORT);
        
        WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath("/");
        
        // 设置 JSP 文件目录（基于工作目录）
        File webappDir = new File("src/main/webapp");
        if (!webappDir.exists()) {
            // 如果运行在打包后的环境，尝试 target/webapp
            webappDir = new File("target/webapp");
        }
        if (!webappDir.exists()) {
            // 最后尝试当前目录
            webappDir = new File(".");
        }
        String resourceBase = webappDir.getAbsolutePath();
        System.out.println("Webapp resource base: " + resourceBase);
        context.setResourceBase(resourceBase);
        
        // 启用 JSP 支持
        context.setAttribute("javax.servlet.context.tempdir", new File("target/jsp-tmp").getAbsolutePath());
        
        // 注册 Servlet
        context.addServlet(new ServletHolder(new LoginServlet(database, hiClawClient, matrixClient)), "/login");
        context.addServlet(new ServletHolder(new ChatServlet(database, hiClawClient, matrixClient)), "/chat/*");
        context.addServlet(new ServletHolder(new ApiServlet(database, hiClawClient)), "/api/*");
        
        server.setHandler(context);
        server.start();
        
        System.out.println("Demo Application started on http://localhost:" + PORT);
        System.out.println("Open http://localhost:" + PORT + "/login.jsp to begin");
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (database != null) {
            database.close();
        }
        if (matrixClient != null) {
            matrixClient.close();
        }
    }

    public static void main(String[] args) throws Exception {
        DemoApplication app = new DemoApplication();
        app.start();
        
        // 等待服务器关闭
        app.server.join();
    }

    // Getter for testing
    public H2Database getDatabase() {
        return database;
    }

    public HiClawClient getHiClawClient() {
        return hiClawClient;
    }

    public MatrixClient getMatrixClient() {
        return matrixClient;
    }
}