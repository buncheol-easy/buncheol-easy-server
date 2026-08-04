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
HEALTH_DEADLINE_SECONDS="${HEALTH_DEADLINE_SECONDS:-180}"
# ⚠️ 위 예산을 늘리면 application.yaml 의 app.scheduler.activation-grace(기동 유예 게이트,
# 기본 300s)가 최악 경로(헬스+웜업+드레인+프로브)보다 큰지 같이 확인할 것 (docs/39).

# compose 는 항상 저장소 루트에서, 두 색 profile 을 모두 보이게 실행한다
# (색 서비스 기동은 반드시 서비스명 명시 — 이 스크립트는 이름 없는 up 을 쓰지 않는다).
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."
export COMPOSE_PROFILES=blue,green

log() { printf '[blue-green] %s\n' "$*"; }
die() { printf '::error::[blue-green] %s\n' "$*" >&2; exit 1; }

# DRAIN_SECONDS·LOCK_WAIT_SECONDS 는 외부 입력 — 숫자가 아니면 sleep/flock 이
# 엉뚱한 지점·엉뚱한 메시지로 죽는다. 진입 시점에 검증한다.
case "$DRAIN_SECONDS" in
  ''|*[!0-9]*) die "DRAIN_SECONDS 는 정수(초)여야 한다: '$DRAIN_SECONDS'" ;;
esac
case "${LOCK_WAIT_SECONDS:-120}" in
  ''|*[!0-9]*) die "LOCK_WAIT_SECONDS 는 정수(초)여야 한다: '${LOCK_WAIT_SECONDS:-}'" ;;
esac
case "$HEALTH_DEADLINE_SECONDS" in
  ''|*[!0-9]*) die "HEALTH_DEADLINE_SECONDS 는 정수(초)여야 한다: '$HEALTH_DEADLINE_SECONDS'" ;;
esac

# 기동 유예 게이트와의 산술 계약 검증 — 예산(헬스+웜업 15s+드레인+프로브 9s)이 유예를 넘으면
# "정지되는 색은 스케줄러 부작용 0" 보장이 조용히 깨진다. 유예는 이미지에 구워진 값(기본 300s,
# app.scheduler.activation-grace)이라 스크립트는 알 수 없어, 기본값 가정 + GRACE_SECONDS 로
# 알려받는다. 어긋나면 경고만 한다(배포 차단 사유는 아님 — docs/39, #92 6차 리뷰).
GRACE_SECONDS="${GRACE_SECONDS:-300}"
case "$GRACE_SECONDS" in
  ''|*[!0-9]*) die "GRACE_SECONDS 는 정수(초)여야 한다: '$GRACE_SECONDS'" ;;
esac
if [ $((HEALTH_DEADLINE_SECONDS + DRAIN_SECONDS + 24)) -gt "$GRACE_SECONDS" ]; then
  log "⚠️ 예산(헬스 ${HEALTH_DEADLINE_SECONDS}s + 드레인 ${DRAIN_SECONDS}s + 웜업·프로브 24s)이 기동 유예(${GRACE_SECONDS}s)를 초과 — 폐기되는 색이 스케줄러 부작용을 낼 수 있다. activation-grace 를 올릴 때는 TRACKING_REFRESH_INITIAL_DELAY_MS 도 유예보다 크게 '함께' 올려야 한다(안 하면 앱이 기동 검증에서 fail-fast — docs/39)"
fi

