#!/usr/bin/env bash
# 블루-그린 무중단 전환 스크립트 (루트 docs/36 방안 1 · docs/39)
#
# 활성 색의 유일한 진실 = Nginx upstream 파일(/etc/nginx/conf.d/bce-backend-upstream.conf).
# 별도 상태 파일을 두지 않고 "실제로 서빙 중인 것"에서 역산하므로 상태 불일치가 생길 수 없다.
#
# 사용법 (배포 디렉터리 /home/ubuntu/buncheol-easy-server 에 rsync 되어 실행된다):
#   BACKEND_IMAGE=<ECR>/bce-backend:sha-... scripts/blue-green.sh deploy
#       → 비활성 색으로 pull+기동 → 헬스 → 웜업 → Nginx 전환 → 드레인 → 구 색 graceful 정지
#   scripts/blue-green.sh switch <blue|green>
#       → pull 없이 기존(정지된) 컨테이너 재기동 + 전환 — 1분 내 롤백 경로.
#         정지된 컨테이너가 직전 버전 이미지를 그대로 물고 있는 것이 이 경로의 전제다
#         (그래서 deploy 는 구 색을 정지만 하고 절대 rm 하지 않는다).
#   scripts/blue-green.sh status
#       → 활성 색·컨테이너·헬스 일괄 출력.
#
# 전제(1회성 셋업, docs/39): Nginx snippet 이 upstream(bce_backend)을 참조하고
# upstream 파일이 존재해야 한다 — 미충족이면 어떤 것도 건드리기 전에 중단한다.
set -euo pipefail

COMPOSE_FILE="docker-compose.staging.yml"
UPSTREAM_CONF="/etc/nginx/conf.d/bce-backend-upstream.conf"
SNIPPET="/etc/nginx/snippets/proxy-backend.conf"
LEGACY_CONTAINER="buncheoleasy-backend"   # 블루-그린 도입 전 단일 컨테이너 — 첫 배포 때만 만난다
DRAIN_SECONDS="${DRAIN_SECONDS:-30}"      # Nginx reload 후 구 색이 진행 중 요청을 마무리할 유예
HEALTH_DEADLINE_SECONDS=180

# compose 는 항상 저장소 루트에서, 두 색 profile 을 모두 보이게 실행한다
# (색 서비스 기동은 반드시 서비스명 명시 — 이 스크립트는 이름 없는 up 을 쓰지 않는다).
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."
export COMPOSE_PROFILES=blue,green

log() { printf '[blue-green] %s\n' "$*"; }
die() { printf '::error::[blue-green] %s\n' "$*" >&2; exit 1; }

port_of() { if [ "$1" = "blue" ]; then echo 8080; else echo 8081; fi; }
other_of() { if [ "$1" = "blue" ]; then echo green; else echo blue; fi; }
container_of() { echo "buncheoleasy-backend-$1"; }
container_exists() { docker inspect "$1" >/dev/null 2>&1; }
container_running() { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ]; }

# ── Nginx ────────────────────────────────────────────────────────────────────

precheck_nginx() {
  # 전환 지점이 없는 상태에서 배포를 진행하면 구 색 정지 순간 중단이 난다 — 시작 전에 멈춘다.
  sudo test -f "$UPSTREAM_CONF" \
    || die "upstream 파일 없음: $UPSTREAM_CONF — Nginx 1회성 셋업(docs/39) 먼저"
  sudo grep -q "bce_backend" "$SNIPPET" 2>/dev/null \
    || die "snippet($SNIPPET)이 upstream(bce_backend)을 참조하지 않는다 — Nginx 1회성 셋업(docs/39) 먼저"
}

active_color() {
  local port
  # grep 실패(파일 손상)를 삼켜 case 의 die 가 원인을 말하게 한다 — set -e 의 무언사 방지.
  port=$(sudo grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM_CONF" 2>/dev/null | head -1 | cut -d: -f2 || true)
  case "$port" in
    8080) echo blue ;;
    8081) echo green ;;
    *) die "upstream 파일에서 활성 포트를 읽지 못했다: '$port' ($UPSTREAM_CONF)" ;;
  esac
}

switch_nginx() {
  local port="$1" prev
  prev=$(sudo cat "$UPSTREAM_CONF")
  printf '# bce backend 활성 색 — 이 파일이 유일한 진실. 수정은 scripts/blue-green.sh 로만 (docs/39)\nupstream bce_backend { server 127.0.0.1:%s; }\n' "$port" \
    | sudo tee "$UPSTREAM_CONF" > /dev/null
  if ! sudo nginx -t; then
    # 문법 실패 시 원복 — 이 시점엔 reload 전이라 서빙은 계속 구 설정으로 돌고 있다.
    printf '%s\n' "$prev" | sudo tee "$UPSTREAM_CONF" > /dev/null
    die "nginx -t 실패 — upstream 파일을 원복했다. 서빙은 기존 색 그대로다."
  fi
  # reload 는 무중단 — 신규 연결은 새 upstream, 진행 중 연결은 구 워커가 마무리한다.
  sudo systemctl reload nginx
  log "Nginx upstream → 127.0.0.1:${port} 전환 완료"
}

