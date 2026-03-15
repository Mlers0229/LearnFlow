# LearnFlow Linux 部署文档

本文档面向当前仓库结构，提供一套适合单机 Linux 服务器的快速部署方案。

推荐部署形态：

- 前端：`Nginx` 托管静态文件
- 后端：`Spring Boot`，通过 `systemd` 常驻
- Agent 平台：`FastAPI + uvicorn`，通过 `systemd` 常驻
- 数据库：`PostgreSQL`

如果你希望尽量减少手工步骤，仓库中还提供了：

- [deploy-linux.sh](/D:/Java_Project/LearnFlow/scripts/deploy-linux.sh)
- [rollback-linux.sh](/D:/Java_Project/LearnFlow/scripts/rollback-linux.sh)
- [learnflow.env.example](/D:/Java_Project/LearnFlow/scripts/learnflow.env.example)

可按如下方式使用：

```bash
cp scripts/learnflow.env.example scripts/learnflow.env
vim scripts/learnflow.env
chmod +x scripts/deploy-linux.sh
sudo bash scripts/deploy-linux.sh scripts/learnflow.env
```

回滚到最近一次部署前快照：

```bash
chmod +x scripts/rollback-linux.sh
sudo bash scripts/rollback-linux.sh scripts/learnflow.env
```

回滚到指定快照：

```bash
sudo bash scripts/rollback-linux.sh scripts/learnflow.env 20260315-210000
```

说明：

- 回滚脚本会恢复应用目录、前端静态资源、`systemd` 配置、Nginx 站点配置和 `/etc/learnflow` 环境文件
- 回滚脚本不会回滚 PostgreSQL 中已经写入的业务数据

当前项目默认端口：

- 前端开发端口：`5173`
- 后端端口：`18081`
- Agent 平台端口：`8000`

## 1. 当前项目结构

仓库主要包含三个可部署模块：

- `frontend/`
  说明：Vue 3 + Vite 前端，构建后产出静态文件
- `backend/`
  说明：Spring Boot 3 后端，连接 PostgreSQL，代理 Agent 平台
- `agent-platform/`
  说明：FastAPI 多 Agent 平台，提供计划生成、资源推荐、聊天、练习等能力

## 2. 部署前准备

建议操作系统：

- Ubuntu 22.04 LTS
- OpenCloudOS / CentOS Stream / Rocky Linux 9

建议软件版本：

- Java 17
- Node.js 18+
- Maven 3.9+
- Python 3.11
- PostgreSQL 14 或 15
- Nginx 1.18+

安装基础依赖：

`deploy-linux.sh` 已支持自动识别 `apt / dnf / yum`，如果你直接使用一键脚本，可以跳过本节手动安装。  
如果你希望手工准备环境，可参考对应系统命令：

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven nginx postgresql postgresql-contrib python3.11 python3.11-venv python3-pip git curl
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs
```

对于 OpenCloudOS / Rocky / CentOS 一类系统，可改用：

```bash
sudo dnf install -y java-17-openjdk-devel maven nginx postgresql-server postgresql-contrib python3.11 python3.11-pip git curl rsync
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
sudo dnf install -y nodejs
```

检查版本：

```bash
java -version
mvn -v
node -v
npm -v
python3.11 -V
psql --version
nginx -v
```

## 3. 拉取代码

```bash
cd /opt
sudo git clone <你的仓库地址> LearnFlow
sudo chown -R $USER:$USER /opt/LearnFlow
cd /opt/LearnFlow
```

如果你已经通过压缩包上传代码，也可以直接解压到 `/opt/LearnFlow`。

## 4. 配置 PostgreSQL

当前后端默认数据库配置可见：

- [application.yml](/D:/Java_Project/LearnFlow/backend/src/main/resources/application.yml)

当前项目默认数据库名和用户建议如下：

- 数据库：`learnflow`
- 用户：`learnflow_user`

进入 PostgreSQL：

```bash
sudo -u postgres psql
```

创建用户和数据库：

```sql
CREATE USER learnflow_user WITH PASSWORD '请替换为强密码';
CREATE DATABASE learnflow OWNER learnflow_user;
GRANT ALL PRIVILEGES ON DATABASE learnflow TO learnflow_user;
\q
```

## 5. 修改部署配置

### 5.1 后端配置

后端默认配置文件位于：

- [application.yml](/D:/Java_Project/LearnFlow/backend/src/main/resources/application.yml)

当前关键配置包括：

- `server.port=18081`
- `spring.datasource.url=jdbc:postgresql://localhost:5432/learnflow`
- `learnflow.ai-agent.base-url=http://localhost:8000`

推荐做法：

- 保留 `application.yml` 作为默认值
- 生产环境通过环境变量覆盖数据库和 Agent 地址

