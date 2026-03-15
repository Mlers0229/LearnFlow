#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/learnflow.env}"
TEMPLATE_DIR="${SCRIPT_DIR}/templates"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "未找到部署配置文件: ${ENV_FILE}"
  echo "请先执行: cp ${SCRIPT_DIR}/learnflow.env.example ${SCRIPT_DIR}/learnflow.env"
  exit 1
fi

if [[ "${EUID}" -ne 0 ]]; then
  echo "请使用 root 或 sudo 执行该脚本。"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/LearnFlow}"
WEB_ROOT="${WEB_ROOT:-/var/www/learnflow}"
APP_USER="${APP_USER:-learnflow}"
APP_GROUP="${APP_GROUP:-${APP_USER}}"
DOMAIN="${DOMAIN:-localhost}"
BACKEND_PORT="${BACKEND_PORT:-18081}"
AGENT_PORT="${AGENT_PORT:-8000}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-learnflow}"
DB_USER="${DB_USER:-learnflow_user}"
DB_PASSWORD="${DB_PASSWORD:-}"
LLM_API_BASE="${LLM_API_BASE:-}"
LLM_API_KEY="${LLM_API_KEY:-}"
LLM_API_MODEL="${LLM_API_MODEL:-gpt-4o-mini}"
ENABLE_LLM_PLAN="${ENABLE_LLM_PLAN:-true}"
ENABLE_NGINX="${ENABLE_NGINX:-true}"
INSTALL_PACKAGES="${INSTALL_PACKAGES:-true}"
SETUP_POSTGRES="${SETUP_POSTGRES:-true}"
BACKUP_ROOT="${BACKUP_ROOT:-${DEPLOY_ROOT}/.deploy-backups}"
NGINX_SITE_PATH="/etc/nginx/conf.d/learnflow.conf"
PKG_MANAGER=""
PYTHON_BIN=""
POSTGRES_SERVICE=""
PKG_INSTALL_ARGS=()

required_vars=(DB_PASSWORD LLM_API_BASE LLM_API_KEY)
for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "缺少必要配置: ${var_name}"
    exit 1
  fi
done

log() {
  echo
  echo "[LearnFlow Deploy] $1"
}

detect_package_manager() {
  if command -v apt-get >/dev/null 2>&1; then
    PKG_MANAGER="apt"
    PKG_INSTALL_ARGS=()
  elif command -v dnf >/dev/null 2>&1; then
    PKG_MANAGER="dnf"
    PKG_INSTALL_ARGS=(--disableexcludes=all)
  elif command -v yum >/dev/null 2>&1; then
    PKG_MANAGER="yum"
    PKG_INSTALL_ARGS=(--disableexcludes=all)
  else
    echo "未找到受支持的包管理器，当前仅支持 apt / dnf / yum。"
    exit 1
  fi
}

detect_python_bin() {
  if command -v python3.11 >/dev/null 2>&1; then
    PYTHON_BIN="python3.11"
  elif command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  else
    echo "未找到可用的 Python 解释器，请先安装 Python 3.11 或 Python 3。"
    exit 1
  fi
}

detect_postgres_service() {
  local candidates=(postgresql postgresql-17 postgresql-16 postgresql-15 postgresql-14 postgresql-13)
  local candidate

  for candidate in "${candidates[@]}"; do
    if systemctl list-unit-files "${candidate}.service" --no-legend 2>/dev/null | grep -q "${candidate}.service"; then
      POSTGRES_SERVICE="${candidate}"
      return
    fi
  done

  POSTGRES_SERVICE="postgresql"
}

install_nodejs() {
  if command -v node >/dev/null 2>&1; then
    return
  fi

  log "安装 Node.js 18"
  if [[ "${PKG_MANAGER}" == "apt" ]]; then
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt-get install -y nodejs
  else
    if [[ "${PKG_MANAGER}" == "dnf" ]]; then
      if dnf module list nodejs 2>/dev/null | grep -q "18"; then
        dnf module reset -y nodejs || true
        dnf module enable -y nodejs:18
      fi
      if dnf install -y "${PKG_INSTALL_ARGS[@]}" nodejs npm; then
        return
      fi
    else
      if yum install -y "${PKG_INSTALL_ARGS[@]}" nodejs npm; then
        return
      fi
    fi

    curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -
    if [[ "${PKG_MANAGER}" == "dnf" ]]; then
      dnf install -y "${PKG_INSTALL_ARGS[@]}" nodejs
    else
      yum install -y "${PKG_INSTALL_ARGS[@]}" nodejs
    fi
  fi
}

ensure_postgres_initialized() {
  if find /var/lib/pgsql -name PG_VERSION -print -quit 2>/dev/null | grep -q .; then
    return
  fi

  if command -v postgresql-setup >/dev/null 2>&1; then
    log "初始化 PostgreSQL 数据目录"
    postgresql-setup --initdb >/dev/null 2>&1 || postgresql-setup --initdb --unit "${POSTGRES_SERVICE}" >/dev/null 2>&1 || true
  fi
}