# ── 헬스·검증 ─────────────────────────────────────────────────────────────────

wait_healthy() {
  local port="$1" ctr="$2" deadline=$((SECONDS + HEALTH_DEADLINE_SECONDS))
  log "헬스체크 대기 — http://127.0.0.1:${port}/actuator/health"
  # 벽시계 데드라인(횟수×간격 곱셈 예산 금지 — #89 리뷰 교훈). curl 호출당 상한 필수.
  until curl -fsS --connect-timeout 3 --max-time 5 "http://127.0.0.1:${port}/actuator/health" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "헬스체크 ${HEALTH_DEADLINE_SECONDS}s 초과 — 컨테이너 로그:"
      docker logs "$ctr" --tail=100 || true
      # restart: unless-stopped 라 부팅 실패가 무한 재시도로 램을 갉아먹는다 — 루프를 멈춘다.
      # 활성 색은 그대로 서빙 중이므로 사용자 영향 없음.
      docker stop -t 5 "$ctr" > /dev/null 2>&1 || true
      die "새 색(${ctr})이 헬스체크를 통과하지 못했다 — 정지시켰고, 서빙은 기존 색 그대로다."
    fi
    sleep 5
  done
  log "헬스체크 통과"
}

verify_image() {
  # "8080/8081 이 살아있다"만으로는 부족 — 떠 있는 게 배포 대상 그 이미지인지 대조
  # (promote 의 "별칭 = 지금 떠 있는 것" 불변식의 전제).
  local ctr="$1" image="$2" running expected
  running=$(docker inspect -f '{{.Image}}' "$ctr")
  expected=$(docker image inspect -f '{{.Id}}' "$image")
  [ "$running" = "$expected" ] \
    || die "떠 있는 컨테이너(${ctr})가 배포 대상 이미지가 아니다: $running != $expected"
}

warmup() {
  # JVM 콜드 스타트 완화용 프라이밍 — 실패해도 배포는 계속한다(헬스는 이미 통과).
  local port="$1" i
  for i in 1 2 3; do
    curl -fsS --max-time 5 "http://127.0.0.1:${port}/v1/buncheols" > /dev/null 2>&1 \
      || { log "웜업 요청 ${i} 실패 (무해 — JIT 프라이밍 목적)"; break; }
  done
  log "웜업 완료"
}

# ── 색 기동/정지 ──────────────────────────────────────────────────────────────

start_color() {
  # pull·재생성 없이 기존 컨테이너를 그대로 재기동한다 — 정지된 컨테이너가 물고 있는
  # 이미지가 곧 롤백 좌표라서, 여기서 up(재생성)을 쓰면 롤백이 롤백이 아니게 된다.
  local color="$1" ctr
  ctr=$(container_of "$color")
  if container_exists "$ctr"; then
    docker start "$ctr" > /dev/null
    log "${ctr} 기동"
  elif [ "$color" = "blue" ] && container_exists "$LEGACY_CONTAINER"; then
    # 블루-그린 도입 직후: blue 자리(8080)의 직전 버전은 구 이름 컨테이너다.
    docker start "$LEGACY_CONTAINER" > /dev/null
    log "레거시 컨테이너(${LEGACY_CONTAINER}) 기동 — 도입 전 마지막 버전"
  else
    die "${color} 색에 재기동할 컨테이너가 없다 — 이미지 좌표 지정 롤백(rollback.yml)을 사용하라 (docs/38 §5)"
  fi
}

stop_color() {
  # graceful 정지만 한다 — 절대 rm 하지 않는다. 정지된 컨테이너 = 스위치 복귀(switch) 롤백 좌표.
  local color="$1" ctr
  ctr=$(container_of "$color")
  if container_running "$ctr"; then
    # stop_grace_period: 40s 가 컨테이너에 박혀 있어 docker stop 이 그대로 존중한다.
    docker stop "$ctr" > /dev/null
    log "${ctr} graceful 정지 (진행 중 요청은 Spring graceful 이 마무리)"
  fi
  if [ "$color" = "blue" ] && container_running "$LEGACY_CONTAINER"; then
    # 레거시 컨테이너는 stop_grace_period 없이 생성됐다(docker 기본 10초) — 명시로 보정.
    docker stop -t 40 "$LEGACY_CONTAINER" > /dev/null
    log "레거시 컨테이너(${LEGACY_CONTAINER}) graceful 정지"
  fi
}

# ── 서브커맨드 ────────────────────────────────────────────────────────────────