# 동시 실행 잠금 — 워크플로 배포와 박스 수동 switch 가 겹치면 "방금 트래픽을 받기 시작한
# 컨테이너를 재생성"하는 경합이 가능하다(러너 큐는 잡끼리만 직렬화한다).
# - 락 파일은 배포 디렉터리의 "부모"(/home/ubuntu, ubuntu 소유) — /var/lock 은 sticky 라
#   root 로 한 번 실행되면 러너(ubuntu)가 열지도 지우지도 못해 전 배포가 영구 실패한다.
#   배포 디렉터리 안은 rsync --delete 가 지워 inode 가 갈리므로 역시 부적합.
# - flock -w: 긴급 롤백이 잔여 배포의 락 때문에 즉시 죽지 않도록 상한부 대기.
# - fd 는 프로세스 종료 시 자동 해제 — stale lock 없음. 읽기 전용 status 는 락을 잡지 않는다.
acquire_lock() {
  # 고정 절대경로 — cwd 파생이면 다른 체크아웃에서 실행한 수동 switch 가 워크플로 배포와
  # 전혀 배제되지 않는다(컨테이너명은 고정이라 같은 컨테이너를 두 실행이 건드린다).
  local lock_file="${LOCK_FILE:-/home/ubuntu/.bce-blue-green.lock}"
  # exec 리다이렉션 실패는 bash 버전/모드에 따라 || 를 타지 않고 즉시 종료할 수 있다 —
  # 안내 메시지를 보장하려면 일반 리다이렉션으로 먼저 열어본다 (7차 리뷰).
  : > "$lock_file" 2>/dev/null \
    || die "락 파일 열기 실패: $lock_file (소유자/권한 확인 — root 로 실행했었다면 rm 후 재시도)"
  exec 9>"$lock_file"
  flock -w "${LOCK_WAIT_SECONDS:-120}" 9 \
    || die "다른 blue-green 작업이 ${LOCK_WAIT_SECONDS:-120}s 안에 끝나지 않았다 — status 로 상태 확인 후 재시도"
}

port_of() { if [ "$1" = "blue" ]; then echo 8080; else echo 8081; fi; }
other_of() { if [ "$1" = "blue" ]; then echo green; else echo blue; fi; }
container_of() { echo "buncheoleasy-backend-$1"; }
container_exists() { docker inspect "$1" >/dev/null 2>&1; }
container_running() { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ]; }
# healthcheck 가 정의된 컨테이너 전용 — 없는 컨테이너는 필드가 비어 false 가 된다.
container_healthy() { [ "$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null)" = "healthy" ]; }

# ── Nginx ────────────────────────────────────────────────────────────────────

# /etc/nginx 의 파일은 0644/0755 라 읽기에는 sudo 가 필요 없다 — sudo 는 root 가 실제로
# 필요한 tee(쓰기)·nginx -t/-T(인증서 읽기)·systemctl reload 세 곳에만 쓴다.
# (sudo 실패가 "파일 없음"으로 오진되는 것도 함께 방지 — 후속 sudoers 축소의 명령 목록이 된다)

