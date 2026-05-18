# Story TD-1: Story 2.1 캐리오버 기술부채 (axios 보안 범프 + 프론트 번들 코드분할) — Brownfield Addition

## Status: Done

> QA Gate (2026-05-18, Quinn — Test Architect): 🟢 **PASS**. 프론트 전용·로컬 완전 검증(Testcontainers/CI 의존 없음). 독립 재실행: N-3 `npm audit` axios **high 0**(axios 1.16.1 locked, 인터셉터 51/51 무회귀), N-2 entry 청크 308→**22KB** gzip·eager 초기로드 실측 **276KB<300KB** §9.7·lazy 청크 <150KB, tsc/build/51 全그린, 코드리뷰 적정. 잔여 dev-only esbuild moderate = TD-1 범위 외(프로덕션 번들 무관 info). 결함 0.

## Story

- As a **개발팀/운영자**
- I want **알려진 axios high 취약점을 제거하고 프론트 초기 번들을 성능 가드레일 이내로 되돌리기**를
- so that **프로덕션 보안 노출을 닫고 architecture §9.7 성능 기준을 회복한다**

> 출처: Story 2.1 QA Results 잔여 비차단 항목 **N-3**(보안, 우선), **N-2**(perf). 두 Task 는 상호 독립이며 각각 한 세션 내 완결 가능. N-3 먼저 처리 권장.

## Acceptance Criteria (ACs)

1. **(N-3)** `frontend` 의 `axios` 가 알려진 high 취약점(SSRF/자격증명 누출, GHSA-jr5f-v2jv-69x6 외 prototype-pollution 체인)이 패치된 1.x 최신 버전으로 상향되고, `npm audit` 의 해당 axios high 가 사라진다.
2. **(N-3)** `apiClient`(JWT 자동첨부 request 인터셉터 + 401 자동 logout response 인터셉터)와 모든 도메인 API 함수가 동작 불변 — 기존 프론트 테스트 51건 전부 통과 유지.
3. **(N-2)** 프론트 **초기 진입 청크 gzip < 300KB** (architecture §9.7 가드레일) 로 복귀. `npm run build` 출력으로 확인.
4. **(N-2)** route-level code-splitting + Vite `manualChunks` vendor 분할 적용 후에도 모든 라우트(`/login`,`/`,`/issues`,`/issues/new`,`/issues/:id`,`/m`,`/403`,`*`)가 정상 동작 — 테스트 51건 통과 + 빌드 성공.
5. 기존 기능 회귀 없음(백엔드 무관·무변경). 변경은 `frontend/` 한정.

## Tasks / Subtasks

- [x] **Task 1 (AC: 1, 2) — N-3 axios 보안 범프 [우선]**
  - [x] `frontend/package.json` 의 `axios` `1.7.7` → `^1.16.1`, lock 갱신.
  - [x] `npm audit` 재확인 — axios high **소거**(잔여 = dev-only esbuild moderate 2, 범위 외).
  - [x] `src/api/client.ts` 인터셉터 API 호환 — axios 1.16 내 시그니처 불변, **코드 변경 불필요**.
  - [x] 회귀: `npm test` **51/51**(`client.test.ts` 그린) + `npm run build` 성공.

- [x] **Task 2 (AC: 3, 4) — N-2 번들 코드분할**
  - [x] `vite.config.ts` `manualChunks` vendor 분할(`react-vendor`/`antd-vendor`/`query-vendor`) — §9.7.
  - [x] `routes.tsx` `IssueFormView`/`IssueDetailView`/`MobileFieldHomeView` `React.lazy` + 단일 `<Suspense fallback={<Spin/>}>`. 부팅 경로(Login/RoleRedirect/error/IssueList placeholder) eager 유지. 명명 export → `default` 리맵.
  - [x] **초기 청크 gzip < 300KB 달성**: entry `index` = **22KB**(이전 단일 308KB). eager 합산(index+react+antd+query) ≈ **~275KB < 300KB**. lazy `IssueFormView` 25KB(<150KB §9.7).
  - [x] 회귀: `npm test` **51/51**, `tsc` 통과, `npm run build` 성공.

- [x] **Task 3 — 검증/문서**
  - [x] N-2/N-3 는 Story 2.1 QA Results 의 비차단 항목 → 본 TD-1 에서 해소(2.1 파일 미수정, 이력 보존).
  - [x] 번들 전/후 수치 Completion Notes 기록.

## Dev Notes

[[LLM: 본 스토리 한정 정보. 코딩/테스트 표준은 dev 가 이미 인지.]]

### Existing System Integration