Spring Boot 常用环境变量映射如下：

```bash
SERVER_PORT=18081
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/learnflow
SPRING_DATASOURCE_USERNAME=learnflow_user
SPRING_DATASOURCE_PASSWORD=你的数据库密码
LEARNFLOW_AI_AGENT_BASE_URL=http://127.0.0.1:8000
```

### 5.2 Agent 平台配置

Agent 平台当前会读取：

- 数据库连接：`LEARNFLOW_DB_URL`
- 模型接口配置：`LLM_API_BASE`、`LLM_API_KEY`、`LLM_API_MODEL`
- 是否允许计划直接走大模型：`ENABLE_LLM_PLAN`

参考代码：

- [db.py](/D:/Java_Project/LearnFlow/agent-platform/app/db.py)
- [llm_runtime.py](/D:/Java_Project/LearnFlow/agent-platform/app/config/llm_runtime.py)

推荐环境变量：

```bash
LEARNFLOW_DB_URL=postgresql+psycopg2://learnflow_user:你的数据库密码@127.0.0.1:5432/learnflow
LLM_API_BASE=https://你的第三方 OpenAI 兼容接口地址
LLM_API_KEY=你的真实密钥
LLM_API_MODEL=你的默认模型
ENABLE_LLM_PLAN=true
```

安全提醒：

- 当前仓库中的 [llm_settings.py](/D:/Java_Project/LearnFlow/agent-platform/app/config/llm_settings.py) 存在明文 Key 风险
- 正式部署前应删除或改为空值
- 同时应立即轮换旧 Key，避免泄漏后被继续调用

### 5.3 前端配置

前端 API 地址当前写死在：

- [config.js](/D:/Java_Project/LearnFlow/frontend/src/api/config.js)

当前默认值是本地开发地址：

```js
export const API_BASE_URL = 'http://localhost:18081';
export const CHAT_API_BASE_URL = 'http://localhost:8000';
```

部署到 Linux 时，建议改成正式域名，或者改成统一反向代理后的地址。

如果你准备使用同域名反向代理，推荐改成：

```js
export const API_BASE_URL = 'https://你的域名';
export const CHAT_API_BASE_URL = 'https://你的域名/agent';
```

说明：

- 前端中大部分业务接口走 `API_BASE_URL + /api/...`
- 聊天流式接口直接走 `CHAT_API_BASE_URL + /api/chat/stream`
- 因此如果通过 Nginx 代理，`/agent` 前缀需要单独转发给 FastAPI

## 6. 构建项目

### 6.1 构建前端

```bash
cd /opt/LearnFlow/frontend
npm install
npm run build
```

构建产物位于：

- `frontend/dist/`

### 6.2 构建后端

```bash
cd /opt/LearnFlow/backend
mvn -DskipTests package
```

构建产物通常位于：

- `backend/target/learnflow-backend-0.0.1-SNAPSHOT.jar`

### 6.3 准备 Agent 平台

```bash
cd /opt/LearnFlow/agent-platform
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 7. 使用 systemd 托管服务

推荐至少创建两个服务：

- `learnflow-agent.service`
- `learnflow-backend.service`

### 7.1 Agent 服务

创建文件：

```bash
sudo nano /etc/systemd/system/learnflow-agent.service
```

内容如下：

```ini
[Unit]
Description=LearnFlow Agent Platform
After=network.target postgresql.service

[Service]
WorkingDirectory=/opt/LearnFlow/agent-platform
Environment=LEARNFLOW_DB_URL=postgresql+psycopg2://learnflow_user:你的数据库密码@127.0.0.1:5432/learnflow
Environment=LLM_API_BASE=https://你的第三方 OpenAI 兼容接口地址
Environment=LLM_API_KEY=你的真实密钥
Environment=LLM_API_MODEL=你的默认模型
Environment=ENABLE_LLM_PLAN=true
ExecStart=/opt/LearnFlow/agent-platform/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
```

### 7.2 后端服务

创建文件：

```bash
sudo nano /etc/systemd/system/learnflow-backend.service
```

内容如下：

```ini
[Unit]
Description=LearnFlow Backend
After=network.target postgresql.service learnflow-agent.service

[Service]
WorkingDirectory=/opt/LearnFlow/backend
Environment=SERVER_PORT=18081
Environment=SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/learnflow
Environment=SPRING_DATASOURCE_USERNAME=learnflow_user
Environment=SPRING_DATASOURCE_PASSWORD=你的数据库密码
Environment=LEARNFLOW_AI_AGENT_BASE_URL=http://127.0.0.1:8000
ExecStart=/usr/bin/java -jar /opt/LearnFlow/backend/target/learnflow-backend-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
```

### 7.3 启动服务

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now learnflow-agent
sudo systemctl enable --now learnflow-backend
```