precheck_nginx() {
  # 전환 지점이 없는 상태에서 배포를 진행하면 구 색 정지 순간 중단이 난다 — 시작 전에 멈춘다.
  [ -f "$UPSTREAM_CONF" ] \
    || die "upstream 파일 없음: $UPSTREAM_CONF — Nginx 1회성 셋업(docs/39) 먼저"
  grep -q "bce_backend" "$SNIPPET" 2>/dev/null \
    || die "snippet($SNIPPET)이 upstream(bce_backend)을 참조하지 않는다 — Nginx 1회성 셋업(docs/39) 먼저"
  # upstream 을 우회해 백엔드 포트를 직접 가리키는 "실효" 설정이 있으면, 전환해도 그 경로는
  # 구 색에 남아 구 색 정지 순간 502 가 난다. 검사 대상은 nginx 가 실제로 include 하는 설정
  # 전체(-T 덤프)다 — 디렉터리 grep -r 은 .bak 백업·sites-available 비활성 파일·주석까지
  # 잡아 오탐으로 전 배포를 막는다(셋업 절차 자체가 스니펫 백업을 남긴다).
  # 덤프 실패는 반드시 die — 파이프에 넣으면 "검사 불가"가 "우회 없음"으로 fail-open 되어,
  # sudo 미설정 시 "컨테이너를 건드리기 전에 중단"이라는 이 함수의 계약이 깨진다.
  # 한계(6차 리뷰): "직접 proxy_pass" 만 탐지한다 — 별도 upstream 블록을 경유한 간접 우회는
  # 못 잡고, 반대로 무관한 서비스가 808[01] 을 쓰면 오탐한다. 오탐으로 배포가 막히면
  # 구성 확인 후 SKIP_NGINX_BYPASS_CHECK=1 로 이 검사만 우회할 수 있다.
  if [ "${SKIP_NGINX_BYPASS_CHECK:-0}" = "1" ]; then
    echo "::warning::[blue-green] SKIP_NGINX_BYPASS_CHECK=1 — 우회 proxy_pass 검사를 건너뛴다 (안전 게이트 비활성 흔적)"
  fi
  if [ "${SKIP_NGINX_BYPASS_CHECK:-0}" != "1" ]; then
    local dump
    dump=$(sudo nginx -T 2>/dev/null) \
      || die "sudo nginx -T 실패 — sudo 권한/Nginx 설정을 확인하라 (검사 불가 = 진행 불가)"
    # ^[[:space:]]* 앵커가 주석(# proxy_pass ...) 줄을 걸러낸다. loopback 의 모든 표기를 잡는다.
    if printf '%s\n' "$dump" | grep -qE '^[[:space:]]*proxy_pass[^;]*(127\.0\.0\.1|localhost|\[::1\]|0\.0\.0\.0):808[01]'; then
      die "upstream 을 우회해 8080/8081 을 직접 가리키는 실효 Nginx 설정이 있다 — 전환 불변식이 깨진다 (sudo nginx -T | grep proxy_pass 로 확인. 무관 서비스 오탐이면 SKIP_NGINX_BYPASS_CHECK=1)"
    fi
  fi
}

# upstream 파일의 bce_backend 블록에서 활성 포트를 읽는다(주석 속 IP 오독 방지 —
# 종단 게이트와 같은 블록 스코프). 실패는 삼켜 호출부가 원인을 말하게 한다(set -e 무언사 방지).
read_active_port() {
  awk '/upstream[[:space:]]+bce_backend/,/}/' "$UPSTREAM_CONF" 2>/dev/null \
    | grep -oE '127\.0\.0\.1:[0-9]+' | head -1 | cut -d: -f2 || true
}

active_color() {
  local port
  port=$(read_active_port)
  case "$port" in
    8080) echo blue ;;
    8081) echo green ;;
    *) die "upstream 파일에서 활성 포트를 읽지 못했다: '$port' ($UPSTREAM_CONF)" ;;
  esac
}

# upstream 파일 원자 교체 — tee 직접 쓰기는 truncate→write 사이 빈 파일 창이 생기고,
# 그 창에 certbot deploy hook 등 외부 reload 가 겹치면 bce_backend 미정의로 실패한다
# (서빙은 구 워커 유지로 안 죽지만 원인 불명 실패가 남는다). 같은 FS 의 mv 는 원자적.
# .tmp 가 nginx 에 안 읽히는 건 include 패턴이 conf.d/*.conf 라는 전제 위다(우분투 기본) —
# include conf.d/* 인 박스라면 .tmp 확장자도 로드되므로 이 전제가 깨진다.
# 실패는 명시 die — set -e 무언사로 죽으면 "원복 시도가 실패했다"는 사실이 로그에 안 남는다.
write_upstream() {
  printf '%s\n' "$1" | sudo tee "${UPSTREAM_CONF}.tmp" > /dev/null \
    || die "upstream 임시 파일 쓰기 실패: ${UPSTREAM_CONF}.tmp (sudo/디스크 확인)"
  sudo mv "${UPSTREAM_CONF}.tmp" "$UPSTREAM_CONF" \
    || die "upstream 파일 교체(mv) 실패 — ${UPSTREAM_CONF}.tmp 상태를 확인하라"
}

