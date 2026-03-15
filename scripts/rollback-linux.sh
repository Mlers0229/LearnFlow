#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/learnflow.env}"
BACKUP_NAME="${2:-latest}"

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
ENABLE_NGINX="${ENABLE_NGINX:-true}"
BACKUP_ROOT="${BACKUP_ROOT:-${DEPLOY_ROOT}/.deploy-backups}"

if [[ "${BACKUP_NAME}" == "latest" ]]; then
  BACKUP_DIR="${BACKUP_ROOT}/latest"
else
  BACKUP_DIR="${BACKUP_ROOT}/${BACKUP_NAME}"
fi

if [[ ! -e "${BACKUP_DIR}" ]]; then
  echo "未找到备份目录: ${BACKUP_DIR}"
  if [[ -d "${BACKUP_ROOT}" ]]; then
    echo "当前可用备份:"
    find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -printf ' - %f\n' | sort || true
  fi
  exit 1
fi

BACKUP_DIR="$(readlink -f "${BACKUP_DIR}")"

log() {
  echo
  echo "[LearnFlow Rollback] $1"
}

restore_if_exists() {
  local src="$1"
  local dst="$2"
  if [[ -d "${src}" ]]; then
    mkdir -p "${dst}"
    rsync -a --delete "${src}/" "${dst}/"
  fi
}

restore_file_if_exists() {
  local src="$1"
  local dst="$2"
  if [[ -f "${src}" ]]; then
    cp "${src}" "${dst}"
  fi
}

stop_services() {
  log "停止 LearnFlow 服务"
  systemctl stop learnflow-backend || true
  systemctl stop learnflow-agent || true
}

restore_app() {
  log "恢复应用目录"
  if [[ ! -d "${BACKUP_DIR}/app" ]]; then
    echo "备份中缺少 app 目录，无法回滚。"
    exit 1
  fi
  mkdir -p "${DEPLOY_ROOT}"
  rsync -a --delete --exclude ".deploy-backups" "${BACKUP_DIR}/app/" "${DEPLOY_ROOT}/"
  chown -R "${APP_USER}:${APP_GROUP}" "${DEPLOY_ROOT}"
}

restore_web() {
  if [[ -d "${BACKUP_DIR}/web" ]]; then
    log "恢复前端静态资源"
    mkdir -p "${WEB_ROOT}"
    rsync -a --delete "${BACKUP_DIR}/web/" "${WEB_ROOT}/"
    chown -R "${APP_USER}:${APP_GROUP}" "${WEB_ROOT}"
  fi
}

restore_configs() {
  log "恢复环境变量和服务配置"
  restore_if_exists "${BACKUP_DIR}/config/learnflow" "/etc/learnflow"
  restore_file_if_exists "${BACKUP_DIR}/config/learnflow-agent.service" "/etc/systemd/system/learnflow-agent.service"
  restore_file_if_exists "${BACKUP_DIR}/config/learnflow-backend.service" "/etc/systemd/system/learnflow-backend.service"

  if [[ "${ENABLE_NGINX}" == "true" ]]; then
    restore_file_if_exists "${BACKUP_DIR}/config/learnflow" "/etc/nginx/sites-available/learnflow"
  fi
}

restart_services() {
  log "重新加载并启动服务"
  systemctl daemon-reload
  systemctl enable --now learnflow-agent
  systemctl enable --now learnflow-backend

  if [[ "${ENABLE_NGINX}" == "true" ]] && [[ -f /etc/nginx/sites-available/learnflow ]]; then
    nginx -t
    systemctl reload nginx
  fi
}

print_summary() {
  cat <<EOF

回滚完成。

已恢复备份：
- ${BACKUP_DIR}

建议立即检查：
- systemctl status learnflow-agent
- systemctl status learnflow-backend
- journalctl -u learnflow-agent -n 100 --no-pager
- journalctl -u learnflow-backend -n 100 --no-pager
EOF
}

main() {
  stop_services
  restore_app
  restore_web
  restore_configs
  restart_services
  print_summary
}

main "$@"
