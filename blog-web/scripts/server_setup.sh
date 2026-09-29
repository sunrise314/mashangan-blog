#!/usr/bin/env bash
# Halo 服务器侧整改脚本（在服务器上以 ubuntu 用户执行）
#
# 用法：
#   chmod +x server_setup.sh
#   ./server_setup.sh backup                        # 安装每日备份（03:17，保留 7 天）并立即试跑一次
#   ./server_setup.sh web /path/halo-web-src.tar.gz # 部署前台新镜像（旧镜像留 halo-web:prev）
#   ./server_setup.sh bind8090                      # 【可选】把 Halo 8090 端口收敛到 127.0.0.1
#
# 说明：
# - 所有动作幂等，可重复执行；修改前自动留 .bak 备份
# - 前台 tar 包在本地 web/ 目录打包：tar -czf halo-web-src.tar.gz Dockerfile .output
set -euo pipefail

HALO_DIR="/home/ubuntu/halo"
BACKUP_DIR="/home/ubuntu/backups/halo"
WEB_DIR="/home/ubuntu/halo-web"
CRON_LINE="17 3 * * * ${HALO_DIR}/scripts/backup.sh >> ${BACKUP_DIR}/backup.log 2>&1"

log() { echo -e "\033[1;36m[setup]\033[0m $*"; }
warn() { echo -e "\033[1;33m[warn]\033[0m $*"; }

# ---------------------------------------------------------------------------
# 1. 每日备份
# ---------------------------------------------------------------------------
install_backup() {
  log "安装备份脚本到 ${HALO_DIR}/scripts/backup.sh"
  mkdir -p "${HALO_DIR}/scripts" "${BACKUP_DIR}/daily"
  cat > "${HALO_DIR}/scripts/backup.sh" <<'BACKUP_EOF'
#!/usr/bin/env bash
# Halo 每日备份：pg_dump 逻辑备份 + halo2 附件打包，保留 7 天。
set -euo pipefail

HALO_DIR="/home/ubuntu/halo"
BACKUP_DIR="/home/ubuntu/backups/halo/daily"
RETENTION_DAYS=7
DATE="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
echo "[$(date -Is)] backup start: $DATE"

DB_CONTAINER="$(docker ps --format '{{.Names}}' | grep -E 'db|postgres' | head -n 1)"
if [ -z "$DB_CONTAINER" ]; then
  echo "ERROR: postgres container not found" >&2
  exit 1
fi
echo "using db container: $DB_CONTAINER"

# 1) 数据库逻辑备份（--clean --if-exists 便于直接恢复）
docker exec "$DB_CONTAINER" pg_dump -U halo_pg -d halo --clean --if-exists \
  | gzip > "$BACKUP_DIR/halo_db_$DATE.sql.gz"

# 2) 附件与 Halo 配置（排除日志；不复制数据库物理目录）
tar --exclude='halo2/logs' -czf "$BACKUP_DIR/halo2_$DATE.tar.gz" -C "$HALO_DIR" halo2

# 3) 清理过期备份
find "$BACKUP_DIR" -name 'halo_db_*.sql.gz' -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -name 'halo2_*.tar.gz' -mtime "+$RETENTION_DAYS" -delete

echo "[$(date -Is)] backup done:"
ls -lh "$BACKUP_DIR" | tail -n 5
BACKUP_EOF
  chmod +x "${HALO_DIR}/scripts/backup.sh"

  log "写入 crontab（已存在则不重复）"
  (
    crontab -l 2>/dev/null | grep -v -F 'scripts/backup.sh'
    echo "$CRON_LINE"
  ) | crontab -

  log "立即试跑一次备份"
  "${HALO_DIR}/scripts/backup.sh"
  log "备份完成，产物见 ${BACKUP_DIR}/daily"
}

