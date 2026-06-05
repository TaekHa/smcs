# SMCS 골든 패스 시나리오 (Story 4.7)

> MVP 도입 직전 사용자 테스트용 E2E 시나리오. 6 단계 통합 시나리오 + 3 역할별 stub.
> 각 단계의 "자동 확인" 항목은 이미 통합/RTL 테스트로 회귀 보호되어 있으므로, **사용자 테스트의 초점은 UX 흐름·시각/시간 감각·키보드 접근성**이다.

## 시드 데이터

- **사용자**: `agent1`(AGENT, 김상담1) / `field1`(FIELD, 이현장1) / `admin1`(ADMIN, 관리자). 모두 시드 비밀번호 `dev1234`. (Stories 1.3, 1.4)
- **카테고리**: L1(아파트먼트v1·v2·voip/pbx) / L2(관리자웹·입주민앱·단말·서버) / L3(기기미동작·기기오동작·로그인오류) (V3 migration).
- **카테고리 키워드**: `local` 프로파일에서 `LocalDataSeeder` 가 자동 시드(SW-003) — 운영 V3 마이그레이션은 `[]` 빈 배열을 유지(Story 1.2). 예: L2=단말 ← `wifi/인터넷/공유기/자판기/장비`, L3=기기미동작 ← `미동작/반환/안됨`. 사용자 테스트 환경(`local`)은 별도 사전 준비 없이 4.2 자동 제안 검증 가능.
- **테스트 이슈 후보**: "1층 자판기 동전 반환 안 됨" — L1=아파트먼트v1 / L2=단말 / L3=기기미동작 / URGENT.

## 통합 시나리오 (6 단계 E2E)

### 단계 1 — AGENT 이슈 등록 (Story 2.1 + 4.2)

| 항목 | 내용 |
| :--- | :--- |
| 행위자 | 김상담1 (AGENT, `agent1`) |
| 조작 | `/login` 입력 → `/issues` → 단축키 **N** → `/issues/new` 폼 → 제목·발신자명·전화번호 입력 → 카테고리(자동 제안 확인, 4.2) → `Ctrl+S` 저장 |
| 기대 결과 | 201 응답 → `/issues/{id}` 이동, status=`NEW`, 활동로그에 `CREATED` 이벤트, 발신자 PII 는 AES-GCM 암호화 저장(서버 로그 평문 0) |
| 자동 회귀 | `IssueControllerIntegrationTest` POST /api/issues 201 / `IssueFormView.autoCategory.test` 키워드 자동 제안 / `IssueFormView.test` Ctrl+S 단축키 |
| 수동 검증 포인트 | 자동 채움(접수 시각·접수자)/`N` 단축키 도달성/카테고리 트리 모달 진행감/저장 후 화면 전환 1초 이내 체감 |

### 단계 2 — AGENT 가 FIELD 에 배정 (Story 2.4 + 2.8)

| 항목 | 내용 |
| :--- | :--- |
| 행위자 | 김상담1 (AGENT) |
| 조작 | `/issues/{id}` → "담당자 배정" → 드롭다운에서 이현장1(`field1`) 선택 → 확인 |
| 기대 결과 | status=`ASSIGNED`, `assigned_to=field1`, IssueEvent(`ASSIGNED`), notifications(ISSUE_ASSIGNED, recipient=field1, actor=agent1) 1행 — 본인(actor)에게는 알림 0 |
| 자동 회귀 | `IssueTransitionIntegrationTest` assign / `NotificationIntegrationTest.assignNotifiesAssignee` |
| 수동 검증 포인트 | 드롭다운 검색 가능성/배정 직후 활동로그 즉시 반영 |

### 단계 3 — FIELD 모바일 본인 이슈 조회 (Story 2.5 + 2.6)

