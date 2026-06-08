# SMCS 백로그 — P3+ 결함 + v2 후보

> Story 4.7 사용자 테스트 cycle 산출물. 본 스토리 종료 시점 기준 P1/P2 = 0 게이트 통과(별도 트래커는 `docs/stories/4.7.story.md` 발견 버그 로그). 이 파일은 **P3 이하 + v2 트리거** 항목 누적.

## 우선순위 정의

- **P1 (Blocker)** = 골든 패스 단계 완주 불가 OR 데이터 손실/오염 가능 OR 보안 결함 → **본 스토리 100% fix 필수**
- **P2 (Major)** = 완주 가능하나 워크플로 저해 OR 우회방안 있음 OR UX 심각 저하 → **본 스토리 100% fix 필수**
- **P3 (Minor)** = 외관/엣지/사용성 미세 결함 → **본 파일로 이관 OK**

## 발견 항목 (2026-05-27 dev 사전 셀프 워크스루)

| ID | 우선순위 | 제목 | 재현 단계 | 우선순위 근거 | 상태 |
| :- | :------- | :--- | :-------- | :------------ | :--- |
| SW-001 | **P1** | 모바일 "완료 처리" 가 ASSIGNED 이슈에서 항상 409 | (1) AGENT 가 issue 등록 + FIELD 배정 → status=ASSIGNED (2) FIELD 모바일 로그인 → 카드 탭 → 상세 (3) 조치 텍스트 입력 후 "완료 처리" 탭 → backend 409 INVALID_TRANSITION | `MobileFieldDetailView.complete()` (L79-87) 가 `transition({to:'DONE'})` 직접 호출. backend `IssueStatus.VALID_TRANSITIONS` 는 {ASSIGNED→IN_PROGRESS, IN_PROGRESS→DONE} 로 직접 ASSIGNED→DONE 금지. **테스트 fixture 가 IN_PROGRESS 시작이라 회귀 미감지** | **FIXED** (Task 6 — 2026-05-27 dev). `MobileFieldDetailView.complete()` 가 `issue.status === 'ASSIGNED'` 시 transition(IN_PROGRESS)→transition(DONE) 2-call chain. RTL `SW-001 P1 regression` 신규(ASSIGNED fixture). vitest **144/144**, 라이브 backend 검증(issue id=23 ASSIGNED→IN_PROGRESS 200→DONE 200), eager gzip 238.26KB<300KB §9.7. |
| SW-002 | **P2** | `N` 단축키(이슈 등록) 미구현 | (1) `/issues` 진입 (2) `N` 키 입력 → 아무 동작 없음 (기대: `/issues/new` 이동) | `golden-path.md` L19 + 키보드 AC6 Step 2 명시이나 hotkey 핸들러 어디에도 없음. **AC6 키보드 골든 패스 첫 진입 불가**. 우회: Tab으로 "신규" 버튼 도달 | **FIXED** (2026-05-27 dev). `IssueListView` 가 `useEffect` 안에서 `window keydown` 리스너 등록 — `e.key === 'n'`(대소문자 모두) + 비-modifier + 비-INPUT/TEXTAREA/combobox 타깃 조건 만족 시 `navigate('/issues/new')`. RTL 3건 신규(positive / search-input 포커스 게이트 / Ctrl+N 게이트). |
| SW-003 | **P2** | V3 시드 카테고리 keywords 빈 배열 → 4.2 자동 제안 미동작 | (1) AGENT 가 새 이슈 등록 폼 진입 (2) 제목/내용에 "wifi"/"인터넷" 등 키워드 입력 → 카테고리 자동 제안 0건 | `V3__seed_categories.sql` 의도적 `'[]'::jsonb` (Story 1.2 결정). 자동 카테고리 = ADMIN 사전 키워드 시드 필요인데 **사용자 테스트 환경 준비 절차 미문서화** → 4.2 AC 검증 불가. 우회: ADMIN 이 사전 `/admin/categories` 키워드 입력 | **FIXED** (2026-05-27 dev). `LocalDataSeeder.seedCategoryKeywords()` 가 10개 카테고리 모두에 키워드 UPDATE(local profile only — 운영 V3 는 그대로 `[]`). `golden-path.md` 시드 데이터 섹션에 명시. IT 신규 1건(10/10 keywords>0 + L2=단말 contains wifi/인터넷). |
| SW-004 | **P2** | Jackson UTF-8 파싱 실패 시 500 INTERNAL_ERROR (기대: 400) | (1) `POST /api/issues` 에 invalid UTF-8 byte 포함 페이로드 전송 (2) 응답 500 INTERNAL_ERROR | `HttpMessageNotReadableException`(JsonParseException) 가 `GlobalExceptionHandler` 에서 500 으로 catch. 정상 매핑은 400 BAD_REQUEST(클라이언트 오류). 우회: 정상 UTF-8 사용 시 영향 0 — 그러나 운영 환경에서 우발적 클라이언트 인코딩 버그 시 trace ID 만 보고 원인 추적 어려움 | **FIXED** (2026-05-27 dev). `GlobalExceptionHandler.handleNotReadable(HttpMessageNotReadableException)` 신규 → 400 `MALFORMED_REQUEST`. 단위 테스트 1건. |
| SW-005 | P3 | 시드 displayName 과 `golden-path.md`/`user-guide.md` persona 불일치 | (1) `agent1` 로그인 → 화면에 "김상담1" 표시 / 문서는 "박지영" 명시 | front-end-spec §3 페르소나(박지영/김민호/이수진) vs `LocalDataSeeder` 시드(김상담1/이현장1/관리자). 사용자 테스트 시 페르소나 이름 혼란. 우회: 시드 또는 docs 일치화 | **FIXED** (2026-06-05 QA Quinn). `golden-path.md` + `screenshots/README.md` 의 페르소나 이름(박지영/김민호/이수진)을 실제 시드 displayName(김상담1/이현장1/관리자)으로 정정 → 화면-문서 일치. front-end-spec 설계 페르소나는 SoT 로 무변경. |
| SW-006 | P3 | `golden-path.md` Stage 4 "완료 처리" 단일 액션 표기, 실은 2단계 전이 필요 | docs L49 "사진 추가 → 카메라 → 3장 → 조치 코멘트 → 완료 처리" | 실 워크플로 = (1) 시작(ASSIGNED→IN_PROGRESS) + (2) 완료(IN_PROGRESS→DONE). SW-001 fix 후 모바일 UI 가 2단계 자동 chain 하면 docs 그대로 유효. 별개로 데스크탑 IssueDetailView 는 명시적 2 클릭 필요(NEXT_STATES). | **SW-001 fix 시 동시 해소 가능** |
| SW-007 | P3 | `GET /api/categories?level=N&parentId=M` 의 `parentId` 무효 | (1) `parentId=999`(존재하지 않는 부모) 로 호출 → 빈 배열이 아니라 해당 level 의 전체 목록 반환 | parent_id 가 V3에서 NULL 통일(자유 조합 결정, Story 1.2). 그러나 컨트롤러 시그니처에 `parentId` 파라미터가 살아있어 클라이언트 측 오해 유발. 우회: 클라이언트가 level 만 사용. v2 시 시그니처 정리 권장 | OPEN |
| SW-008 | **P1** | AGENT/ADMIN UI 에 "신규 등록" 진입점 자체 부재 → 골든 패스 Stage 1 차단 | (1) agent1 로그인 → `/issues` (2) "신규 등록"/"+" 버튼 탐색 → **없음** (3) AppLayout nav 메뉴에도 신규 등록 항목 없음 → URL 직접 입력만이 유일 진입 | `/issues/new` 라우트 + `IssueFormView` 컴포넌트 모두 정상. **`IssueListView` 헤더 + AppLayout 메뉴 모두 진입 버튼 누락**. SW-002 (N 단축키 미구현) 과 합쳐 **AGENT primary action(이슈 접수) 가 UI 동선에서 완전히 사라짐**. Story 2.1 AC1 미충족. 브라우저 셀프 워크스루 단계 1 에서 사용자(권용기) 발견 | **FIXED** (Task 6 — 2026-05-27 dev). `IssueListView` 헤더 flex row 에 `<Button type="primary" icon={<PlusOutlined/>}>신규 등록</Button>` 추가, `onClick={() => navigate('/issues/new')}`, `aria-label="신규 이슈 등록"`. `/issues` 가 `RequireRole={'AGENT','ADMIN'}` 가드라 추가 권한 분기 불필요. RTL `SW-008 P1 regression` 신규(클릭→`/issues/new` 라우트 도달 단언). vitest 145/145, IssueListView lazy +0.07KB gzip(37.10KB), eager 무영향 |