cmd_deploy() {
  [ -n "${BACKEND_IMAGE:-}" ] || die "BACKEND_IMAGE 미설정 — 배포할 이미지 좌표가 필요하다"
  precheck_nginx

  local active target target_port target_ctr active_ctr prev
  active=$(active_color)
  target=$(other_of "$active")
  target_port=$(port_of "$target")
  target_ctr=$(container_of "$target")

  # 롤백 좌표(직전 이미지)를 남긴다 — 배포 실패·사후 롤백 때 이 값이 좌표다. 조회 실패는 무해.
  active_ctr=$(container_of "$active")
  if ! container_exists "$active_ctr" && [ "$active" = "blue" ]; then
    active_ctr="$LEGACY_CONTAINER"
  fi
  prev=$(docker inspect -f '{{.Config.Image}}' "$active_ctr" 2>/dev/null || echo "none")
  echo "::notice::활성 ${active} → 대상 ${target} | 롤백 좌표(직전 이미지): ${prev}"

  # 박스는 pull 만 한다 — 빌드 부하 0 이 이 구조의 존재 이유(docs/38).
  # timeout 은 박스·네트워크 이상 시 잡이 매달리지 않게 하는 안전망(35 §11 교훈 유지).
  # ECR/네트워크 장애 시에도 로컬에 이미지가 있으면 진행한다 — 불변 태그라 로컬 == 원격이
  # 정의상 보장(#89 4차 리뷰의 롤백 내성과 동일 근거).
  if docker image inspect "$BACKEND_IMAGE" > /dev/null 2>&1; then
    timeout -k 30s 5m docker compose -f "$COMPOSE_FILE" pull "backend-${target}" \
      || log "pull 실패 — 로컬 캐시 이미지로 진행 (불변 태그라 동일 이미지)"
  else
    timeout -k 30s 5m docker compose -f "$COMPOSE_FILE" pull "backend-${target}"
  fi
  # up 은 대상 색 서비스만 — 활성 색 컨테이너는 건드리지 않으므로 BACKEND_IMAGE 가
  # 바뀌어도 재생성되지 않는다.
  timeout -k 30s 3m docker compose -f "$COMPOSE_FILE" up -d "backend-${target}"

  wait_healthy "$target_port" "$target_ctr"
  verify_image "$target_ctr" "$BACKEND_IMAGE"
  warmup "$target_port"

  switch_nginx "$target_port"

  log "드레인 ${DRAIN_SECONDS}s — 구 색으로 이미 프록시된 요청이 끝나기를 기다린다"
  sleep "$DRAIN_SECONDS"
  stop_color "$active"

  echo "::notice::배포 완료 — 활성: ${target}(${target_port}). 1분 롤백: scripts/blue-green.sh switch ${active}"
}

cmd_switch() {
  local target="${1:-}" active target_port target_ctr
  case "$target" in blue|green) ;; *) die "사용법: blue-green.sh switch <blue|green>" ;; esac
  precheck_nginx
  active=$(active_color)
  [ "$target" != "$active" ] || die "이미 ${target} 이 활성이다 — 전환할 것이 없다"
  target_port=$(port_of "$target")
  target_ctr=$(container_of "$target")
  if ! container_exists "$target_ctr"; then
    if [ "$target" = "blue" ] && container_exists "$LEGACY_CONTAINER"; then
      target_ctr="$LEGACY_CONTAINER"
    else
      die "${target} 색에 컨테이너가 없다 — 이미지 좌표 지정 롤백(rollback.yml)을 사용하라 (docs/38 §5)"
    fi
  fi

  start_color "$target"
  wait_healthy "$target_port" "$target_ctr"
  switch_nginx "$target_port"
  log "드레인 ${DRAIN_SECONDS}s"
  sleep "$DRAIN_SECONDS"
  stop_color "$active"
  echo "::notice::전환 완료 — 활성: ${target}(${target_port}). 복귀: scripts/blue-green.sh switch ${active}"
}

cmd_status() {
  precheck_nginx
  local active port
  active=$(active_color)
  port=$(port_of "$active")
  echo "활성 색: ${active} (127.0.0.1:${port})"
  echo "--- upstream ---"
  sudo cat "$UPSTREAM_CONF"
  echo "--- 컨테이너 ---"
  docker ps -a --filter "name=buncheoleasy-backend" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'
  echo "--- 헬스 ---"
  for p in 8080 8081; do
    if curl -fsS --connect-timeout 2 --max-time 4 "http://127.0.0.1:${p}/actuator/health" > /dev/null 2>&1; then
      echo "  ${p}: UP"
    else
      echo "  ${p}: down"
    fi
  done
}

case "${1:-}" in
  deploy) cmd_deploy ;;
  switch) cmd_switch "${2:-}" ;;
  status) cmd_status ;;
  *) die "사용법: blue-green.sh <deploy|switch <blue|green>|status>" ;;
esac