| 항목 | 내용 |
| :--- | :--- |
| 행위자 | 이현장1 (FIELD, `field1`, **모바일 디바이스**) |
| 조작 | 폰 브라우저에서 SMCS URL → `/login` → 자동 redirect `/m` → 카드 스택에서 URGENT 카드(빨간 막대) 탭 → `/m/issues/{id}` 상세 |
| 기대 결과 | `GET /api/issues?assignedTo=field1` 본인 이슈만 / `GET /api/issues/{id}` 200 (FIELD ownership §6.3 통과) / 카테고리·메타·이전 활동 표시 |
| 자동 회귀 | `MeAssignedIntegrationTest` / `MobileFieldHomeView.test` / `MobileFieldDetailView.test` |
| 수동 검증 포인트 | 모바일 폰트/터치 타깃 크기/URGENT 시각 강조/카드↔상세 전환 자연스러움 |

### 단계 4 — FIELD 사진 첨부 + 조치 + 완료 (Story 2.6 + 2.3)

| 항목 | 내용 |
| :--- | :--- |
| 행위자 | 이현장1 (FIELD, 모바일) |
| 조작 | 상세 → "사진 추가" → 카메라/갤러리 → **3장** 첨부 → 조치 코멘트("전원 보드 교체, 정상 동작 확인") → "완료 처리" → 확인 모달 → 확인 |
| 기대 결과 | attachments 3 rows (EXIF strip 적용), comment 1 row(kind=`FIELD_ACTION`), IssueEvent(`COMMENTED`, `STATUS_CHANGED`), status=`DONE`, resolvedAt=now, notifications(ISSUE_STATUS_CHANGED → 김상담1) |
| 자동 회귀 | `AttachmentIntegrationTest` (3장 / EXIF / 10MB / 10장 한도) / 2.6 transition DONE / 2.8 notify status_changed |
| 수동 검증 포인트 | 카메라 직접 호출(또는 갤러리 폴백) 동작/업로드 진행 표시/완료 모달 텍스트 명료성 |

### 단계 5 — AGENT 검수 (Story 2.7)

| 항목 | 내용 |
| :--- | :--- |
| 행위자 | 김상담1 (AGENT, 데스크탑) |
| 조작 | 알림 벨 클릭 → 드롭다운(4.1) → 항목 클릭 → `/issues/{id}` → "검수 완료" 버튼(또는 "재오픈" → 모달 사유 입력 → 확인) |
| 기대 결과 (검수) | status=`VERIFIED`, IssueEvent(`STATUS_CHANGED`), notifications(ISSUE_STATUS_CHANGED → field1) |
| 기대 결과 (재오픈) | status=`IN_PROGRESS`, resolvedAt=null, Comment(kind=`NOTE`, body="재오픈 사유: ..."), IssueEvent(`COMMENTED`+`STATUS_CHANGED`), notifications(ISSUE_REOPENED) |
| 자동 회귀 | `IssueVerifyReopenIntegrationTest` 5+ 케이스 / `IssueDetailView.test` 재오픈 모달 |
| 수동 검증 포인트 | DONE 상태에서 검수/재오픈 두 버튼 분리 명확/재오픈 모달의 사유 필수 표시 |

### 단계 6 — ADMIN 대시보드 + 보고서 + 알림 (Story 3.2 + 3.4 + 3.5 + 4.1)

| 항목 | 내용 |
| :--- | :--- |
| 행위자 | 관리자 (ADMIN, `admin1`, 데스크탑, **다음 날 09:00 KST**) |
| 조작 | `/login` → 자동 `/dashboard` (RoleRedirect) → KPI 4 카드 + 카테고리 차트 + 담당자별 처리량 확인 → 좌측 "보관함" → `/reports` → 어제 일간 PDF 미리보기 → 헤더 벨 → 드롭다운에 `REPORT_READY` 알림 → 항목 클릭 → `/reports` (3.4 분기) |
| 기대 결과 | `GET /api/stats/...` 응답 / `/api/reports` 어제 일간 1건(08:00 KST cron) / `notifications(REPORT_READY, recipient=admin1, issue_id=NULL)` / 벨 드롭다운 항목 클릭 시 markRead + `/reports` 이동 |
| 자동 회귀 | `ReportArchiveIntegrationTest` 일간/주간 생성 / `ReportArchiveAccessIntegrationTest` ADMIN-only / `ReportControllerIntegrationTest` / `NotificationBell.test` REPORT_READY 분기 |
| 수동 검증 포인트 | 보고서 표지·KPI 정확성/PDF 미리보기 로드 속도/벨 드롭다운 10건·"모두 보기" 풋터 동작 |