render_template() {
  local template_path="$1"
  local output_path="$2"
  sed \
    -e "s|__DEPLOY_ROOT__|${DEPLOY_ROOT}|g" \
    -e "s|__WEB_ROOT__|${WEB_ROOT}|g" \
    -e "s|__DOMAIN__|${DOMAIN}|g" \
    -e "s|__APP_USER__|${APP_USER}|g" \
    -e "s|__APP_GROUP__|${APP_GROUP}|g" \
    -e "s|__BACKEND_PORT__|${BACKEND_PORT}|g" \
    -e "s|__AGENT_PORT__|${AGENT_PORT}|g" \
    -e "s|__BACKEND_JAR__|${BACKEND_JAR_NAME}|g" \
    "${template_path}" > "${output_path}"
}

create_backup() {
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local backup_dir="${BACKUP_ROOT}/${timestamp}"

  log "创建部署前快照 ${backup_dir}"
  mkdir -p "${backup_dir}"

  if [[ -d "${DEPLOY_ROOT}" ]] && [[ -n "$(find "${DEPLOY_ROOT}" -mindepth 1 -maxdepth 1 2>/dev/null)" ]]; then
    mkdir -p "${backup_dir}/app"
    rsync -a \
      --exclude ".deploy-backups" \
      "${DEPLOY_ROOT}/" "${backup_dir}/app/"
  fi

  if [[ -d "${WEB_ROOT}" ]] && [[ -n "$(find "${WEB_ROOT}" -mindepth 1 -maxdepth 1 2>/dev/null)" ]]; then
    mkdir -p "${backup_dir}/web"
    rsync -a "${WEB_ROOT}/" "${backup_dir}/web/"
  fi

  mkdir -p "${backup_dir}/config"
  if [[ -d /etc/learnflow ]]; then
    rsync -a /etc/learnflow/ "${backup_dir}/config/learnflow/"
  fi
  if [[ -f /etc/systemd/system/learnflow-agent.service ]]; then
    cp /etc/systemd/system/learnflow-agent.service "${backup_dir}/config/"
  fi
  if [[ -f /etc/systemd/system/learnflow-backend.service ]]; then
    cp /etc/systemd/system/learnflow-backend.service "${backup_dir}/config/"
  fi
  if [[ -f /etc/nginx/sites-available/learnflow ]]; then
    cp /etc/nginx/sites-available/learnflow "${backup_dir}/config/"
  fi
  if [[ -f "${NGINX_SITE_PATH}" ]]; then
    cp "${NGINX_SITE_PATH}" "${backup_dir}/config/learnflow.conf"
  fi

  cat > "${backup_dir}/metadata.env" <<EOF
BACKUP_CREATED_AT=$(date -Iseconds)
DEPLOY_ROOT=${DEPLOY_ROOT}
WEB_ROOT=${WEB_ROOT}
DOMAIN=${DOMAIN}
BACKEND_PORT=${BACKEND_PORT}
AGENT_PORT=${AGENT_PORT}
EOF

  ln -sfn "${backup_dir}" "${BACKUP_ROOT}/latest"
}

ensure_packages() {
  if [[ "${INSTALL_PACKAGES}" != "true" ]]; then
    log "跳过依赖安装"
    detect_python_bin
    return
  fi

  log "安装系统依赖"
  if [[ "${PKG_MANAGER}" == "apt" ]]; then
    apt-get update
    apt-get install -y openjdk-17-jdk maven nginx postgresql postgresql-contrib python3.11 python3.11-venv python3-pip git curl rsync
  elif [[ "${PKG_MANAGER}" == "dnf" ]]; then
    dnf install -y "${PKG_INSTALL_ARGS[@]}" java-17-openjdk-devel maven nginx postgresql-server postgresql-contrib python3.11 python3.11-pip git curl rsync
  else
    yum install -y "${PKG_INSTALL_ARGS[@]}" java-17-openjdk-devel maven nginx postgresql-server postgresql-contrib python3.11 python3.11-pip git curl rsync
  fi

  install_nodejs
  detect_python_bin
}

ensure_app_user() {
  if ! id -u "${APP_USER}" >/dev/null 2>&1; then
    log "创建服务用户 ${APP_USER}"
    useradd --system --create-home --shell /bin/bash "${APP_USER}"
  fi

  if ! getent group "${APP_GROUP}" >/dev/null 2>&1; then
    groupadd --system "${APP_GROUP}"
  fi
}

sync_repo() {
  log "同步代码到 ${DEPLOY_ROOT}"
  mkdir -p "${DEPLOY_ROOT}"
  rsync -a --delete \
    --exclude ".git" \
    --exclude ".idea" \
    --exclude "frontend/node_modules" \
    --exclude "frontend/dist" \
    --exclude "backend/target" \
    --exclude "agent-platform/.venv" \
    --exclude ".deploy-backups" \
    --exclude ".m2repo" \
    --exclude "tmp" \
    --exclude "dummy" \
    --exclude "dummy2" \
    "${REPO_ROOT}/" "${DEPLOY_ROOT}/"
  chown -R "${APP_USER}:${APP_GROUP}" "${DEPLOY_ROOT}"
}

