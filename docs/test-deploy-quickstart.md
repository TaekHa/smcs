# SMCS 테스트 배포 퀵스타트 (단일 Linux PC + Docker)

> **목적**: 운영 서버가 아닌 **별도 Linux PC 한 대**에 Docker 로 SMCS 전체 스택을 띄워
> 사용자 테스트(Story 4.7 Phase 2 cycle)·데모·PoC 를 빠르게 진행한다.
>
> **범위**: 자가서명 SSL + 시드 데이터로 5~10분 내 기동. 외부 신뢰 인증서/도메인 불필요.
>
> **비범위**: Let's Encrypt 발급, 백업·복구, 운영 보안 하드닝, 모니터링 → 운영 배포는
> [`operations.md`](operations.md) 를 따른다. 본 문서는 운영용이 **아니다**.

이 스택은 Story 4.6 산출물(`docker/docker-compose.prod.yml` 4-service)을 그대로 쓰며,
자가서명 SSL 부속 절([`operations.md` §1.5 (d)](operations.md))을 퀵스타트 형태로 정리한 것이다.

---

## 0. 무엇이 뜨는가

`docker compose -f docker-compose.prod.yml` 하나로 4개 컨테이너가 뜬다:

| 컨테이너 | 이미지 | 역할 | 포트 |
| :------- | :----- | :--- | :--- |
| `smcs_postgres` | postgres:16-alpine | DB (volume 영속) | 내부 5432 |
| `smcs_backend` | 멀티스테이지 빌드(JDK21→JRE21, 비root uid 1000) | Spring API | 내부 8080 |
| `smcs_frontend` | 멀티스테이지 빌드(Node20→nginx alpine) | Vite SPA 정적 서빙 | 내부 80 |
| `smcs_nginx` | nginx:1.27-alpine | SSL 종단 + 리버스 프록시 | **호스트 80/443** |

nginx 가 `/api/*` → backend, 그 외 `/` → frontend 로 분기한다. HTTP(:80) 는 HTTPS(:443) 로
리다이렉트되므로 **브라우저 접속은 항상 `https://`** 다.

---

## 1. 사전 요구사항 (호스트 Linux PC)

- Ubuntu 22.04 LTS 권장(다른 배포판도 Docker 만 되면 가능)
- CPU/RAM: 2 vCPU / 4 GB 이상(빌드 + PDF 생성 여유)
- 디스크: 10 GB 이상 여유(이미지 빌드 + DB volume)
- 인바운드 포트: **80, 443** (테스트 클라이언트가 접근할 수 있어야 함)

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin openssl apache2-utils
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # 로그아웃 후 재로그인 필요(docker 그룹 적용)
```

> `apache2-utils` 는 시드 없이 ADMIN 을 직접 만들 때 쓰는 `htpasswd`(BCrypt) 용. 옵션 A 만
> 쓸 거면 생략 가능.

---

## 2. 5분 셋업

### 2.1 Repo clone + 디렉토리/시크릿

```bash
git clone https://github.com/TaekHa/smcs.git /opt/smcs
cd /opt/smcs/docker

# 첨부/로그 디렉토리 — backend 가 비root(uid 1000)로 동작하므로 owner 필수
sudo mkdir -p /data/smcs/files /var/log/smcs
sudo chown -R 1000:1000 /data/smcs/files /var/log/smcs

# .env 생성 + 시크릿 자동 채움
cp .env.template .env
chmod 600 .env
for v in JWT_SECRET DATA_KEY HMAC_KEY; do
  sed -i "s|SMCS_$v=.*|SMCS_$v=$(openssl rand -base64 32)|" .env
done
sed -i "s|POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -base64 24)|" .env
```

> backend 는 `SMCS_JWT_SECRET` / `SMCS_DATA_KEY` / `SMCS_HMAC_KEY` 셋 중 하나라도 비어 있으면
> 기동을 거부한다(fail-fast). 위 루프가 3개를 모두 채운다.

### 2.2 자가서명 SSL

```bash
sudo mkdir -p nginx/ssl
sudo openssl req -x509 -nodes -days 365 -newkey rsa:4096 \
  -keyout nginx/ssl/server.key \
  -out   nginx/ssl/server.crt \
  -subj "/CN=smcs.local"