查看状态：

```bash
sudo systemctl status learnflow-agent
sudo systemctl status learnflow-backend
```

查看日志：

```bash
journalctl -u learnflow-agent -f
journalctl -u learnflow-backend -f
```

## 8. 配置 Nginx

### 8.1 发布前端静态文件

```bash
sudo mkdir -p /var/www/learnflow
sudo cp -r /opt/LearnFlow/frontend/dist/* /var/www/learnflow/
```

### 8.2 Nginx 配置文件

创建文件：

```bash
sudo nano /etc/nginx/sites-available/learnflow
```

示例配置：

```nginx
server {
    listen 80;
    server_name 你的域名;

    root /var/www/learnflow;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:18081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /agent/ {
        proxy_pass http://127.0.0.1:8000/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
    }
}
```

启用站点：

```bash
sudo ln -s /etc/nginx/sites-available/learnflow /etc/nginx/sites-enabled/learnflow
sudo nginx -t
sudo systemctl reload nginx
```

## 9. 配置 HTTPS

安装 Certbot：

```bash
sudo apt install -y certbot python3-certbot-nginx
```

申请证书：

```bash
sudo certbot --nginx -d 你的域名
```

自动续期测试：

```bash
sudo certbot renew --dry-run
```

## 10. 验收检查

### 10.1 本机接口自检

Agent：

```bash
curl http://127.0.0.1:8000/api/chat/models
curl http://127.0.0.1:8000/api/v2/plan -X POST
```

说明：

- 第二条只是示例路径，真实调用时需要带完整 JSON 请求体

后端：

```bash
curl http://127.0.0.1:18081/api/chat/models
curl "http://127.0.0.1:18081/api/plan/recent?userId=1"
```

### 10.2 域名自检

```bash
curl https://你的域名/api/chat/models
curl -I https://你的域名
```

### 10.3 浏览器检查

重点检查以下页面：

- 登录页
- 生成学习计划页
- 历史计划页
- 练习回顾页
- 资源上传页
- 管理端 Dashboard

## 11. 常见问题排查

### 11.1 后端启动失败

重点检查：

- PostgreSQL 是否已启动
- 数据库用户名和密码是否正确
- `18081` 端口是否已被占用

命令：

```bash
sudo ss -lntp | grep 18081
journalctl -u learnflow-backend -n 200 --no-pager
```

### 11.2 Agent 启动失败

重点检查：

- Python 虚拟环境是否创建成功
- `requirements.txt` 是否完整安装
- `LEARNFLOW_DB_URL` 是否正确
- `LLM_API_BASE` 和 `LLM_API_KEY` 是否可用

命令：

```bash
sudo ss -lntp | grep 8000
journalctl -u learnflow-agent -n 200 --no-pager
```

### 11.3 前端能打开，但接口报错

重点检查：

- 前端 [config.js](/D:/Java_Project/LearnFlow/frontend/src/api/config.js) 是否已改成生产地址
- Nginx `/api/` 是否正确转发到 `18081`
- Nginx `/agent/` 是否正确转发到 `8000`
- HTTPS 页面是否还在请求 HTTP 接口

### 11.4 中文内容变成乱码或问号

建议：

- Linux 终端使用 UTF-8
- Nginx、Java、Python 环境默认都使用 UTF-8
- 不要通过错误编码方式手工写入中文配置文件

可执行：

```bash
locale
```

如果不是 UTF-8，建议设置：

```bash
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
```

## 12. 生产建议

如果准备长期运行，建议继续补这些内容：

- 使用非 `root` 用户运行服务
- 将敏感配置统一放到 `/etc/learnflow/*.env`
- 前端把接口地址改为环境化构建，而不是手改源码
- 使用 `pg_dump` 定期备份 PostgreSQL
- 为 Nginx、后端、Agent 增加日志轮转
- 为 PostgreSQL、后端、Agent 增加监控和告警

## 13. 最短上线流程

如果你只想尽快跑起来，可以按这个顺序执行：

1. 安装 Java、Node、Python、PostgreSQL、Nginx
2. 创建 `learnflow` 数据库和 `learnflow_user`
3. 修改前端 [config.js](/D:/Java_Project/LearnFlow/frontend/src/api/config.js) 为正式域名
4. 构建前端 `npm run build`
5. 构建后端 `mvn -DskipTests package`
6. 创建 Agent 虚拟环境并安装依赖
7. 用 `systemd` 启动 Agent 和后端
8. 用 Nginx 发布 `frontend/dist`
9. 配置 HTTPS
10. 打开浏览器做全链路验收

---

如果后续准备补 Docker 部署，可以在此文档基础上再增加：

- `docker-compose.yml`
- `nginx.conf`
- `.env.example`

这样会更适合一键部署和迁移。
