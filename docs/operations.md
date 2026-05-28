# SMCS 운영 가이드

> Story 4.6 운영 배포 + 백업 가이드. 사내 서버 단일 호스트 docker compose 배포 기준.
> 대상: 운영자 1인. 모든 명령은 운영 서버 SSH 세션에서 실행한다는 가정.

---

## 1. 사전 준비

### 1.1 서버 사양 권장

- **OS**: Ubuntu 22.04 LTS (Docker 공식 지원, 2027년까지 LTS) — PO v0.3 비준
- **CPU/RAM**: 2 vCPU / 4 GB 최소 (사용자 70명 동시 접속 + PDF 생성 + 백업 여유)
- **디스크**: 시스템 디스크 30 GB + **별도 데이터 디스크** (`/data/`) 50 GB 이상
- **네트워크**: TCP 80/443 인바운드, TCP 5432/8080 차단(컨테이너 내부 통신)

### 1.2 호스트 패키지 설치 (Ubuntu 22.04)

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin certbot rsync curl
sudo systemctl enable --now docker
sudo usermod -aG docker $USER  # 로그아웃 후 재로그인 필요
```

### 1.3 데이터/백업 디렉토리 준비

별도 데이터 파티션에 첨부 + 백업 디렉토리를 생성. 컨테이너 backend는 `uid 1000`(비root)로 동작하므로 owner 권한 필수.

```bash
# 첨부 디렉토리 (별도 데이터 디스크 마운트 권장)
sudo mkdir -p /data/smcs/files
sudo chown -R 1000:1000 /data/smcs/files

# 로그 디렉토리 (선택, 현재 docker logs 만 사용 — Deviation #9, v2 file appender 도입 시 활성)
sudo mkdir -p /var/log/smcs
sudo chown -R 1000:1000 /var/log/smcs

# 백업 디렉토리 (동일 서버 로컬 — PO Deviation #3 v0.2 비준)
sudo mkdir -p /backup/db /backup/files
sudo chown -R root:root /backup
```

별도 데이터 파티션 마운트 예시 `/etc/fstab` 라인:

```
UUID=<별도 디스크 UUID>  /data  ext4  defaults,noatime  0 2
```

### 1.4 환경변수 + 시크릿 생성

```bash
git clone <리포지토리 URL> /opt/smcs
cd /opt/smcs/docker
cp .env.template .env
chmod 600 .env  # 시크릿 보호
```

`/opt/smcs/docker/.env` 편집 (필수 항목):

- `POSTGRES_PASSWORD` — postgres user 비밀번호 (Spring datasource password 와 단일 SoT)
- `SMCS_JWT_SECRET`, `SMCS_DATA_KEY`, `SMCS_HMAC_KEY` — **각각** `openssl rand -base64 32` 로 생성한 서로 다른 32 byte base64 값

```bash
# 시크릿 3개 동시 생성 예시 (편집 시 그대로 복붙)
for v in JWT_SECRET DATA_KEY HMAC_KEY; do
  printf 'SMCS_%s=%s\n' "$v" "$(openssl rand -base64 32)"
done
```

3 시크릿 모두 누락 시 backend `@PostConstruct` 검증에서 startup 실패합니다(CI lesson #3, fail-fast 보장).

### 1.5 SSL 인증서 — Let's Encrypt dns-01 (PO v0.2 비준)

PO 결정: **Let's Encrypt dns-01 challenge** (무료 + 90일 자동 갱신, 내부망 도메인 가능).

**선결 협의 항목** (운영자 ↔ 인프라/IT 팀):
- (a) 운영 서버 도메인 — 예: `smcs.example.co.kr`
- (b) **DNS provider + API 자격증명**

DNS provider 별 certbot 플러그인 + 발급 명령:

#### (a) Cloudflare

```bash
sudo apt install -y python3-certbot-dns-cloudflare

# API token (Zone:DNS:Edit 권한) 저장
sudo install -m 600 -o root -g root /dev/null /etc/letsencrypt/cloudflare.ini
sudo tee /etc/letsencrypt/cloudflare.ini >/dev/null <<EOF
dns_cloudflare_api_token = <API_TOKEN>
EOF

sudo certbot certonly \
  --dns-cloudflare \
  --dns-cloudflare-credentials /etc/letsencrypt/cloudflare.ini \
  -d smcs.example.co.kr \
  --agree-tos -m ops@example.co.kr --non-interactive