# 운영 도메인이 정해져 있으면 CN= 값을 그 도메인으로 바꾼다(예: /CN=smcs.test.example.co.kr)
```

### 2.3 시드 데이터 (둘 중 택1)

prod 프로파일만으로는 시드가 비활성이라 DB 가 비어 로그인할 계정이 없다.

**옵션 A — 시드 자동(가장 빠름, 테스트 전용)**

```bash
# backend 프로파일에 local 추가 → LocalDataSeeder 가 8 user + 20 issue + 카테고리 키워드 생성
# .env 만 수정한다(추적 파일 compose 는 건드리지 않음 → 이후 git pull/checkout 충돌 없음).
sed -i 's|SPRING_PROFILES_ACTIVE=.*|SPRING_PROFILES_ACTIVE=prod,local|' .env
```

생성되는 시드 계정(비밀번호 전부 `dev1234`):

| 계정 | 역할 | 용도 |
| :--- | :--- | :--- |
| `agent1`, `agent2`, `agent3` | AGENT | 접수자 시나리오 |
| `field1` ~ `field4` | FIELD | 현장 작업자 시나리오 |
| `admin1` | ADMIN | 관리자 시나리오 |

> ⚠️ 시드 계정은 **테스트 전용**. 운영 전환 시 `prod` 단독으로 되돌리고 시드 계정을 삭제해야 한다(§5).

**옵션 B — 운영-등가(ADMIN 직접 생성 후 UI 로 사용자 추가)**

prod 단독 유지. 기동(§2.4) 후 첫 ADMIN 만 SQL 로 넣고, 나머지는 `/admin/users` 화면에서 생성한다
(Story 4.4 임시 비밀번호 흐름 + Story 4.7 사용자 cycle 을 동시에 운영-등가로 검증).

```bash
HASH=$(htpasswd -nbBC 10 "" "<원하는 비번>" | tr -d ':\n')
docker compose -f docker-compose.prod.yml exec postgres \
  psql -U smcs -d smcs -c \
  "INSERT INTO users (username,password_hash,display_name,role,active) VALUES ('admin','$HASH','초기관리자','ADMIN',true);"
```

### 2.4 빌드 + 기동

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps    # 4개 모두 (healthy) 될 때까지 대기
```

최초 빌드는 backend Gradle + frontend Vite 컴파일 때문에 수 분 걸린다(이후는 캐시).
postgres ≤ 30s, backend ≤ 60s 내 `(healthy)` 도달이 정상.

### 2.5 클라이언트에서 접속

테스트할 PC(브라우저 쓰는 쪽)의 hosts 파일에 서버 IP 매핑 추가:

- Linux/macOS: `/etc/hosts`
- Windows: `C:\Windows\System32\drivers\etc\hosts` (관리자 권한)

```
<서버 IP>   smcs.local
```

브라우저에서 **`https://smcs.local`** 접속 → 자가서명 경고 → "고급" → 계속 진행.
시드 계정(`admin1` / `dev1234`)으로 로그인.

---

## 3. 동작 검증 체크리스트

```bash
# 컨테이너 상태
docker compose -f docker-compose.prod.yml ps

# backend health (컨테이너 내부 — 항상 200)
docker compose -f docker-compose.prod.yml exec nginx \
  wget -qO- http://backend:8080/actuator/health        # {"status":"UP"}

# 외부 경로(자가서명이라 -k 로 인증서 검증 생략)
curl -fsSk https://smcs.local/api/actuator/health       # {"status":"UP"}
```

브라우저 골든 패스(Story 4.7):
- [ ] `admin1` 로그인 → 대시보드 KPI/차트 표시
- [ ] `agent1` 로그인 → 이슈 목록 "신규 등록" 버튼(SW-008) → 등록 폼 → 카테고리 자동 제안(SW-003 시드 키워드)
- [ ] `field1` 모바일 → 배정된 이슈 → 사진 첨부 → 조치 코멘트 → "완료 처리"(SW-001)
- [ ] 키보드만으로 로그인 → `N` 단축키(SW-002) → 등록 → Ctrl+S 저장(AC6)

