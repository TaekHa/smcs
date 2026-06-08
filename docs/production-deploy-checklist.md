# SMCS 운영 배포 사전 점검 체크리스트

> 실 서버에 SMCS 를 올리기 **전·중·후** 확인할 항목. 상세 절차/명령은 [`operations.md`](operations.md) 가 SoT 이며, 본 문서는 빠뜨리기 쉬운 것을 체크박스로 잡는 **게이트**다.
>
> 테스트 배포([`test-deploy-quickstart.md`](test-deploy-quickstart.md))와의 핵심 차이 = **실 인증서 / `prod` 단독 프로파일(시드 OFF) / 시드 계정 제거 / 백업 / cleanup cron**.

---

## 0. 사전 결정 (블로킹 — 정해지기 전엔 배포 불가)

- [ ] **운영 도메인** 확정 (예: `smcs.example.co.kr`)
- [ ] **DNS provider** 확정 — Cloudflare / Route 53 / 사내 BIND 중 (§1.5 인증서 방식이 갈림)
- [ ] **서버** 확보 — Ubuntu 22.04 LTS, 2 vCPU / 4 GB+ / 디스크 20 GB+ (operations.md §1.1)
- [ ] **첨부 데이터 디스크** 위치 결정 (`/data/smcs/files` 또는 별도 마운트)
- [ ] **백업 정책 인지** — 현재 동일 서버 로컬(`/backup/`)만 = **DR 단일 장애점** (PO Deviation #3). 오프사이트(NAS/S3)는 v2 — 이 리스크를 운영 주체가 수용했는지 확인

## 1. 서버 준비 (operations.md §1.1~1.3)

- [ ] Docker + compose plugin + openssl 설치, `docker` 그룹 적용
- [ ] 디렉토리 생성 + **소유권 `chown -R 1000:1000`** (비root uid 1000 컨테이너 쓰기용):
  - [ ] `/data/smcs/files` (첨부)
  - [ ] `/var/log/smcs` (선택)
  - [ ] `/backup/db`, `/backup/files`
- [ ] 인바운드 방화벽 **80, 443** 오픈 (테스트 클라이언트/사용자 접근)
- [ ] repo clone (`/opt/smcs`)

## 2. 환경변수 + 시크릿 (operations.md §1.4)

- [ ] `cp .env.template .env` + `chmod 600 .env`
- [ ] **시크릿 3개** 실제 값 주입 (`openssl rand -base64 32`): `SMCS_JWT_SECRET` / `SMCS_DATA_KEY` / `SMCS_HMAC_KEY` — 하나라도 비면 backend fail-fast (lesson #3)
- [ ] `POSTGRES_PASSWORD` 강한 값으로 설정
- [ ] ⭐ **`SPRING_PROFILES_ACTIVE=prod`** (테스트의 `prod,local` 이 **아님** — 시드 데이터 OFF)
- [ ] `SMCS_FILES_DIR` / `SMCS_LOGS_DIR` 호스트 경로가 §1.3 에서 chown 한 경로와 일치
- [ ] cron 기본값 확인: `SMCS_REPORTS_CLEANUP_CRON=0 0 5 31 2 *` (Feb31 = 의도적 비활성, §6 에서 나중에 활성화)

## 3. SSL 인증서 (operations.md §1.5)

- [ ] **Let's Encrypt dns-01** 발급 (provider별 절차 §1.5) — 또는 운영 정책상 사내 CA
- [ ] `nginx/ssl/server.crt` + `server.key` 연결(심볼릭 링크 또는 직접 마운트)
- [ ] 인증서 **자동 갱신**(certbot renew) 등록 — 90일 만료 전 갱신 (§7.4)
- [ ] (자가서명으로 임시 운영 시) clipboard·secure-context 기능 제한 인지 — 가능하면 실 인증서 권장

## 4. 첫 배포 + 헬스체크 (operations.md §2)

- [ ] `docker compose -f docker-compose.prod.yml up -d --build`
- [ ] 4개 컨테이너 모두 **`(healthy)`** (postgres ≤30s, backend ≤60s)
- [ ] 내부 헬스: `exec nginx wget -qO- http://backend:8080/actuator/health` → `{"status":"UP"}`
- [ ] 외부 헬스: `curl -fsS https://<도메인>/api/actuator/health` → `{"status":"UP"}` (인증서·nginx 정상)

## 5. 운영 하드닝 — 배포 직후 (operations.md §2.1)

- [ ] `prod` 단독이라 **시드 계정(agent1/field1/admin1)이 없어야** 정상 — 로그인 안 되는 게 맞음
- [ ] 첫 ADMIN 부트스트랩: `htpasswd` BCrypt 로 초기 admin SQL INSERT (operations.md §1.5 말미 / §2.1)
- [ ] 첫 ADMIN 로그인 → `/admin/users` 에서 **실 운영 ADMIN 생성** → 임시 비밀번호 **즉시 기록**
- [ ] 초기/부트스트랩 admin **비활성화** (비밀번호 reset 미구현 v2 → 비활성화+신규 우회)

## 6. 기능 스모크 테스트 — 실 브라우저 (Phase 2 cycle 에서 깨졌던 경로 우선)

> 자동 테스트·CI 가 못 잡는 **nginx 뒤 실배포 경로**를 실제 클릭으로 확인 (lesson #15).

- [ ] 로그인 → 역할별 자동 리다이렉트
- [ ] AGENT: 이슈 등록(카테고리 자동 제안) → 담당자 배정
- [ ] FIELD(모바일): 본인 이슈 조회 → **사진 첨부(업로드 성공)** → **미리보기 표시** → 조치 → 완료 ⭐
- [ ] **세션 전환 누수 없음**: A 로그아웃 → B 로그인 열람 → A 재로그인 시 B 데이터 안 보임 (UT-001)
- [ ] ADMIN: 대시보드 KPI/차트 → 보고서 보관함 PDF 미리보기 → 벨 드롭다운 알림
- [ ] 브라우저 콘솔 에러 0 (403/CORS/mixed-content/**CSP 위반** 없음) — nginx CSP 적용 후 전 화면(로그인·이슈·모바일·대시보드·보고서·첨부 미리보기) 정상 동작 + 콘솔에 `Content-Security-Policy` 위반 0 확인. 위반 시 해당 directive(예: antd 폰트→`font-src`) 보완

## 7. 백업 (operations.md §4)

- [ ] 백업 cron 등록 (§4.1 — pg_dump + 첨부 rsync)
- [ ] **첫 백업 수동 실행** 후 산출물 확인 (`/backup/db/*.dump`, `/backup/files/...`)
- [ ] 복원 절차(§4.3/4.4) 1회 숙지 — 장애 시 당황 방지
- [ ] 월 1회 무결성 검증(§4.3) 일정 등록

## 8. 점진 활성화 — 배포 ~1주 후 (operations.md §6)

- [ ] 보관함에 일간/주간 보고서 정상 누적 확인 (08:00 KST cron 동작)
- [ ] `.env` `SMCS_REPORTS_CLEANUP_CRON=0 0 3 * * *` 로 변경 → backend 재기동
- [ ] 다음 03:00 후 `docker logs smcs_backend | grep cleanup` 로 동작 확인

## 9. 운영 중 주의 (operations.md §5/§7)

- [ ] 로그 확인 방법 숙지 (`docker compose logs -f backend`)
- [ ] 인증서 만료 모니터링 (§7.4, 90일)
- [ ] **DR**: 동일 서버 백업의 한계 — 서버 자체 장애 대비 오프사이트 복사 검토(v2)
- [ ] 디스크 사용량 모니터링 (첨부 누적 + DB + 보고서)

---

## 부록 — "정해야 시작" 항목 요약

배포를 막는 **결정 3가지**: ① 운영 도메인 ② DNS provider(인증서) ③ 백업 DR 리스크 수용 여부.
이 3개가 정해지면 §1부터 순서대로 진행 가능. 나머지는 절차(operations.md)대로 따라가면 됨.