- **대상**: `frontend/` 전용. 백엔드 무변경·무영향.
- **N-3 통합점**: `frontend/src/api/client.ts`(axios 인스턴스 + 인터셉터). 도메인 API: `api/auth.ts|me.ts|issues.ts|categories.ts|navigation.ts`. axios 1.7.7→1.x 최신은 동일 메이저, 인터셉터/`create()` API 안정.
- **N-2 통합점**: `vite.config.ts`(빌드 설정), `frontend/src/routes.tsx`(라우트 정의). 패턴 출처: architecture §9.7(Code Splitting/Vendor Splitting), §9.5(lazy), Story 1.5 Dev Notes carry-over #3.

### 출처 / 컨텍스트

- N-3: Story 2.1 QA Results — `npm audit` high = `axios 1.0.0–1.15.1`(현 `1.7.7`, Story 1.4 도입). dev-only `esbuild` moderate 2건은 본 스토리 제외(vite 툴체인, 별도).
- N-2: Story 2.1 — RHF+Zod+TanStack Query 도입으로 초기 청크 gzip 238KB(1.5) → **305.8KB**(2.1). architecture §9.7 가드레일: 초기 진입 청크 < 300KB(gzip), 라우트별 lazy < 150KB.
- 현재 `vite.config.ts` 는 `manualChunks` 미설정, `routes.tsx` 전부 eager import(1.5 carry-over #3 가 의도적 지연 명시 — 본 스토리가 그 해소 시점).

### 기존 패턴 (준수)

- 라우트 보호 패턴은 1.5/2.1 그대로(`RequireAuth`/`RequireRole`/`AppLayout`). lazy 도입 시 보호 래핑 구조 불변, 컴포넌트만 `lazy()` 로 교체 + `Suspense` 추가.
- API 호출은 `api/*.ts` → `apiClient` 경유(PRD §9.1) — 변경 없음.

### Risk & Compatibility

- **Primary Risk**: axios 메이저-내 마이너 업그레이드가 인터셉터/에러 형태(`err.response`)에 미세 변화 → `LoginView.mapError`/`client.ts` 401 처리 영향. **Mitigation**: 51 테스트(특히 `client.test.ts`, `LoginView.test.tsx`) 회귀 + 빌드로 검증. **Rollback**: `package.json`/lock 의 axios 버전 되돌림(단일 의존성).
- **N-2 Risk**: lazy 분할로 라우트 진입 시 `Suspense` 깜빡임/테스트 비동기화. **Mitigation**: 부팅 경로(login/redirect) eager 유지, 테스트는 컴포넌트 직접 렌더라 lazy 미경유 — 51건 회귀로 확인. **Rollback**: `routes.tsx`/`vite.config.ts` revert.
- 호환성: API/DB 변경 없음. UI 동작 불변(분할은 로딩 전략만). 성능은 개선 방향.

### Testing

Dev Note: Story Requires the following tests:

- [ ] **회귀(필수)**: `npm test` 51건 전부 통과(신규 테스트 불요 — 동작 불변 리팩터/범프). `src/api/client.test.ts` 명시 그린.
- [ ] **빌드 검증**: `npm run build` 성공 + 초기 청크 gzip 수치 < 300KB 캡처.
- [ ] **수동**: dev 가동 후 각 lazy 라우트 1회 진입(`/issues/new`,`/m`,`/issues/:id`) 정상 렌더 — QA/사용자 수행 권장.
- [ ] E2E: 범위 밖.

### PO Validation (Sarah, 2026-05-18)

**판정: APPROVED** — brownfield Validation Checklist 통과. Scope(각 Task 단일 세션)·Clarity(측정가능 AC)·Risk(낮음, 롤백 단순) 충족. Epic 4 중복 없음 확인.
- **우선순위**: Task 1(N-3 보안 high) 먼저. Task 2(N-2 perf) 후행.
- **Escalate 조건**: Task 2 에서 vendor 분할만으로 초기 청크 <300KB 미달성 시 — 추가 lazy 경계 분석이 한 세션 초과로 커지면 본 스토리 분할(brownfield-create-epic) 재검토. Dev 는 3회 실패 규칙 적용.
- **독립성**: 두 Task 무의존 — 부분 머지(Task 1만 먼저) 허용.

### QA Results

**Reviewer:** Quinn (Test Architect) · **Date:** 2026-05-18 · **Gate:** 🟢 **PASS** (결함 0, 로컬 완전 검증)

| AC | 독립 검증 | 결과 |
| :- | :-------- | :--- |
| 1 axios high 제거 | `npm audit` → high 0; `axios` locked `1.16.1` | ✅ |
| 2 인터셉터/API 불변 | 51/51 (`client.test.ts` 포함) 회귀 그린 | ✅ |
| 3 초기 청크 <300KB | entry `index` 22KB; eager(index+react+antd+query) 실측 **276KB** < 300KB §9.7 | ✅ |
| 4 라우트 정상·회귀 | 51/51 + tsc 0 + build 0; lazy `IssueFormView` 25KB(<150KB §9.7) | ✅ |
| 5 frontend 한정·무회귀 | 변경 `package.json`/lock·`vite.config.ts`·`routes.tsx` 한정; 백엔드 무관 | ✅ |

**코드 리뷰:** `vite.config.ts` manualChunks(react/antd/query vendor) 적정; `routes.tsx` 명명-export→`default` lazy 리맵 + 단일 `<Suspense fallback={<Spin/>}>`, 부팅경로(Login/RoleRedirect/error/IssueList) eager 유지, lazied 컴포넌트 eager import 제거 확인.

**판정 근거:** TD-1 은 Story 2.1 과 달리 Testcontainers/CI 의존이 없어 **모든 AC 를 로컬에서 1차 직접 검증** — CONCERNS 보류 사유 없음. Story 2.1 잔여 비차단 N-2/N-3 가 본 스토리로 **완전 해소**. 잔여 dev-only `esbuild` moderate 2건은 TD-1 범위 외(스토리 Task 1 명시) — 프로덕션 번들 무관 info-level, 향후 vite 업그레이드 건으로 추적(게이트 무관).

### Definition of Done

- [ ] AC1–5 충족, axios high 소거(`npm audit` 캡처), 초기 청크 < 300KB(빌드 출력 캡처)
- [ ] 51 테스트 + 빌드 그린, 라우트 회귀 없음
- [ ] 기존 패턴/표준 준수, `frontend/` 외 변경 없음
- [ ] Completion Notes 에 전/후 번들 수치 + 최종 axios 버전 기록

## Dev Agent Record

### Agent Model Used: claude-opus-4-7 (1M context)

### Debug Log References

| Task | File | Change | Reverted? |
| :--- | :--- | :----- | :-------- |
| — | — | 임시 변경 없음 — 의존성 범프 + 빌드설정/라우트 분할만, 전부 첫 시도 그린 | — |

### Completion Notes List

- **N-3**: `axios 1.7.7 → ^1.16.1`. `npm audit` axios **high 소거**(잔여 = dev-only `esbuild` moderate 2, 범위 외/별도). `client.ts` 인터셉터 코드 변경 불필요(1.x API 안정). 51/51 회귀 그린.
- **N-2 번들(전→후, gzip)**: 단일 `index` **308KB** → 분할: entry `index` **22KB** / `antd-vendor` 232 / `query-vendor` 14 / `react-vendor` 7 / lazy `IssueFormView` 25. eager 초기 로드 합산 ≈ **~275KB < 300KB**(§9.7 가드레일 충족, 양쪽 해석 모두). lazy 청크 全 <150KB.
- 명명 export 컴포넌트는 `lazy(() => import().then(m => ({default:m.X})))` 패턴. 테스트 51건은 컴포넌트 직접 렌더라 lazy 미경유 — 무회귀 확인.
- 변경 `frontend/` 한정(`package.json`/lock, `vite.config.ts`, `routes.tsx`). 백엔드 무관.

### Change Log

| Date       | Version | Description | Author |
| :--------- | :------ | :---------- | :----- |
| 2026-05-18 | 0.1     | Initial draft by PO (Sarah) via brownfield-create-story. 단일 TD 스토리(사용자 결정), N-3 보안 우선 + N-2 코드분할 2 Task. 출처: Story 2.1 QA Results N-2/N-3. Epic 4 중복 없음 확인. | Sarah (PO) |
| 2026-05-18 | 0.2     | PO 검증 — brownfield checklist 통과, Status Draft → **Approved**. 우선순위(N-3 선행)·escalate 조건·Task 독립성 명시. dev 착수 가능. | Sarah (PO) |
| 2026-05-18 | 0.3     | 구현 완료(James). Task 1 axios→^1.16.1(high 소거), Task 2 manualChunks+lazy(entry 308→22KB, eager~275<300KB), Task 3 문서. 51/51 + tsc + build 그린. Status → **Review**. | James (Dev) |
| 2026-05-18 | 0.4     | QA 게이트(Quinn): 🟢 **PASS**, Status → **Done**. 독립 재실행 검증: audit high 0, eager 276KB<300KB 실측, 51/51, tsc/build 0, 코드리뷰 적정. 로컬 완전 검증(CI 의존 없음) → CONCERNS 사유 없음. | Quinn (QA) |