---

## 4. 자주 막히는 곳

| 증상 | 원인 / 해결 |
| :--- | :--- |
| backend 컨테이너가 즉시 죽음(restarting) | `.env` 시크릿 3개 중 누락 → fail-fast. `docker compose ... logs backend` 확인 후 §2.1 재실행 |
| backend 가 `(unhealthy)` 로 멈춤 | postgres 가 아직 안 떴거나 DATASOURCE 비번 불일치. `logs postgres` + `.env` `POSTGRES_PASSWORD` 확인 |
| 첨부 업로드 시 500 | (1) 마운트 디렉터리 권한: `sudo chown -R 1000:1000 /data/smcs/files`. (2) `logs backend` 가 `AccessDeniedException: /app/var` 면 backend 가 마운트 대신 컨테이너 작업디렉터리에 쓰는 것 — compose backend `environment` 에 `SMCS_FILES_DIR: /var/smcs/files` 핀이 있는지 확인(없는 구버전이면 `git pull` 후 `up -d` 재기동). 옵션 A(`prod,local`)에서 이 핀이 없으면 `local` 프로파일의 상대경로(`./var`)가 우선해 실패한다(UT-002). |
| 로그인 시 계정 없음 | 시드 미적용. 옵션 A(`prod,local`) 적용했는지 또는 옵션 B INSERT 했는지 확인. **이미 한 번 떠서 users 테이블이 비어있지 않으면 시드는 skip** 됨 → 깨끗이 하려면 §5 의 volume 삭제 후 재기동 |
| 임시 비밀번호 "복사" 버튼 무동작 | 자가서명을 단순 "경고 무시"만 하면 `navigator.clipboard` 가 secure context 가 아니라 비활성. `server.crt` 를 클라이언트 OS 신뢰 저장소에 임포트하면 해결. 임포트 전에는 평문을 수동 선택+복사로 우회 |
| 포트 80/443 충돌 | 호스트에 다른 웹서버가 점유 중. 해당 서비스 중지 또는 compose 의 nginx `ports` 매핑 변경 |
| 빌드 중 메모리 부족 | RAM 부족(특히 frontend Vite). swap 추가 또는 4GB+ 호스트 사용 |

로그 보기:

```bash
docker compose -f docker-compose.prod.yml logs -f backend     # 특정 서비스 follow
docker compose -f docker-compose.prod.yml logs --tail=100      # 전체 최근 100줄
```

---

## 5. 컨테이너 관리 / 정리

```bash
# 재시작(코드 변경 후 재빌드)
git -C /opt/smcs pull
docker compose -f docker-compose.prod.yml up -d --build

# 중지(데이터 보존)
docker compose -f docker-compose.prod.yml down

# 완전 초기화(DB volume 까지 삭제 — 시드부터 다시)
docker compose -f docker-compose.prod.yml down -v
```

> `down -v` 는 `smcs_pg_data` volume 을 지워 **모든 DB 데이터가 사라진다**. 시드를 깨끗이
> 다시 넣고 싶을 때만 사용.

---

## 6. 테스트 → 운영 전환 시

본 퀵스타트로 검증을 마치고 실제 운영에 올린다면 다음을 **반드시** 교체한다:

1. **SSL**: 자가서명 → Let's Encrypt 발급([`operations.md` §1.5 (a)/(b)/(c)](operations.md))
2. **프로파일**: 옵션 A 를 썼다면 `SPRING_PROFILES_ACTIVE` 를 `prod` 단독으로 복귀
3. **시드 계정 제거**: `agent1`~`admin1` 비활성화/삭제, 신규 ADMIN 발급
4. **백업 cron**: `operations.md` §3(pg_dump + 첨부 rsync) 설정
5. **cleanup cron**: 안정화 후 `SMCS_REPORTS_CLEANUP_CRON` 을 실제 스케줄(`0 0 3 * * *`)로 활성

운영 배포의 정본은 항상 [`operations.md`](operations.md) 다.