switch_nginx() {
  local port="$1" prev
  prev=$(cat "$UPSTREAM_CONF")
  # ⚠️ 아래 실패 문구가 "기존 색"이라 말하지 않는 이유: 이 함수는 revert 경로에서 중첩
  # 호출된다(ensure_target_alive_or_revert). 그 컨텍스트의 prev 는 "새 색을 가리키는 현재
  # 파일"이라, 항상 참인 서술은 "마지막 reload 성공 설정 그대로"뿐이다 (4차 리뷰).
  write_upstream "$(printf '# bce backend 활성 색 — 이 파일이 유일한 진실. 수정은 scripts/blue-green.sh 로만 (docs/39)\nupstream bce_backend { server 127.0.0.1:%s; }' "$port")"
  if ! sudo nginx -t; then
    # 문법 실패 시 원복 — 이 시점엔 reload 전이라 서빙 설정은 바뀐 게 없다.
    write_upstream "$prev"
    die "nginx -t 실패 — upstream 파일을 원복했다. 서빙은 마지막 reload 성공 설정 그대로다."
  fi
  # reload 실패도 원복해야 한다 — 파일만 새 대상을 가리키고 실서빙은 아닌 상태를 남기면
  # "활성 색의 유일한 진실 = upstream 파일" 불변식이 깨지고, 이후 switch 가
  # "이미 활성"이라며 복귀 경로까지 막는다.
  if ! sudo systemctl reload nginx; then
    write_upstream "$prev"
    sudo systemctl reload nginx || true
    die "nginx reload 실패 — upstream 파일을 원복했다. 서빙은 마지막 reload 성공 설정 그대로다."
  fi
  # 실효 설정 종단 게이트 — 이 함수 다음은 되돌릴 수 없는 구 색 정지다. "파일에 썼고
  # reload 가 성공했다"에 더해, -T 덤프의 "bce_backend 블록 안"에 대상 포트가 있는지
  # 최종 확인한다(블록 스코프 없이 전체 grep 하면 같은 포트를 쓰는 다른 upstream 이
  # 우연히 통과시킨다). 주의: -T 는 디스크 설정을 다시 읽는다 — 단독으로는 "reload 적용"의
  # 증거가 아니며, 위 reload exit code 검사와 결합해서만 의미가 있다.
  if ! sudo nginx -T 2>/dev/null | awk '/upstream[[:space:]]+bce_backend/,/}/' | grep -q "127.0.0.1:${port};"; then
    write_upstream "$prev"
    sudo systemctl reload nginx || true
    die "reload 후 실효 설정의 bce_backend 블록에 ${port} 가 없다 — 원복했다. 서빙은 마지막 reload 성공 설정 그대로다. Nginx 구성을 점검하라."
  fi
  # reload 는 무중단 — 신규 연결은 새 upstream, 진행 중 연결은 구 워커가 마무리한다.
  log "Nginx upstream → 127.0.0.1:${port} 전환 완료 (실효 설정 확인됨)"
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
      # 새 색 "스케줄러"는 기동 시점부터 시계가 돈다 — 기동 유예 게이트(SchedulerActivationGate,
      # 기본 300s > 이 데드라인 180s + 후속 창)가 실행을 막고 있어, 여기서 정지되는 색은
      # 부작용 0 으로 죽는다. (유예를 이 예산 밑으로 줄이면 그 보장이 깨진다 — docs/39)
      docker stop -t 5 "$ctr" > /dev/null 2>&1 || true
      die "새 색(${ctr})이 헬스체크를 통과하지 못했다 — 정지시켰다. 서빙은 기존 색 그대로, 기동 유예 게이트 덕에 스케줄러 부작용도 없다."
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

# 되돌릴 수 없는 구 색 정지 "직전"의 최종 생존 확인 — 전환 이후 어떤 이유로든(늦은 크래시,
# OOM 등) 새 색이 죽었다면, 아직 살아 있는 구 색으로 스위치를 되돌리고 중단한다.
# "설정이 새 색을 가리킨다"(종단 게이트)와 "새 색이 실제로 응답한다"는 다른 명제다.
ensure_target_alive_or_revert() {
  local target_port="$1" old_color="$2" target_ctr="$3" i old_port
  # 단발 프로브는 full GC·순간 포화를 "죽었다"로 오판한다 — 3회(간격 3s) 모두 실패해야 되돌린다.
  for i in 1 2 3; do
    if curl -fsS --connect-timeout 3 --max-time 5 "http://127.0.0.1:${target_port}/actuator/health" > /dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  # revert 전에 구 색 생존을 확인한다 — 구 색도 죽어 있으면(박스 압박으로 동시 OOM 등)
  # 되돌리는 순간 "restart 로 살아날 수 있던 새 색을 확정 정지 + 확실히 죽은 구 색을 지목"
  # = 100% 장애 고정이 된다. 둘 다 죽은 상황은 자동 판단으로 개선할 수 없다 — 사람을 부른다.
  old_port=$(port_of "$old_color")
  if ! curl -fsS --connect-timeout 3 --max-time 5 "http://127.0.0.1:${old_port}/actuator/health" > /dev/null 2>&1; then
    die "새 색(${target_port}) 3회 무응답 + 구 색(${old_port})도 무응답 — 자동 revert 를 중단한다(새 색의 restart 자가복구 가능성을 남김). 즉시 수동 개입 필요: status 로 상태 확인 후 살릴 색을 정해 switch 하라."
  fi
  switch_nginx "$old_port"
  # 죽었다고 판정한 새 색을 정지 — restart: unless-stopped 의 크래시 루프가 mem_limit 를
  # 물고 스케줄러까지 재실행하는 것을 차단(wait_healthy 실패 경로와 같은 계약).
  # docker logs 는 정지된 컨테이너에서도 되므로 진단은 잃지 않는다.
  docker stop -t 5 "$target_ctr" > /dev/null 2>&1 || true
  die "구 색 정지 직전 확인에서 새 색(${target_port})이 3회 무응답 — upstream 을 ${old_color} 로 되돌리고 ${target_ctr} 를 정지했다. 로그: docker logs ${target_ctr}"
}

warmup() {
  # JVM 콜드 스타트 완화용 프라이밍 — 실패해도 배포는 계속한다(헬스는 이미 통과).
  # 3회 전부 시도한다(첫 실패에서 끊으면 실질 1회가 된다). 효과는 보조적 — 판정 기준 아님.
  local port="$1" i
  for i in 1 2 3; do
    curl -fsS --max-time 5 "http://127.0.0.1:${port}/v1/buncheols" > /dev/null 2>&1 \
      || log "웜업 요청 ${i} 실패 (무해 — JIT 프라이밍 목적)"
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
    # stop_grace_period: 45s 가 컨테이너에 박혀 있어 docker stop 이 그대로 존중한다.
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

# 전환 성공 전에 죽으면(검증 실패·워크플로 취소·SIGTERM) 새로 띄운 색을 정리한다 —
# restart: unless-stopped 라 방치하면 실패한 JVM 이 최대 mem_limit 을 물고 무기한 남는다.
# 전환 성공 후에는 CLEANUP_CTR 를 비워 no-op 이 된다. 정리 후에도 비워 멱등(INT 핸들러의
# exit 가 EXIT 트랩을 다시 태우는 경로에서 이중 정지 방지).
#
# ⚠️ INT/TERM 핸들러는 반드시 exit 해야 한다 — bash 는 핸들러가 반환하면 중단 지점
# 다음부터 스크립트를 "계속 실행"한다. exit 없이 정리만 하면 "새 색을 정지시킨 손으로
# 그 색에 전환 → 구 색도 정지 = 전면 장애"가 된다(3차 리뷰). 종료 코드는 128+시그널 관례.
CLEANUP_CTR=""
cleanup_target() {
  if [ -n "$CLEANUP_CTR" ]; then
    docker stop -t 5 "$CLEANUP_CTR" > /dev/null 2>&1 || true
    log "중단 정리 — 전환 전이라 ${CLEANUP_CTR} 를 정지했다. 서빙은 기존 색 그대로다 (기동 유예 게이트 300s > 이 시점의 컨테이너 나이라 스케줄러 부작용도 없다 — docs/39)"
    CLEANUP_CTR=""
  fi
}
arm_cleanup() {
  CLEANUP_CTR="$1"
  trap cleanup_target EXIT
  trap 'cleanup_target; exit 130' INT
  trap 'cleanup_target; exit 143' TERM
  # HUP 도 트랩한다 — 문서가 권하는 긴급 롤백 경로가 "ssh 박스 → switch" 인데, ssh 세션이
  # 끊기면 오는 시그널이 정확히 HUP 이고, 트랩 없는 HUP 은 EXIT 트랩도 태우지 않는다.
  trap 'cleanup_target; exit 129' HUP
}

# 전환 "성공 이후" 드레인 구간의 트랩 — 여기서 취소되면 정리 대상은 새 색이 아니라 "구 색"이다
# (전환은 이미 끝났으므로 구 색 정지가 안전한 방향). 이게 없으면 드레인 30s 중의 취소가 구 색을
# 살려 둬, 다음 배포의 reconcile 전까지 두 인스턴스가 스케줄러·메모리·DB 풀을 동시 점유한다
# (7차 리뷰 — cancel-in-progress 는 롤백의 정상 동작이라 드문 경로가 아니다).
OLD_COLOR=""
finish_old_color() {
  if [ -n "$OLD_COLOR" ]; then
    stop_color "$OLD_COLOR"
    OLD_COLOR=""
  fi
}
arm_finish_old() {
  OLD_COLOR="$1"
  # set +e: 트랩 안에서 정리 명령이 실패하면 set -e 가 exit 128+N 에 닿기 전에 죽여
  # 의도한 종료 코드·로그가 사라진다 (8차 리뷰).
  trap 'set +e; finish_old_color; exit 130' INT
  trap 'set +e; finish_old_color; exit 143' TERM
  trap 'set +e; finish_old_color; exit 129' HUP
}
# revert 가능 구간(생존 확인) 직전에는 해제해야 한다 — 그 안에서 구 색으로 되돌 수 있다.
disarm_signals() { trap - INT TERM HUP; }

cmd_deploy() {
  # 수동 실행 편의 — 셸 env 가 없으면 compose 와 같은 곳(.env 의 별칭 기본값)을 읽는다.
  # 별칭(:staging/:prod)은 아래 pull 단계에서 관용 없이 항상 성공해야 통과한다.
  # sed -n 은 매치 없어도 exit 0 이지만 방어적으로 || true(§active_color 와 같은 원칙 —
  # 여기서 무언사하면 아래 die 의 안내에 영영 닿지 못한다). 따옴표·CR 은 벗긴다.
  if [ -z "${BACKEND_IMAGE:-}" ] && [ -f .env ]; then
    # compose 의 .env 파서와 결과가 갈리지 않게 인라인 주석·끝 공백·따옴표·CR 을 벗긴다
    # (안 벗기면 verify_image 의 "대상 이미지가 아니다"로 나와 원인 추적이 어렵다 — 5차 리뷰).
    BACKEND_IMAGE=$(sed -n 's/^BACKEND_IMAGE=//p' .env | tail -1 \
      | sed 's/[[:space:]]*#.*$//; s/[[:space:]]*$//' | tr -d "\r\"'" || true)
    export BACKEND_IMAGE
    if [ -n "$BACKEND_IMAGE" ]; then log "BACKEND_IMAGE 를 .env 기본값에서 읽음: $BACKEND_IMAGE"; fi
  fi
  [ -n "${BACKEND_IMAGE:-}" ] || die "BACKEND_IMAGE 미설정 — 배포할 이미지 좌표가 필요하다"
  acquire_lock
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

  # 화해(reconcile): upstream 이 가리키지 않는 색이 살아 있으면 이전 실행이 "전환 후·
  # 구 색 정지 전"에 끊긴 잔재다 — 정지하고 시작한다. 안 하면 그 색이 스케줄러를 돌리며
  # 메모리를 물고 남고, 같은 이미지 재배포 시 compose 가 낡은 컨테이너를 최신으로 판단해
  # 살려둔 채 헬스·이미지 대조를 통과시킨 뒤 그리로 전환한다 (5차 리뷰).
  if container_running "$target_ctr" \
    || { [ "$target" = "blue" ] && container_running "$LEGACY_CONTAINER"; }; then
    log "비활성 색(${target})이 실행 중 — 중단된 이전 실행의 잔재로 보고 graceful 정지 후 진행"
    stop_color "$target"
  fi

  # 박스는 pull 만 한다 — 빌드 부하 0 이 이 구조의 존재 이유(docs/38).
  # timeout 은 박스·네트워크 이상 시 잡이 매달리지 않게 하는 안전망(35 §11 교훈 유지).
  # pull 실패 관용은 sha- 불변 태그 + 로컬 존재일 때만 — "로컬 == 원격"은 불변 태그에서만
  # 정의상 보장된다(#89 4차 리뷰와 동일 근거). 이동 별칭(:staging/:prod)은 로컬이 낡은
  # 버전일 수 있어 pull 이 반드시 성공해야 한다(낡은 코드를 배포 성공으로 보고하는 사고 방지).
  # 관용 경로의 타임아웃은 1m — 레지스트리 무응답 시 5m 을 태우면 rollback.yml 의
  # 잡 예산(20m) 산식이 깨져 스위치 이후·구 색 정지 이전에 잡이 끊길 수 있다.
  case "$BACKEND_IMAGE" in
    *:sha-*)
      if docker image inspect "$BACKEND_IMAGE" > /dev/null 2>&1; then
        timeout -k 15s 1m docker compose -f "$COMPOSE_FILE" pull "backend-${target}" \
          || log "pull 실패 — 로컬 캐시 이미지로 진행 (불변 태그라 동일 이미지)"
      else
        timeout -k 30s 5m docker compose -f "$COMPOSE_FILE" pull "backend-${target}"
      fi
      ;;
    *)
      timeout -k 30s 5m docker compose -f "$COMPOSE_FILE" pull "backend-${target}"
      ;;
  esac
  # redis 는 색과 독립으로 상주시킨다 — "healthy" 면 절대 건드리지 않는다. 무조건
  # `up -d redis` 를 부르면 정의가 바뀐 배포에서 compose 가 redis 를 재생성해, --no-deps 로
  # 막으려던 "활성 색의 커넥션 단절"을 그 줄이 그대로 실행하게 된다 (5차 리뷰).
  # 판정은 running 이 아니라 healthy — 색 기동이 --no-deps 라 depends_on 의 healthy 조건이
  # 우회되므로 이 분기가 그 유일한 대체 게이트다. running-but-unhealthy(RDB 로드 중 등)를
  # 통과시키면 새 색이 커넥션 실패 상태로 부팅한다 (8차 리뷰 — 6차 --wait 의 잔여 구멍).
  # redis 정의 변경은 블루-그린으로 무중단이 안 되는 변경이다 — compose 헤더 참조.
  if ! container_healthy "buncheoleasy-redis"; then
    timeout -k 30s 2m docker compose -f "$COMPOSE_FILE" up -d --wait redis
  else
    log "redis healthy — 건드리지 않는다 (정의 변경 반영은 별도 정비 창에서)"
  fi
  # up 은 대상 색 서비스만 — 활성 색 컨테이너는 건드리지 않으므로 BACKEND_IMAGE 가
  # 바뀌어도 재생성되지 않는다. up 직후부터 전환 성공까지 trap 이 새 색을 책임진다.
  arm_cleanup "$target_ctr"
  timeout -k 30s 3m docker compose -f "$COMPOSE_FILE" up -d --no-deps "backend-${target}"

  wait_healthy "$target_port" "$target_ctr"
  verify_image "$target_ctr" "$BACKEND_IMAGE"
  warmup "$target_port"

  switch_nginx "$target_port"
  CLEANUP_CTR=""   # 전환 성공 — 새 색 정리 trap 은 no-op, 이제 취소 시 정리 대상은 구 색
  arm_finish_old "$active"

  log "드레인 ${DRAIN_SECONDS}s — 구 색으로 이미 프록시된 요청이 끝나기를 기다린다"
  sleep "$DRAIN_SECONDS"
  disarm_signals
  ensure_target_alive_or_revert "$target_port" "$active" "$target_ctr"
  # 프로브 통과 = 더 이상 revert 하지 않는다 — 여기서 취소되면 정리 대상은 다시 구 색이다
  # (프로브~정지 사이 취소가 구 색을 살려 두는 창 봉쇄 — 8차 리뷰).
  arm_finish_old "$active"
  stop_color "$active"
  OLD_COLOR=""

  echo "::notice::배포 완료 — 활성: ${target}(${target_port}). 1분 롤백: scripts/blue-green.sh switch ${active}"
}