```

#### (b) AWS Route 53

```bash
sudo apt install -y python3-certbot-dns-route53

# IAM 키 (Route 53 ChangeResourceRecordSets + ListHostedZones 권한)를
# /root/.aws/credentials 또는 환경변수로 설정한 뒤:
sudo certbot certonly \
  --dns-route53 \
  -d smcs.example.co.kr \
  --agree-tos -m ops@example.co.kr --non-interactive
```

#### (c) 사내 BIND (rfc2136 + nsupdate)

```bash
sudo apt install -y python3-certbot-dns-rfc2136

sudo install -m 600 -o root -g root /dev/null /etc/letsencrypt/rfc2136.ini
sudo tee /etc/letsencrypt/rfc2136.ini >/dev/null <<EOF
dns_rfc2136_server = <DNS서버 IP>
dns_rfc2136_port = 53
dns_rfc2136_name = <TSIG 키 이름>
dns_rfc2136_secret = <TSIG 비밀>
dns_rfc2136_algorithm = HMAC-SHA512
EOF

sudo certbot certonly \
  --dns-rfc2136 \
  --dns-rfc2136-credentials /etc/letsencrypt/rfc2136.ini \
  -d smcs.example.co.kr \
  --agree-tos -m ops@example.co.kr --non-interactive
```

#### 인증서를 nginx 컨테이너에 연결

발급 결과는 `/etc/letsencrypt/live/<도메인>/{fullchain.pem,privkey.pem}` 에 저장됩니다. nginx.conf 는 `/etc/nginx/ssl/server.crt` + `server.key` 를 참조하므로 두 방식 중 하나:

```bash
# 옵션 A — 심볼릭 링크 (간단)
sudo mkdir -p /opt/smcs/docker/nginx/ssl
sudo ln -sf /etc/letsencrypt/live/smcs.example.co.kr/fullchain.pem \
            /opt/smcs/docker/nginx/ssl/server.crt
sudo ln -sf /etc/letsencrypt/live/smcs.example.co.kr/privkey.pem \
            /opt/smcs/docker/nginx/ssl/server.key

# 옵션 B — docker-compose.prod.yml 의 volume 을 /etc/letsencrypt 직접 마운트
#   - ./nginx/ssl:/etc/nginx/ssl:ro  →  - /etc/letsencrypt:/etc/letsencrypt:ro
# 그리고 nginx.conf 의 ssl_certificate 경로를 fullchain.pem 으로 수정
```

자동 갱신 cron (월 1회 04:00 KST, 백업 시간 분산):

```bash
sudo crontab -e
# 다음 줄 추가:
0 4 1 * * /usr/bin/certbot renew --quiet --post-hook 'docker compose -f /opt/smcs/docker/docker-compose.prod.yml exec nginx nginx -s reload'
```

#### (d) 자가서명 — 테스트/스테이징 전용 (운영 금지)

> Story 4.7 Phase 2 사용자 cycle 환경 또는 사내 격리망 PoC 처럼 **외부 신뢰 인증서가 불가능하거나 불필요한 경우의 임시 부트스트랩**. 운영 배포에서는 반드시 (a)/(b)/(c) Let's Encrypt 발급으로 교체.

```bash
# 1) 자가서명 인증서 생성 (1년 유효)
sudo mkdir -p /opt/smcs/docker/nginx/ssl
sudo openssl req -x509 -nodes -days 365 -newkey rsa:4096 \
  -keyout /opt/smcs/docker/nginx/ssl/server.key \
  -out   /opt/smcs/docker/nginx/ssl/server.crt \
  -subj "/CN=smcs.local"
