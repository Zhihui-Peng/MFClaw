<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>HiClaw Demo - 登录</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
        }
        .login-container {
            background: white;
            padding: 40px;
            border-radius: 12px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.2);
            width: 100%;
            max-width: 400px;
        }
        h1 {
            text-align: center;
            color: #333;
            margin-bottom: 10px;
            font-size: 28px;
        }
        .subtitle {
            text-align: center;
            color: #666;
            margin-bottom: 30px;
            font-size: 14px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 8px;
            color: #333;
            font-weight: 500;
        }
        input[type="text"] {
            width: 100%;
            padding: 12px 16px;
            border: 2px solid #e0e0e0;
            border-radius: 8px;
            font-size: 16px;
            transition: border-color 0.3s;
        }
        input[type="text"]:focus {
            outline: none;
            border-color: #667eea;
        }
        .error {
            background: #fee;
            color: #c00;
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 14px;
        }
        button {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        button:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
        }
        .info {
            margin-top: 20px;
            padding: 15px;
            background: #f0f7ff;
            border-radius: 8px;
            font-size: 13px;
            color: #555;
        }
        .info h3 {
            margin-bottom: 8px;
            color: #333;
        }
        .info ul {
            margin-left: 20px;
        }
        .info li {
            margin-bottom: 4px;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <h1>HiClaw Demo</h1>
        <p class="subtitle">一对一智能对话演示</p>
        
        <% if ("true".equals(request.getAttribute("error"))) { %>
        <div class="error"><%= request.getAttribute("error") %></div>
        <% } %>
        
        <form method="post" action="/login">
            <div class="form-group">
                <label for="userId">用户 ID</label>
                <input type="text" id="userId" name="userId" 
                       placeholder="请输入您的用户 ID" required
                       autocomplete="off">
            </div>
            <button type="submit">开始对话</button>
        </form>
        
        <div class="info">
            <h3>说明</h3>
            <ul>
                <li>首次登录会自动分配一个 AI Worker</li>
                <li>Worker 会持续运行直到您退出</li>
                <li>退出后 Worker 会进入休眠状态</li>
            </ul>
        </div>
    </div>
</body>
</html>
