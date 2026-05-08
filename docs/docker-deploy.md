# LearnFlow Docker 部署说明

本文档说明如何用 Docker Compose 部署 LearnFlow。编排包含 4 个服务：

- `postgres`：PostgreSQL 数据库
- `agent`：FastAPI Agent 平台，容器内端口 `8000`
- `backend`：Spring Boot 后端，容器内端口 `18081`
- `frontend`：Nginx 静态站点，并反向代理 `/api/` 和 `/agent/`

## 1. 准备环境变量

复制环境变量样例：

```bash
cp .env.example .env
```

修改 `.env`：

```env
POSTGRES_DB=learnflow
POSTGRES_USER=learnflow_user
POSTGRES_PASSWORD=替换为强密码

LLM_API_BASE=https://你的OpenAI兼容接口地址
LLM_API_KEY=你的模型API_KEY
LLM_API_MODEL=deepseek-chat
ENABLE_LLM_PLAN=true

PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
NPM_REGISTRY=https://registry.npmmirror.com

HTTP_PROXY=
HTTPS_PROXY=
NO_PROXY=localhost,127.0.0.1,::1,postgres,backend,agent,frontend
```

不要把 `.env` 提交到仓库。

如果数据库密码包含 `@`、`:`、`/`、`#` 等 URL 特殊字符，`LEARNFLOW_DB_URL` 中需要使用 URL 编码。更简单的做法是为 Docker 部署使用只包含字母、数字和下划线的数据库密码。

如果服务器无法访问默认 PyPI 或 npm registry，可以在 `.env` 中替换 `PIP_INDEX_URL` 和 `NPM_REGISTRY`。例如公司内网镜像、阿里云镜像或清华镜像。

如果依赖源仍然超时，可以只给 Docker build 阶段配置代理。宿主机 Clash/Mihomo 监听 `*:7890` 后，在 `.env` 中设置：

```env
HTTP_PROXY=http://172.17.0.1:7890
HTTPS_PROXY=http://172.17.0.1:7890
NO_PROXY=localhost,127.0.0.1,::1,postgres,backend,agent,frontend
```

Docker daemon 不建议配置该代理；拉镜像优先使用 `/etc/docker/daemon.json` 中的 registry mirror。

## 2. 构建并启动

```bash
docker compose up -d --build
```

默认暴露端口：

- 前端：`http://服务器IP/`
- 后端：`http://服务器IP:18081/`
- Agent：`http://服务器IP:8000/`

生产环境通常只需要开放前端端口 `80`，后端和 Agent 可以不对公网开放。需要关闭公网映射时，删除 `docker-compose.yml` 中 `backend` 和 `agent` 的 `ports` 配置，保留 `expose` 即可。

## 3. 反向代理路径

前端容器内 Nginx 已配置：

- `/api/` 转发到 `backend:18081`
- `/agent/` 转发到 `agent:8000`

项目前端在生产环境下会自动使用当前域名作为 API 地址，所以不需要额外修改 `frontend/src/api/config.js`。

## 4. 1Panel 部署

在 1Panel 中进入：

```text
容器 -> Compose -> 创建编排
```

选择项目目录或粘贴 `docker-compose.yml`，并确保同目录下存在 `.env`。

如果已经使用 1Panel 应用商店安装了 PostgreSQL，可以删除 compose 中的 `postgres` 服务，并把连接地址改为你的 1Panel PostgreSQL 地址：

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://你的数据库地址:5432/learnflow
LEARNFLOW_DB_URL: postgresql+psycopg2://learnflow_user:密码@你的数据库地址:5432/learnflow
```

## 5. 常用命令

查看服务状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f agent
docker compose logs -f frontend
```

重启：

```bash
docker compose restart
```

停止：

```bash
docker compose down
```

停止并删除数据库卷：

```bash
docker compose down -v
```

`down -v` 会删除 PostgreSQL 数据，生产环境慎用。
