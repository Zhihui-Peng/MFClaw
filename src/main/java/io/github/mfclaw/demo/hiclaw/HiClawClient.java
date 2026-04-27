package io.github.mfclaw.demo.hiclaw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * HiClaw Controller API 客户端
 * 
 * 通过 docker exec 在 hiclaw-controller 容器内执行 curl 来访问 API
 * 这是因为 Controller API (8090) 默认未暴露到宿主机
 */
public class HiClawClient {

    private static final String CONTROLLER_CONTAINER = "hiclaw-controller";
    private static final String API_BASE = "http://127.0.0.1:8090/api/v1";
    private static final ObjectMapper mapper = new ObjectMapper();

    private String token;

    public HiClawClient() {
        this.token = getTokenFromController();
    }

    /**
     * 从 Controller 容器获取 CLI token
     */
    private String getTokenFromController() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", CONTROLLER_CONTAINER,
                "cat", "/var/run/hiclaw/cli-token"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String token = reader.readLine();
            p.waitFor();
            return token;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get CLI token from controller", e);
        }
    }

    /**
     * 执行 API 请求
     * 
     * 通过 docker exec -i 在容器内执行 curl，JSON body 通过 stdin 传递，
     * 避免 Windows 上 ProcessBuilder 参数中双引号被拆分的问题
     */
    private JsonNode executeRequest(String method, String path, String body) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("exec");
            cmd.add("-i");  // 保持 stdin 打开
            cmd.add(CONTROLLER_CONTAINER);
            cmd.add("curl");
            cmd.add("-s");
            cmd.add("-m");
            cmd.add("60");
            cmd.add("-X");
            cmd.add(method);
            cmd.add("-H");
            cmd.add("Authorization: Bearer " + token);
            cmd.add("-H");
            cmd.add("Content-Type: application/json");
            if (body != null) {
                cmd.add("-d");
                cmd.add("@-");  // 从 stdin 读取 body
            }
            cmd.add(API_BASE + path);

            System.out.println("[HiClawClient] " + method + " " + path);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // 写 JSON body 到 stdin
            if (body != null) {
                java.io.OutputStream stdin = p.getOutputStream();
                stdin.write(body.getBytes("UTF-8"));
                stdin.flush();
                stdin.close();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            boolean finished = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("Command timeout after 60s");
            }

            String responseStr = response.toString();
            System.out.println("[HiClawClient] 响应: " + responseStr.substring(0, Math.min(200, responseStr.length())));

            return mapper.readTree(responseStr);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API request failed: " + method + " " + path, e);
        }
    }

    // ========== Worker API ==========

    /**
     * 创建 Worker
     * @param name Worker 名称
     * @param model LLM 模型
     * @return Worker 响应
     */
    public JsonNode createWorker(String name, String model) {
        String body = String.format("{\"name\":\"%s\",\"model\":\"%s\"}", name, model);
        return executeRequest("POST", "/workers", body);
    }

    /**
     * 获取 Worker 信息
     */
    public JsonNode getWorker(String name) {
        return executeRequest("GET", "/workers/" + name, null);
    }

    /**
     * 列出所有 Workers
     */
    public JsonNode listWorkers() {
        return executeRequest("GET", "/workers", null);
    }

    /**
     * 唤醒 Worker
     */
    public JsonNode wakeWorker(String name) {
        return executeRequest("POST", "/workers/" + name + "/wake", null);
    }

    /**
     * 休眠 Worker
     */
    public JsonNode sleepWorker(String name) {
        return executeRequest("POST", "/workers/" + name + "/sleep", null);
    }

    /**
     * 删除 Worker
     */
    public JsonNode deleteWorker(String name) {
        return executeRequest("DELETE", "/workers/" + name, null);
    }

        /**
     * 配置 Worker 的 autoReply（允许直接回复，无需 @mention）
     * 通过 MinIO 直接修改配置源文件（持久化，重启后不会被覆盖）
     * @param name Worker 名称
     * @return 是否成功
     */
    public boolean configureWorkerAutoReply(String name) {
        try {
            // MinIO 配置
            String minioAlias = "local";
            String minioEndpoint = "http://127.0.0.1:9000";
            String minioUser = "admin";
            String minioPassword = "admin46c1cab2ae4a";
            String minioBucket = "hiclaw-storage";
            String minioPath = "agents/" + name + "/openclaw.json";
            String tmpFile = "/tmp/openclaw_" + name + ".json";

            // 1. 设置 mc alias
            List<String> aliasCmd = new ArrayList<>();
            aliasCmd.add("docker");
            aliasCmd.add("exec");
            aliasCmd.add(CONTROLLER_CONTAINER);
            aliasCmd.add("mc");
            aliasCmd.add("alias");
            aliasCmd.add("set");
            aliasCmd.add(minioAlias);
            aliasCmd.add(minioEndpoint);
            aliasCmd.add(minioUser);
            aliasCmd.add(minioPassword);

            System.out.println("[HiClawClient] 设置 MinIO alias");
            ProcessBuilder aliasPb = new ProcessBuilder(aliasCmd);
            aliasPb.redirectErrorStream(true);
            Process aliasP = aliasPb.start();
            aliasP.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            // 2. 从 MinIO 下载配置文件
            List<String> downloadCmd = new ArrayList<>();
            downloadCmd.add("docker");
            downloadCmd.add("exec");
            downloadCmd.add(CONTROLLER_CONTAINER);
            downloadCmd.add("mc");
            downloadCmd.add("cp");
            downloadCmd.add(minioAlias + "/" + minioBucket + "/" + minioPath);
            downloadCmd.add(tmpFile);

            System.out.println("[HiClawClient] 下载配置: " + minioPath);
            ProcessBuilder downloadPb = new ProcessBuilder(downloadCmd);
            downloadPb.redirectErrorStream(true);
            Process downloadP = downloadPb.start();
            boolean downloadOk = downloadP.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (!downloadOk) {
                System.out.println("[HiClawClient] 下载配置超时");
                return false;
            }

            // 3. 修改配置文件（groupPolicy: open, requireMention: false）
            String pythonScript =
                "import json\n" +
                "with open('" + tmpFile + "') as f: d=json.load(f)\n" +
                "d['channels']['matrix']['groupPolicy']='open'\n" +
                "d['channels']['matrix']['groups']['*']['requireMention']=False\n" +
                "with open('" + tmpFile + "','w') as f: json.dump(d,f,indent=2)\n" +
                "print('modified')\n";

            List<String> modifyCmd = new ArrayList<>();
            modifyCmd.add("docker");
            modifyCmd.add("exec");
            modifyCmd.add(CONTROLLER_CONTAINER);
            modifyCmd.add("python3");
            modifyCmd.add("-c");
            modifyCmd.add(pythonScript);

            System.out.println("[HiClawClient] 修改配置: groupPolicy=open, requireMention=false");
            ProcessBuilder modifyPb = new ProcessBuilder(modifyCmd);
            modifyPb.redirectErrorStream(true);
            Process modifyP = modifyPb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(modifyP.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[HiClawClient] " + line);
            }
            boolean modifyOk = modifyP.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!modifyOk || modifyP.exitValue() != 0) {
                System.out.println("[HiClawClient] 修改配置失败");
                return false;
            }

            // 4. 上传修改后的配置到 MinIO
            List<String> uploadCmd = new ArrayList<>();
            uploadCmd.add("docker");
            uploadCmd.add("exec");
            uploadCmd.add(CONTROLLER_CONTAINER);
            uploadCmd.add("mc");
            uploadCmd.add("cp");
            uploadCmd.add(tmpFile);
            uploadCmd.add(minioAlias + "/" + minioBucket + "/" + minioPath);

            System.out.println("[HiClawClient] 上传配置到 MinIO");
            ProcessBuilder uploadPb = new ProcessBuilder(uploadCmd);
            uploadPb.redirectErrorStream(true);
            Process uploadP = uploadPb.start();
            boolean uploadOk = uploadP.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (!uploadOk) {
                System.out.println("[HiClawClient] 上传配置超时");
                return false;
            }

            // 5. 重启 Worker 容器，让它从 MinIO 拉取新配置
            String containerName = "hiclaw-worker-" + name;
            System.out.println("[HiClawClient] 重启 Worker 容器: " + containerName);
            List<String> restartCmd = new ArrayList<>();
            restartCmd.add("docker");
            restartCmd.add("restart");
            restartCmd.add(containerName);
            ProcessBuilder restartPb = new ProcessBuilder(restartCmd);
            restartPb.redirectErrorStream(true);
            Process restartP = restartPb.start();
            boolean restartOk = restartP.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!restartOk) {
                System.out.println("[HiClawClient] Worker 重启超时");
                return false;
            }

            // 6. 等待 Worker 容器完全就绪
            System.out.println("[HiClawClient] 等待 Worker 就绪...");
            boolean ready = waitForWorkerReady(name, 60000);
            if (!ready) {
                System.out.println("[HiClawClient] Worker 就绪超时");
                return false;
            }

            System.out.println("[HiClawClient] autoReply 配置完成（MinIO 持久化 + 容器已重启）");
            return true;
        } catch (Exception e) {
            System.out.println("[HiClawClient] 配置失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 等待 Worker 就绪
     * @param name Worker 名称
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否就绪
     */
    public boolean waitForWorkerReady(String name, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                JsonNode worker = getWorker(name);
                String phase = worker.has("phase") ? worker.get("phase").asText() : "";
                if ("Running".equals(phase)) {
                    return true;
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                // 忽略错误，继续等待
            }
        }
        return false;
    }

    // ========== Getter ==========

    public String getToken() {
        return token;
    }
}