# 운영 도메인이 정해져 있다면 CN= 값을 그 도메인으로(예: /CN=smcs.test.example.co.kr)
```

```bash
# 2) 클라이언트 PC /etc/hosts (Windows: C:\Windows\System32\drivers\etc\hosts) 에 추가
<운영 서버 IP>  smcs.local
```

브라우저는 자가서명 인증서를 신뢰하지 않으므로 첫 접속 시 경고 화면이 뜹니다. 두 가지 처리법:

- **간단**: 경고 화면 → "고급" → "안전하지 않음으로 이동" — 매 세션 1회 클릭(테스트용으로만 허용)
- **권장(여러 테스트 사용자 환경)**: `server.crt` 를 각 클라이언트의 OS 신뢰 저장소에 임포트(Windows: 인증서 관리자 → 신뢰할 수 있는 루트 인증 기관 / macOS: 키체인 → System / Ubuntu: `/usr/local/share/ca-certificates/` + `update-ca-certificates`). 임포트 후 경고 사라짐.

**Story 4.7 Phase 2 사용자 cycle 추가 셋업** (시드 사용자 활성화 — 테스트 전용):

prod profile 만으로는 `LocalDataSeeder` 가 비활성 → DB 가 비어 있어 `agent1`/`field1`/`admin1` 로그인 불가. 두 옵션:

- **옵션 A (간단)**: `docker-compose.prod.yml` 의 `backend.environment.SPRING_PROFILES_ACTIVE` 를 `prod` → `prod,local` 로 수정 → 시드 자동 생성(8 user + 20 issue + L1~L3 카테고리 키워드). **테스트 종료 후 운영 전환 시 반드시 `prod` 단독으로 복귀** + 시드 사용자 비활성화/삭제.
- **옵션 B (운영-등가)**: prod 프로파일 유지 + 첫 ADMIN 만 SQL 로 직접 INSERT → ADMIN UI(`/admin/users`) 로 AGENT/FIELD 정상 생성(임시 비밀번호 평문 1회 응답 정상 검증 = AC2/AC8 운영-등가 cycle). Story 4.4 의 임시비번 + Story 4.7 의 사용자 cycle 동시 검증 가능.

옵션 B INSERT 예시(`apache2-utils` 의 `htpasswd` 가 BCrypt hash 를 한 줄로 생성. `dev1234` 와 다른 보호 비번 사용 권장):

```bash
sudo apt install -y apache2-utils   # htpasswd 미설치 시
HASH=$(htpasswd -nbBC 10 "" "<원하는 비번>" | tr -d ':\n')
docker compose -f /opt/smcs/docker/docker-compose.prod.yml exec postgres \
  psql -U smcs -d smcs -c \
  "INSERT INTO users (username,password_hash,display_name,role,active) VALUES ('admin','$HASH','초기관리자','ADMIN',true);"
# 이후 https://smcs.local → admin/<원하는 비번> 로 로그인 → /admin/users 에서 AGENT/FIELD 정상 생성
```

자가서명 환경에서 동작하지 않는 기능 1건(알려진 한계):
- **클립보드 복사**(Story 4.4 임시비밀번호 모달의 "복사" 버튼) — 브라우저가 `navigator.clipboard` 를 secure context(HTTPS + 신뢰 인증서)에서만 노출. 자가서명을 신뢰 저장소에 임포트(권장 방법)하면 정상 동작. 임포트 없이 경고 무시만 한 상태에서는 동작하지 않음 — 평문 monospace 표시는 정상이므로 수동 선택+복사로 우회 가능. Story 4.4 QA 잔여 관찰 #1 (clipboard HTTPS 보장) 가 자가서명 환경에서 신뢰 저장소 임포트로 해결됨을 명시.

---

## 2. 첫 배포

```bash
cd /opt/smcs/docker
# .env 작성 + SSL 인증서 ssl/ 디렉토리 연결 완료 가정
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

모든 컨테이너가 `(healthy)` 상태가 될 때까지 대기 (postgres ≤ 30s, backend ≤ 60s).

헬스체크 확인:

```bash
# 내부 (컨테이너간) — 항상 200
docker compose -f docker-compose.prod.yml exec nginx \
  wget -qO- http://backend:8080/actuator/health

# 외부 (운영 도메인) — 인증서 + nginx 동작 확인
curl -fsS https://smcs.example.co.kr/api/actuator/health
# 기대: {"status":"UP"}
```

### 2.1 admin1 초기 비밀번호 정책 (Story 4.4 AC8 우회)

비밀번호 reset endpoint 가 미구현(v2)이므로 첫 로그인 후 **admin1 비활성화 + 신규 admin 생성** 절차로 우회합니다.

1. 브라우저 → `https://smcs.example.co.kr` → 기본 자격증명 (`admin1` / 첫 배포 임시 비밀번호) 로그인
2. `/admin/users` → "사용자 추가" → 신규 ADMIN 계정 1개 생성 → **반환된 임시 비밀번호 즉시 기록**
3. 로그아웃 → 신규 ADMIN 계정으로 로그인 → `/admin/users` → `admin1` 행 **비활성화** (또는 삭제)
4. 신규 ADMIN 임시 비밀번호로 로그인한 채 v2 에서 reset 기능 도입까지 사용