setup_postgres() {
  if [[ "${SETUP_POSTGRES}" != "true" ]]; then
    log "跳过 PostgreSQL 初始化"
    return
  fi

  detect_postgres_service
  ensure_postgres_initialized
  systemctl enable --now "${POSTGRES_SERVICE}"

  log "初始化 PostgreSQL 用户和数据库"
  runuser -u postgres -- psql <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
  ELSE
    ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASSWORD}';
  END IF;
END
\$\$;
SQL

  if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
    runuser -u postgres -- createdb -O "${DB_USER}" "${DB_NAME}"
  fi
}

build_frontend() {
  log "构建前端"
  pushd "${DEPLOY_ROOT}/frontend" >/dev/null
  runuser -u "${APP_USER}" -- npm install
  runuser -u "${APP_USER}" -- npm run build
  popd >/dev/null
}

build_backend() {
  log "构建后端"
  pushd "${DEPLOY_ROOT}/backend" >/dev/null
  runuser -u "${APP_USER}" -- mvn -DskipTests package
  popd >/dev/null

  BACKEND_JAR_NAME="$(basename "$(find "${DEPLOY_ROOT}/backend/target" -maxdepth 1 -name '*.jar' ! -name '*original*' | head -n 1)")"
  if [[ -z "${BACKEND_JAR_NAME}" ]]; then
    echo "未找到后端 jar 包，构建失败。"
    exit 1
  fi
}

build_agent() {
  log "安装 Agent 平台依赖"
  pushd "${DEPLOY_ROOT}/agent-platform" >/dev/null
  runuser -u "${APP_USER}" -- "${PYTHON_BIN}" -m venv .venv
  runuser -u "${APP_USER}" -- ./.venv/bin/pip install --upgrade pip
  runuser -u "${APP_USER}" -- ./.venv/bin/pip install -r requirements.txt
  popd >/dev/null
}

write_env_files() {
  log "写入服务环境变量"
  install -d -m 0750 /etc/learnflow

  cat > /etc/learnflow/backend.env <<EOF
SERVER_PORT=${BACKEND_PORT}
SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
SPRING_DATASOURCE_USERNAME=${DB_USER}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
LEARNFLOW_AI_AGENT_BASE_URL=http://127.0.0.1:${AGENT_PORT}
EOF

  cat > /etc/learnflow/agent.env <<EOF
LEARNFLOW_DB_URL=postgresql+psycopg2://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}
LLM_API_BASE=${LLM_API_BASE}
LLM_API_KEY=${LLM_API_KEY}
LLM_API_MODEL=${LLM_API_MODEL}
ENABLE_LLM_PLAN=${ENABLE_LLM_PLAN}
EOF

  chmod 640 /etc/learnflow/backend.env /etc/learnflow/agent.env
}

install_systemd_units() {
  log "安装 systemd 服务"
  render_template "${TEMPLATE_DIR}/learnflow-agent.service.tpl" /etc/systemd/system/learnflow-agent.service
  render_template "${TEMPLATE_DIR}/learnflow-backend.service.tpl" /etc/systemd/system/learnflow-backend.service

  systemctl daemon-reload
  systemctl enable --now learnflow-agent
  systemctl enable --now learnflow-backend
}

publish_frontend() {
  log "发布前端静态资源到 ${WEB_ROOT}"
  mkdir -p "${WEB_ROOT}"
  rsync -a --delete "${DEPLOY_ROOT}/frontend/dist/" "${WEB_ROOT}/"
  chown -R "${APP_USER}:${APP_GROUP}" "${WEB_ROOT}"
}

install_nginx_site() {
  if [[ "${ENABLE_NGINX}" != "true" ]]; then
    log "跳过 nginx 配置"
    return
  fi

  log "安装 nginx 站点配置"
  mkdir -p "$(dirname "${NGINX_SITE_PATH}")"
  render_template "${TEMPLATE_DIR}/learnflow.nginx.conf.tpl" "${NGINX_SITE_PATH}"

  nginx -t
  systemctl enable nginx
  systemctl reload nginx
}

print_summary() {
  cat <<EOF

部署完成。

访问方式：
- 前端站点: http://${DOMAIN}
- 后端接口: http://${DOMAIN}/api/
- Agent 接口: http://${DOMAIN}/agent/

常用检查命令：
- systemctl status learnflow-agent
- systemctl status learnflow-backend
- journalctl -u learnflow-agent -f
- journalctl -u learnflow-backend -f

如需启用 HTTPS，可继续执行：
- certbot --nginx -d ${DOMAIN}
EOF
}

main() {
  detect_package_manager
  ensure_packages
  ensure_app_user
  create_backup
  sync_repo
  setup_postgres
  build_frontend
  build_backend
  build_agent
  write_env_files
  install_systemd_units
  publish_frontend
  install_nginx_site
  print_summary
}

main "$@"