# ---------------------------------------------------------------------------
# 2. 前台部署
# ---------------------------------------------------------------------------
deploy_web() {
  local tarball="${1:?用法: $0 web /path/halo-web-src.tar.gz}"
  [ -f "$tarball" ] || { echo "tar 包不存在: $tarball" >&2; exit 1; }

  local cur image network published
  cur="$(docker ps --format '{{.Names}}' | grep -E 'web' | head -n 1 || true)"
  if [ -n "$cur" ]; then
    image="$(docker inspect -f '{{.Config.Image}}' "$cur")"
    log "保留旧镜像为 halo-web:prev（当前容器 $cur / $image）"
    docker tag "$image" halo-web:prev || true
  fi

  mkdir -p "$WEB_DIR/release"
  rm -rf "$WEB_DIR/release/.[!.]*" "$WEB_DIR/release"/*
  log "解包 $tarball -> $WEB_DIR/release"
  tar -xzf "$tarball" -C "$WEB_DIR/release"
  [ -f "$WEB_DIR/release/Dockerfile" ] || { echo "包内缺少 Dockerfile" >&2; exit 1; }

  log "构建新镜像 halo-web:latest"
  docker build -t halo-web:latest "$WEB_DIR/release"

  if [ -f "$WEB_DIR/docker-compose.yml" ] || [ -f "$WEB_DIR/docker-compose.yaml" ]; then
    log "通过 docker compose 重建前台"
    (cd "$WEB_DIR" && docker compose up -d)
  else
    warn "未找到 $WEB_DIR/docker-compose.yml，使用 docker 重建（沿用现网网络与端口）"
    [ -n "$cur" ] || { echo "找不到运行中的 web 容器，无法推断网络/端口，请用 compose 方式部署" >&2; exit 1; }
    network="$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$cur")"
    published="$(docker inspect -f '{{range $p,$conf := .NetworkSettings.Ports}}{{range $conf}}{{.HostPort}}{{end}}{{end}}' "$cur")"
    published="${published:-3000}"
    docker stop "$cur" || true
    docker rename "$cur" "${cur}.prev.$(date +%s)" || true
    docker run -d --name "$cur" --network "$network" \
      -p "${published}:3000" \
      -e NODE_ENV=production \
      -e HALO_API_BASE=http://halo:8090 \
      halo-web:latest
  fi

  sleep 3
  log "前台健康检查"
  curl -fsS -o /dev/null "http://127.0.0.1:${published:-3000}/" && log "前台返回 200，部署成功" \
    || warn "健康检查未通过，请用 docker logs 排查；回滚: docker tag halo-web:prev <旧镜像名> 后重建"
}

# ---------------------------------------------------------------------------
# 3. 【可选】8090 端口收敛到 127.0.0.1
#    收敛后公网无法直接访问后台，请用 SSH 隧道：
#    ssh -L 8090:127.0.0.1:8090 ubuntu@<服务器IP>
#    然后本机打开 http://127.0.0.1:8090/console
# ---------------------------------------------------------------------------
bind_8090() {
  local compose="${HALO_DIR}/docker-compose.yml"
  [ -f "$compose" ] || compose="${HALO_DIR}/docker-compose.yaml"
  [ -f "$compose" ] || { echo "找不到 $compose" >&2; exit 1; }

  if grep -Eq '^\s*-?\s*"?127\.0\.0\.1:8090:8090' "$compose"; then
    log "8090 已绑定回环地址，无需修改"
  else
    cp "$compose" "${compose}.bak.$(date +%Y%m%d%H%M%S)"
    log "修改端口映射 8090:8090 -> 127.0.0.1:8090:8090"
    python3 - "$compose" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
new, n = re.subn(r'^(\s*-\s*)"?8090:8090"?\s*$', r'\1"127.0.0.1:8090:8090"', s, flags=re.M)
if n == 0:
    sys.exit("未匹配到 8090:8090 端口映射，请手工检查")
open(p, "w", encoding="utf-8").write(new)
print(f"替换 {n} 处")
PY
  fi

  log "重建 halo 服务（前台容器走内网 http://halo:8090，不受影响）"
  (cd "$HALO_DIR" && docker compose up -d halo)
  sleep 4
  log "当前监听情况（8090 应只在 127.0.0.1）:"
  ss -ltn | grep 8090 || warn "未看到 8090 监听，请检查容器状态"
}

case "${1:-}" in
  backup) install_backup ;;
  web) deploy_web "${2:-/home/ubuntu/halo-web-src.tar.gz}" ;;
  bind8090) bind_8090 ;;
  *) grep '^#' "$0" | sed 's/^# \{0,1\}//' ; exit 1 ;;
esac