---

## 3. 재기동 / 업데이트

```bash
cd /opt/smcs/docker

# 코드 변경 없이 컨테이너 재기동
docker compose -f docker-compose.prod.yml restart backend

# 새 코드 pull + 이미지 재빌드 + 무중단 재기동
git -C /opt/smcs pull
docker compose -f docker-compose.prod.yml up -d --build --no-deps backend frontend

# nginx 설정만 갱신
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload

# 전체 재기동 (점검 시간)
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d
```

---

## 4. 백업 / 복원

> ⚠️ **DR (Disaster Recovery) 경고 — PO Deviation #3 v0.2**
>
> 현재 백업 저장소는 **운영 서버와 동일한 호스트의 로컬 디스크(`/backup/`)** 입니다.
> 서버 디스크가 손실되거나 화재/물리 사고가 발생하면 DB 백업과 첨부 백업이 **동시에 분실**됩니다.
> **`/backup`을 OS root 와 다른 물리 디스크에 마운트**하면 단일 디스크 장애 일부 보호가 가능합니다.
> **v2 에서 별도 NAS 마운트 또는 S3 동기화를 강력 권장**합니다.

### 4.1 자동 백업 cron 등록

`scripts/backup-*.sh` 는 운영 서버 호스트에서 cron 실행을 가정합니다.

```bash
sudo crontab -e
# 다음 두 줄 추가:
0 3 * * * /opt/smcs/scripts/backup-db.sh    >> /var/log/smcs/backup-db.log 2>&1
5 3 * * * /opt/smcs/scripts/backup-files.sh >> /var/log/smcs/backup-files.log 2>&1
```

5분 간격은 동시 실행 부하 회피. 03:00 KST 는 보고서 cleanup-cron 과 동일 시각이지만 활성화 일정이 다릅니다 (§6 점진 활성화).

### 4.2 수동 백업 실행

```bash
/opt/smcs/scripts/backup-db.sh
# → /backup/db/smcs-db-YYYY-MM-DD.dump 생성, 30일 이전 파일 자동 정리

/opt/smcs/scripts/backup-files.sh
# → /backup/files/YYYY-MM-DD/ 디렉토리 (rsync) 또는
#   /backup/files/smcs-files-YYYY-MM-DD.tar.gz (rsync 미설치 시 폴백)
```

### 4.3 백업 무결성 검증 (월 1회 권장)

```bash
# DB dump 헤더 검증 (실제 복원 없이 구조만 확인)
docker exec -i smcs_postgres pg_restore --list < /backup/db/smcs-db-YYYY-MM-DD.dump | head

# 첨부 백업 크기 확인 (직전일과 큰 차이 없는지)
du -sh /backup/files/$(date +%F)/ 2>/dev/null \
   || ls -lh /backup/files/smcs-files-$(date +%F).tar.gz
```

### 4.4 복원 (장애 발생 시)

```bash
cd /opt/smcs/docker

# 1) backend 정지 (DB 일관성 보호)
docker compose -f docker-compose.prod.yml stop backend

# 2) 복원 실행 (--clean --if-exists 로 기존 스키마 drop 후 재생성)
/opt/smcs/scripts/restore-db.sh /backup/db/smcs-db-YYYY-MM-DD.dump

# 3) 첨부 디렉토리 복원 (rsync 모드 백업의 경우)
sudo rsync -a --delete /backup/files/YYYY-MM-DD/ /data/smcs/files/
sudo chown -R 1000:1000 /data/smcs/files

# 4) backend 재기동
docker compose -f docker-compose.prod.yml start backend

# 5) 헬스체크 + 수동 로그인 회귀
curl -fsS https://smcs.example.co.kr/api/actuator/health
```

---

## 5. 로그