## 발견 항목 (2026-06-05 Phase 2 사용자 cycle 1 — gihyeon)

| ID | 우선순위 | 제목 | 재현 단계 | 우선순위 근거 | 상태 |
| :- | :------- | :--- | :-------- | :------------ | :--- |
| UT-004 | P3 | ADMIN '내 작업'(/m) 메뉴 → `GET /api/me/assigned` 403 콘솔 에러 + 빈 페이지 | admin1 로 `/m` 진입 → FIELD 전용 엔드포인트 호출 → 403 | **정상 authz**(admin≠FIELD, 백엔드 거부가 올바름). 보안·데이터 문제 0. `AppLayout` nav 가 ADMIN 에도 '내 작업' 링크 노출(`role==='FIELD'\|\|'ADMIN'`) → 콘솔 노이즈·죽은 메뉴. UT-001 fix 후 stale 캐시 마스킹이 사라져 표면화 | **FIXED** (2026-06-05, chore/post-4.7-cleanup): `AppLayout` nav 의 '내 작업' 을 FIELD 전용으로 변경(ADMIN 제거) → ADMIN 의 FIELD 전용 `/me/assigned` 호출·403 노이즈 제거. RTL 단언 갱신(ADMIN sees 이슈 not 내 작업) |
| UT-005 | P2 | AGENT/ADMIN 데스크탑 이슈 상세에서 첨부 이미지 미리보기 깨짐 | AGENT/ADMIN 로 이슈 상세(`IssueDetailView`) 열람 → 첨부 이미지 401 → broken img | `IssueDetailView` 가 antd `<Image src={a.url}>`(평범한 `<img>`, JWT 미전송) 사용 → `/files/**` 401. 모바일 `AuthImage`(blob+JWT)는 정상이라 FIELD 만 보였음. UT-001/003 과 동일 계열(nginx 뒤 실배포 미검증, lesson #15) | **FIXED** (2026-06-05, fix/ut-005): blob fetch 를 `useAuthObjectUrl` 훅으로 추출 + 신규 `AuthPreviewImage`(antd Image + JWT blob, 줌/PreviewGroup 보존)로 갤러리 교체. AuthImage 도 훅 사용(DRY). vitest 149/149 |

> **사용자 cycle 배포 결함 4건(UT-001 캐시 노출 / UT-002 업로드 경로 / UT-003 미리보기 프록시 / UT-005 데스크탑 미리보기 401)는 전부 FIXED** — UT-001/002/003 실배포 검증 완료, 모두 lesson #15(nginx 뒤 실배포 e2e 미검증) 계열. 본 표 P3 = UT-004(ADMIN nav 정리). 상세는 스토리 발견 버그 로그.

## 보안 리뷰 (2026-06-08, 운영 배포 직전 4차원 실사)

전반 견고 — CRITICAL/HIGH 0. 인가/IDOR(중앙 `IssueAccessGuard` 실 user-id 비교)·암호화(AES-GCM fresh IV·키 fail-fast·로그 PII 0)·인젝션(SQL 파라미터화·업로드 magic byte/EXIF/traversal·XSS 0)·배포(시크릿 외부화·actuator 잠금·TLS/HSTS·비root) 모두 양호. **3건 fix(fix/security-hardening, 2026-06-08)**:

| 항목 | 등급 | fix |
| :--- | :--- | :-- |
| CSV/Excel 수식 인젝션 — `IssueCsvExporter.escape()` 가 `= + - @ TAB CR` 시작 셀 미중화 → ADMIN 이 export 를 Excel 로 열면 수식/명령 실행 | MEDIUM | 트리거 시작 셀에 `'` prefix(OWASP) + 단위 테스트 |
| CSP 헤더 부재 — nginx 가 HSTS/X-Frame 등은 있으나 Content-Security-Policy 없음 | MEDIUM | nginx 에 CSP 추가(script-src 'self' / antd 위해 style 'unsafe-inline' / img blob:). **배포 시 실 브라우저 검증 필수** |
| HMAC 키 길이 미검증 — `HmacHasher.init()` 가 non-blank 만(JWT≥32·AES=32 와 비대칭) | LOW | `init()` 에 ≥32바이트 체크 + CI 키 32B↑ 갱신 + 테스트 |

## v2 트리거 (deferred)

- **이슈 트래커**: `docs/backlog.md` → Linear/Jira 마이그레이션 (현재 MVP 단순화, Deviation #3)
- **PDF 다운로드 한글 파일명** (Story 3.5 잔여 관찰 #3, RFC 5987)
- **antd Table jsdom warning** 제거 (라이브러리 한계, 모든 4.x 스토리 공통)
- **PreventFurtherUsage budget guardrail** 자동 감지/알림 (lesson #9)
- **Category 엔티티 timestamps + name unique** (Story 4.5 잔여 관찰 #2/#3)
- **(보안) localStorage JWT → httpOnly+Secure 쿠키 + CSRF** (XSS 토큰 탈취 완화; CSP 가 현 완화책) / JWT TTL 8h 단축 검토
- **(보안) 로그인 엔드포인트 요청 rate-limit** (nginx `limit_req`) — 현재 DB 잠금 5회/10분만, 크리덴셜 스터핑 완화
- **(보안) 직원 `users.phone` 평문 → 암호화** 검토 (현재 ADMIN-only 노출이라 저위험)
- **(보안) Spring Boot 3.3.4 → 3.3.x 최신 패치** 정기 bump (알려진 CVE 아님)

## SW-001 (P1) 제안 fix 패턴

```tsx
// MobileFieldDetailView.complete() — Story 4.7 P1 fix
function complete() {
  const body = action.trim();
  if (!body) return;
  const needStart = issue.status === 'ASSIGNED';
  addComment.mutate(
    { body, kind: 'FIELD_ACTION' },
    {
      onSuccess: () => {
        if (needStart) {
          transition.mutate({ to: 'IN_PROGRESS' }, {
            onSuccess: () => transition.mutate({ to: 'DONE' }, { onSuccess: () => setAction('') })
          });
        } else {
          transition.mutate({ to: 'DONE' }, { onSuccess: () => setAction('') });
        }
      }
    }
  );
}
```

회귀 보호 = 신규 RTL 1건(`status: 'ASSIGNED'` fixture + complete 클릭 → 2 transition 호출 + 순서 검증).
