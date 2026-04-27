<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>HiClaw Demo - 对话</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #f5f5f5;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
        
        /* 顶部栏 */
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 15px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
        }
        .header-left {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .header h1 {
            font-size: 18px;
            font-weight: 600;
        }
        .status {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 12px;
        }
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #4ade80;
        }
        .status-dot.connecting {
            background: #fbbf24;
            animation: pulse 1s infinite;
        }
        .status-dot.error {
            background: #ef4444;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .exit-btn {
            background: rgba(255,255,255,0.2);
            color: white;
            border: 1px solid rgba(255,255,255,0.3);
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 14px;
            transition: background 0.2s;
        }
        .exit-btn:hover {
            background: rgba(255,255,255,0.3);
        }

        /* 消息区域 */
        .messages {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
        }
        .message {
            max-width: 70%;
            margin-bottom: 16px;
            padding: 12px 16px;
            border-radius: 12px;
            line-height: 1.5;
            word-wrap: break-word;
        }
        .message.user {
            background: #667eea;
            color: white;
            margin-left: auto;
            border-bottom-right-radius: 4px;
        }
        .message.worker {
            background: white;
            color: #333;
            margin-right: auto;
            border-bottom-left-radius: 4px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .message .sender {
            font-size: 11px;
            font-weight: 600;
            margin-bottom: 4px;
            opacity: 0.7;
        }
        .message .text {
            font-size: 14px;
        }
        .message .time {
            font-size: 10px;
            margin-top: 4px;
            opacity: 0.5;
            text-align: right;
        }
        .system-message {
            text-align: center;
            color: #999;
            font-size: 13px;
            margin: 16px 0;
        }

        /* 输入区域 */
        .input-area {
            background: white;
            padding: 16px 20px;
            border-top: 1px solid #e0e0e0;
            display: flex;
            gap: 12px;
        }
        .input-area input {
            flex: 1;
            padding: 12px 16px;
            border: 2px solid #e0e0e0;
            border-radius: 8px;
            font-size: 15px;
            transition: border-color 0.3s;
        }
        .input-area input:focus {
            outline: none;
            border-color: #667eea;
        }
        .send-btn {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 8px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.2s;
        }
        .send-btn:hover {
            transform: translateY(-1px);
        }
        .send-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
            transform: none;
        }
    </style>
</head>
<body>
    <!-- 顶部栏 -->
    <div class="header">
        <div class="header-left">
            <h1>HiClaw Demo</h1>
            <div class="status">
                <div class="status-dot" id="statusDot"></div>
                <span id="statusText">连接中...</span>
            </div>
        </div>
        <button class="exit-btn" onclick="exitChat()">退出对话</button>
    </div>

    <!-- 消息区域 -->
    <div class="messages" id="messages">
        <div class="system-message">已连接到 Worker: <%= request.getAttribute("workerName") %></div>
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
        <input type="text" id="messageInput" placeholder="输入消息..." 
               autocomplete="off" onkeydown="if(event.key==='Enter')sendMessage()">
        <button class="send-btn" id="sendBtn" onclick="sendMessage()">发送</button>
    </div>

    <script>
        var userId = '<%= request.getAttribute("userId") %>';
        var roomId = '<%= request.getAttribute("roomId") %>';
        var eventSource = null;
        var lastPollTime = Date.now();

        // 轮询新消息
        function pollMessages() {
            fetch('/chat/messages')
                .then(function(resp) { return resp.json(); })
                .then(function(data) {
                    if (data.error) {
                        setStatus('error', '消息错误');
                        return;
                    }
                    if (data.messages && data.messages.length > 0) {
                        for (var i = 0; i < data.messages.length; i++) {
                            var msg = data.messages[i];
                            // 跳过自己发送的消息（已在发送时显示）
                            if (msg.sender === userId) continue;
                            addMessage(msg.sender, msg.content, msg.timestamp);
                        }
                    }
                    setStatus('connected', '已连接');
                })
                .catch(function(err) {
                    setStatus('error', '轮询失败');
                })
                .finally(function() {
                    // 每 3 秒轮询一次
                    setTimeout(pollMessages, 3000);
                });
        }

        // 发送消息
        function sendMessage() {
            var input = document.getElementById('messageInput');
            var text = input.value.trim();
            if (!text) return;
            
            var btn = document.getElementById('sendBtn');
            btn.disabled = true;
            input.value = '';
            
            fetch('/chat', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'message=' + encodeURIComponent(text)
            })
            .then(function(resp) {
                if (resp.ok) {
                    return resp.json();
                }
                throw new Error('发送失败: ' + resp.status);
            })
            .then(function(data) {
                // 显示发送的消息
                addMessage(userId, text, Date.now(), 'user');
            })
            .catch(function(err) {
                alert('发送失败: ' + err.message);
                input.value = text;
            })
            .finally(function() {
                btn.disabled = false;
                input.focus();
            });
        }

        // 添加消息到界面
        function addMessage(sender, content, timestamp, forceType) {
            var container = document.getElementById('messages');
            var div = document.createElement('div');
            
            var isUser = forceType === 'user' || sender === userId;
            div.className = 'message ' + (isUser ? 'user' : 'worker');
            
            var time = new Date(typeof timestamp === 'number' ? timestamp : parseInt(timestamp));
            var timeStr = time.getHours().toString().padStart(2, '0') + ':' + 
                         time.getMinutes().toString().padStart(2, '0');
            
            div.innerHTML = 
                '<div class="sender">' + escapeHtml(sender) + '</div>' +
                '<div class="text">' + escapeHtml(content) + '</div>' +
                '<div class="time">' + timeStr + '</div>';
            
            container.appendChild(div);
            container.scrollTop = container.scrollHeight;
        }

        // 退出
        function exitChat() {
            if (confirm('确定退出？Worker 将进入休眠状态。')) {
                window.location.href = '/chat/exit';
            }
        }

        // 状态更新
        function setStatus(state, text) {
            var dot = document.getElementById('statusDot');
            dot.className = 'status-dot ' + state;
            document.getElementById('statusText').textContent = text;
        }

        // HTML 转义
        function escapeHtml(text) {
            var div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        // 启动
        setStatus('connecting', '连接中...');
        pollMessages();
        document.getElementById('messageInput').focus();
    </script>
</body>
</html>