기본 로그 드라이버 = `json-file` (Deviation #9). max-size 100MB × max-file 5 = 컨테이너당 최대 500MB.

```bash
# 실시간 tail
docker compose -f /opt/smcs/docker/docker-compose.prod.yml logs -f backend
docker compose -f /opt/smcs/docker/docker-compose.prod.yml logs -f nginx

# 최근 1000줄
docker compose -f /opt/smcs/docker/docker-compose.prod.yml logs --tail=1000 backend

# 디스크 사용량
docker inspect smcs_backend --format='{{.LogPath}}' | xargs ls -lh
```

호스트 마운트 `/var/log/smcs/` 는 backend file appender 가 도입되는 v2 단계에서 활성화됩니다. 현재 MVP 는 docker logs 만 사용합니다.

---

## 6. 점진 활성화 — Reports cleanup-cron (Story 3.5 lesson)

첫 배포 시 `.env` 의 `SMCS_REPORTS_CLEANUP_CRON` 기본값은 `0 0 5 31 2 *` (Feb 31 — **도달 불가능 날짜**) 입니다. 보고서 보관/삭제 동작 검증 전에는 의도적으로 비활성화 상태입니다.

**활성화 절차** (배포 후 ~1주, 보관함에 일간/주간 보고서가 정상 누적된 뒤):

1. 보관함 확인 — `/reports` 에서 자동 생성된 일간/주간 보고서 7+1개 이상 존재 확인
2. `.env` 수정:

   ```
   SMCS_REPORTS_CLEANUP_CRON=0 0 3 * * *
   ```

   (매일 03:00 KST 실행, 90일 초과 보관 보고서 삭제)

3. backend 재기동:

   ```bash
   docker compose -f /opt/smcs/docker/docker-compose.prod.yml up -d --no-deps backend
   ```

4. 다음 03:00 이후 `docker logs smcs_backend | grep cleanup` 로 실행 확인

---

## 7. 트러블슈팅

### 7.1 헬스체크 실패 (`/actuator/health` → 503 또는 timeout)

```bash
docker compose -f /opt/smcs/docker/docker-compose.prod.yml ps
docker compose -f /opt/smcs/docker/docker-compose.prod.yml logs backend | tail -50
```

흔한 원인:
- **DB 연결 실패** — postgres 컨테이너가 healthy 아님. `docker compose logs postgres` 확인. `POSTGRES_PASSWORD` 불일치 (.env vs 첫 init 비밀번호).
- **secret 누락** — `SMCS_JWT_SECRET` / `SMCS_DATA_KEY` / `SMCS_HMAC_KEY` 중 하나라도 비어 있으면 backend startup 거부. 로그에 `IllegalStateException: SMCS_*_SECRET is required` 출력.
- **Flyway 마이그레이션 실패** — 스키마 손상. `docker logs smcs_backend | grep -i flyway` 후 마지막 적용 버전 확인 → DBA 협의.

### 7.2 모든 API 가 401 Unauthorized

JWT secret 변경 직후라면 **기존 발급 토큰 전부 무효화** 됩니다. 정상 동작. 사용자는 재로그인 필요.

JWT secret 변경 없이 401 이 발생하면:
- `system clock skew` — 컨테이너 시간이 어긋남. 호스트 NTP 동기화 확인 (`timedatectl status`).
- nginx 가 `Authorization` 헤더를 제거하지 않는지 — `docker compose exec nginx nginx -T | grep proxy_set_header` 로 헤더 전달 룰 확인 (`Authorization` 은 nginx 기본 전달).

### 7.3 첨부 업로드 실패 (413 또는 500)

- **413 Request Entity Too Large** — 10MB 초과 (application.yml `max-file-size: 10MB`). nginx `client_max_body_size 15m` 도 한계. 더 큰 파일이 필요하면 양쪽 모두 조정.
- **500 Internal Server Error** — `/data/smcs/files` 디렉토리 권한 문제. `ls -la /data/smcs/files` → owner 가 `1000:1000` 인지 확인. 아니면 `sudo chown -R 1000:1000 /data/smcs/files`.
- **디스크 가득** — `df -h /data` 확인.

### 7.4 SSL 인증서 만료 (Let's Encrypt)

```bash
# 만료 임박 여부
sudo certbot certificates | grep -E 'Domains|Expiry'

# 강제 갱신 (90일 주기 자동 갱신이 멈춘 경우)
sudo certbot renew --force-renewal
docker compose -f /opt/smcs/docker/docker-compose.prod.yml exec nginx nginx -s reload
```

### 7.5 컨테이너 안에서 명령 실행 (디버깅)

```bash
docker compose -f /opt/smcs/docker/docker-compose.prod.yml exec backend bash
docker compose -f /opt/smcs/docker/docker-compose.prod.yml exec postgres psql -U smcs -d smcs
docker compose -f /opt/smcs/docker/docker-compose.prod.yml exec nginx nginx -t
```