## 역할별 stub 시나리오 (front-end-spec §3 carry-over)

### Stub A — AGENT (김상담1) "통화 1건 처리"

```
[전화 벨] → [N 단축키] → [등록 폼·자동 포커스]
   ↓ 듣는 동안 키워드만 빠르게 입력
[제목·발신자명·전화·내용] → [카테고리 자동 제안 확인 or 수정]
   ↓ Ctrl+S → 상세 페이지
[담당자 드롭다운 → 이현장1 선택] → [자동 알림 발송] → [통화 종료]
```
⏱️ 목표 시간: 등록 30초 / 배정 포함 1분 — *front-end-spec §3.1*

### Stub B — FIELD (이현장1) "출동/조치/보고"

```
[차량] → [폰 잠금 해제] → [SMCS PWA → 자동 로그인 → 모바일 홈]
   ↓ 카드 스택에서 URGENT 카드 탭
[상세: 1층 자판기 미동작] → [현장 30분 작업 (시스템 안 봄)]
   ↓ 차로 복귀 → 폰 다시
[사진 추가 → 카메라 → 3장] → [조치 코멘트 짧게] → [완료 처리]
   ↓ 자동으로 다음 카드
```
⏱️ 목표 시간: 입력 3분 이내 — *front-end-spec §3.2*

### Stub C — ADMIN (관리자) "매일 아침 1분 루틴"

```
[09:00 노트북] → [브라우저 즐겨찾기 SMCS] → [로그인 후 /dashboard]
   ↓ KPI 4 카드: 어제 신규 N / 처리 N / 미처리 N / 평균 N시간
[카테고리별 분포 차트 훑기] → [평소보다 ↑인 분류 탐지]
   ↓ 좌측 "보관함" → 어제 일간 PDF
[1페이지 빠르게 스캔] → [필요시 특정 이슈 클릭해 상세]
```
⏱️ 목표 시간: 60초 — *front-end-spec §3.3*

## 자동 회귀 커버리지 요약

| 단계 | 자동 회귀 수단 | 통과 시점 |
| :--- | :------------- | :-------- |
| 1 등록 | IssueController IT, IssueFormView RTL(autoCategory + Ctrl+S) | Story 2.1/4.2 Done |
| 2 배정 | IssueTransition IT, Notification IT (assign) | Story 2.4/2.8 Done |
| 3 모바일 조회 | MeAssigned IT, MobileFieldHomeView/DetailView RTL | Story 2.5/2.6 Done |
| 4 사진+완료 | Attachment IT (EXIF/10MB/10장), 2.6 transition + 2.8 notify | Story 2.6/2.8 Done |
| 5 검수/재오픈 | IssueVerifyReopen IT, IssueDetailView RTL | Story 2.7 Done |
| 6 대시보드/보고서/알림 | Stats IT, ReportArchive IT, NotificationBell RTL (4.1) | Story 3.2/3.4/3.5/4.1 Done |

→ **6 단계 모두 자동 회귀 커버됨**. 본 스토리(4.7)의 수동 테스트는 자동에서 잡히지 않는 **UX 시점·시간 감각·키보드 접근성·실제 사진/모바일 디바이스 동작**을 검증한다.

## 키보드 골든 패스 (AC6 — architecture §9.x mandated)

마우스 사용 **0회**로 다음 4 단계를 통과해야 함. 통과 못 하면 P1 또는 P2 등록.

1. **로그인** — username 자동 포커스 → 입력 → Tab → password 입력 → Enter
2. **이슈 등록** — `N` 단축키(또는 Tab 도달) → 폼 Tab 순회(제목·발신자명·전화·카테고리 트리·내용) → Ctrl+S
3. **조치 코멘트(FIELD 시점)** — 상세에서 코멘트 textarea 까지 Tab → 입력 → "추가" 버튼 도달 → Enter
4. **완료 처리** — 상태 버튼 Tab 도달 → Enter → 확인 모달의 "확인" 버튼 Tab → Enter

각 단계에서 (a) 포커스 시각 표시 명확 (b) 모달 열림 시 트랩 동작 (c) ESC 로 모달 취소 확인. 스크린리더 검증은 MVP 필수 아님(v2).