cmd_switch() {
  local target="${1:-}" active target_port target_ctr
  case "$target" in blue|green) ;; *) die "사용법: blue-green.sh switch <blue|green>" ;; esac
  acquire_lock
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

  # 재기동한 색도 전환 성공 전 중단 시 정리한다(deploy 와 동일한 trap 계약).
  arm_cleanup "$target_ctr"
  start_color "$target"
  wait_healthy "$target_port" "$target_ctr"
  switch_nginx "$target_port"
  CLEANUP_CTR=""   # 전환 성공 — 취소 시 정리 대상은 구 색 (deploy 와 동일 계약)
  arm_finish_old "$active"
  log "드레인 ${DRAIN_SECONDS}s"
  sleep "$DRAIN_SECONDS"
  disarm_signals
  ensure_target_alive_or_revert "$target_port" "$active" "$target_ctr"
  # 프로브 통과 = 더 이상 revert 하지 않는다 — 여기서 취소되면 정리 대상은 다시 구 색이다
  # (프로브~정지 사이 취소가 구 색을 살려 두는 창 봉쇄 — 8차 리뷰).
  arm_finish_old "$active"
  stop_color "$active"
  OLD_COLOR=""
  local running_img
  running_img=$(docker inspect -f '{{.Config.Image}}' "$target_ctr" 2>/dev/null || echo "?")
  echo "::notice::전환 완료 — 활성: ${target}(${target_port}, ${running_img}). 복귀: scripts/blue-green.sh switch ${active}"
  # 워크플로 경로는 promote 잡이 별칭을 옮기지만 수동 switch 는 아무도 안 옮긴다 —
  # 이 상태에서 박스 .env 기본값(환경 별칭)으로 up/restart 하면 롤백이 조용히 원복된다.
  echo "::warning::ECR 별칭은 이동하지 않았다 — 지금 떠 있는 이미지(${running_img})와 환경 별칭(:staging/:prod)이 다를 수 있다. 별칭을 맞추기 전까지 박스에서 compose up/restart 금지 (docs/38 §5). 정식 복구는 rollback.yml 에 위 좌표를 지정하라."
}

cmd_status() {
  # 읽기 전용 진단 — precheck·락·die 없이 항상 끝까지 출력한다. upstream 이 깨졌거나
  # sudo 가 안 되는 상황이야말로 status 를 가장 보고 싶은 순간이다(3차 리뷰).
  local port p active="판독 불가 — upstream 파일/포트 확인 필요"
  port=$(read_active_port)
  case "$port" in
    8080) active="blue" ;;
    8081) active="green" ;;
    *) port="?" ;;
  esac
  echo "활성 색: ${active} (127.0.0.1:${port})"
  echo "--- upstream ---"
  cat "$UPSTREAM_CONF" 2>/dev/null || echo "(upstream 파일 없음/읽기 실패: $UPSTREAM_CONF — Nginx 셋업은 docs/39 §4)"
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
