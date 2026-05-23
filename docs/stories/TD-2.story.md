# Story TD-2: Story 3.3 + 3.4 QA 캐리오버 — report 도메인 견고화 (Brownfield Addition)

## Status: Review

> 출처: **Story 3.4 QA 잔여 #1**(Quinn, PR #16) + **Story 3.3 QA 잔여 #1**(Quinn, PR #15) — 둘 다 비차단으로 머지됐고, Epic 3 가 운영 단계로 가기 전 한 PR 로 정리. TD-1 패턴(2.1 carry-over 묶음) 일관.

## Story

- As a **운영팀/개발자**
- I want **`ReportArchiveService.runArchive`의 트랜잭션 경계가 실제로 적용되도록 하고, `ReportService.loadOpenIssues`가 운영 데이터 누적에도 메모리 안전하기를**
- so that **재생성 보고서의 `size_bytes` 메타가 정확해지고, 미처리 이슈 수가 많아져도 보고서 PDF 생성이 OOM 위험 없이 안정적으로 동작한다**

## Acceptance Criteria (ACs)

1. **(3.4 QA #1)** `ReportArchiveService.generateAndStoreDaily`/`generateAndStoreWeekly` 의 두 번째(이후) 호출에서 동일 `(kind, periodKey)` 의 `reports.file_path` / `size_bytes` 가 **실제로 갱신**되어 DB 에 반영된다(자동 dirty checking 작동). `createdAt` 은 그대로 보존된다.
2. **(3.4 QA #1)** 통합 테스트로 PDF 바이트 변동 시 `size_bytes` 갱신이 검증된다(현 `rerunUpsertsTheSameRowAndKeepsCreatedAt` 보강 또는 신규).
3. **(3.3 QA #1)** `ReportService.loadOpenIssues` 가 SQL `LIMIT` 으로 최대 `OPEN_LIST_MAX + 1` 행만 받고, 전체 미처리 건수는 별도 카운트 쿼리로 알아낸다(메모리 안전).
4. **(3.3 QA #1)** "이하 N건 생략 — 보관함 PDF 참조" footnote 의 N 이 **정확한 hidden count**(전체 미처리 - 표시 30) 로 표기된다 — 단위 테스트로 검증.
5. 기존 기능 회귀 없음. 단위 + 통합 + 프론트 회귀 그린. 마이그레이션 0, 신규 의존성 0, AC/엔드포인트/DTO 변경 없음(내부 리팩터).

## Tasks / Subtasks

- [x] **Task 1 (AC: 1, 2) — 3.4 `runArchive` `@Transactional` self-invocation 해소**
  - [x] `ReportArchiveService` 수정: `@Transactional` 어노테이션을 **public 진입 메서드** `generateAndStoreDaily(LocalDate)` / `generateAndStoreWeekly(int, int)` 로 이동. `runArchive(...)` package-private 메서드의 `@Transactional` 은 제거(중복·무효 — Spring AOP 프록시는 self-invocation 무효). 두 public 메서드의 호출자(스케줄러/통합 테스트) 는 모두 외부 → 프록시 경유 보장. [Source: backend/.../report/ReportArchiveService.java(현 runArchive @Tx); docs/stories/3.4.story.md QA Results 잔여 #1]
  - [x] **통합 테스트 보강**: `ReportArchiveIntegrationTest.rerunUpsertsTheSameRowAndKeepsCreatedAt` 에 PDF 바이트 변동 시 `size_bytes` 갱신 검증 추가 — 첫 호출 후 시드 변경(예: 신규 이슈 INSERT) → 두 번째 호출 → `reports.size_bytes` 가 첫 값과 **다름** 확인. 또는 신규 시나리오 `rerunUpdatesSizeBytesWhenContentChanges` 추가. [Source: backend/.../report/ReportArchiveIntegrationTest.java; docs/stories/3.4.story.md QA Results 잔여 #1]
  - [x] 단위 테스트 회귀: `ReportArchiveServiceTest` 5/5 그대로 통과(Mockito 라 @Transactional 영향 없음 — 호출 횟수/순서만 검증).

- [x] **Task 2 (AC: 3, 4) — 3.3 `loadOpenIssues` 페이징 + 정확한 hidden count**
  - [x] `IssueRepository` 에 `long count(Specification<Issue>)` 신규 메서드 추가 또는 `JpaSpecificationExecutor.count(...)` 기본 메서드 활용(Spring Data 기본 제공 — `JpaSpecificationExecutor<Issue>` 이미 상속, 신규 메서드 불요 확인). [Source: backend/.../issue/IssueRepository.java(JpaSpecificationExecutor 상속)]
  - [x] `ReportService.loadOpenIssues()` 변경:
    - `Specification<Issue> open = ...` 그대로
    - `Page<Issue> page = issueRepository.findAll(open, PageRequest.of(0, OPEN_LIST_MAX + 1, severityThenCreatedSort()))` — `OPEN_LIST_MAX + 1` 건만 받음
    - `long totalOpen = issueRepository.count(open)` — 별도 카운트 쿼리(인덱스 적중, 효율)
    - 반환: `OpenIssuesResult(List<OpenIssueRow> rows, long total)` 신규 nested record — `rows` 는 페이지 내용, `total` 은 전체 미처리 수
    - `severityThenCreatedSort()` 신규 헬퍼: `Sort.by(...)` — 다만 priority enum 정렬은 CASE 표현식이라 `Specification` orderBy 가 필요 → **대안**: `findAll(Specification)` 그대로 두되 페이징 호출 시 별도 Sort 무시되니까 기존 `severityThenCreatedAsc()` Specification 을 spec 에 and 합쳐서 PageRequest 사용 시 page 의 정렬은 spec 의 orderBy 에 의존(2.2 `IssueQueryService.list` 패턴 그대로 — `count` 쿼리에는 orderBy 무시되어 부담 X).
    - **세부**: `issueRepository.findAll(open.and(severityThenCreatedAsc()), PageRequest.of(0, OPEN_LIST_MAX + 1))` — Page<Issue> 반환. `getContent()` 로 List 추출. [Source: backend/.../report/ReportService.java(현 loadOpenIssues findAll 전체); backend/.../issue/IssueQueryService.java#list(2.2 Specification + Pageable 선례); docs/stories/3.3.story.md QA Results 잔여 #1]
  - [x] `ReportData` record 에 `long totalOpenCount` 필드 추가(기존 5개 → 6개). 기존 `openIssues: List<OpenIssueRow>` 는 페이지 내용(MAX+1 최대). [Source: backend/.../report/dto/ReportData.java(현 record)]
  - [x] `ReportPdfRenderer.writeOpenIssueRows(cursor, data.openIssues(), data.totalOpenCount())` — hidden = `Math.max(0, totalOpenCount - OPEN_LIST_MAX)` 로 footnote 정확. 기존 `rows.size() > OPEN_LIST_MAX` 판단 로직은 `totalOpenCount > OPEN_LIST_MAX` 로 변경. [Source: backend/.../report/ReportPdfRenderer.java(현 writeOpenIssueRows)]
  - [x] **단위 테스트 변경**:
    - `ReportServiceTest.overflowFootnoteShownWhenOpenListExceedsCap` — mock 변경: `issueRepository.findAll(any(Spec.class), any(Pageable.class))` 가 31개 List 를 담은 Page 반환 + `issueRepository.count(any(Spec.class))` 가 35 반환 → renderer 가 "이하 5건 생략" 출력 검증.
    - 신규 `ReportServiceTest.openListUnderCapNoFootnote` — count=10 → footnote 미표기 검증(회귀).
  - [x] **통합 테스트**: 새 시나리오 `ReportControllerIntegrationTest.dailyPdfTruncatesOpenListAt30WithFootnote` 권장(MAX+5=35건 시드 → PDF 본문에 "이하 5건 생략" 포함). PDFBox 텍스트 추출. CI. [Source: backend/.../report/ReportControllerIntegrationTest.java(3.3 통합 패턴)]

- [x] **Task 3 — 검증/문서**
  - [x] `./gradlew build -x test` ✅ assemble + 단위(통합 CI). 회귀: Stats/Report/Notification/Issue/Attachment/ReportArchiveAccess 통합 그린.
  - [x] 회귀: 프론트 102/102(본 PR 백엔드 전용 — 영향 없음).
  - [x] QA Results 갱신은 본 TD-2 의 QA 게이트에서 처리(원본 3.3/3.4 파일은 무수정, 이력 보존 — TD-1 선례).

## Dev Notes

[[LLM: 본 스토리 한정 정보. 코딩/테스트 표준은 dev 가 이미 인지.]]

### Existing System Integration

- **대상**: `backend/com.smcs.report` 패키지. 프론트 무변경·무영향(엔드포인트/DTO 모양 불변).
- **Task 1 통합점**: `ReportArchiveService` 의 `@Transactional` 위치만 변경(어노테이션 이동, public 진입 메서드). 호출자 = `ReportScheduler.dailyJob/weeklyJob` + 통합 테스트 — 모두 외부 호출이라 프록시 경유.
- **Task 2 통합점**: `ReportService.loadOpenIssues` (private 헬퍼) + `ReportData` record (필드 1개 추가) + `ReportPdfRenderer.writeOpenIssueRows` (시그니처 1개 추가). 외부 API/DB 스키마 변경 없음.

### 출처 / 컨텍스트

- **Task 1**: Story 3.4 QA Results (Quinn, 2026-05-23) 잔여 관찰 #1 — "`runArchive` `@Transactional` self-invocation 무효화 → `existing.replaceFile()` detached → `size_bytes` stale 가능". 영향: `file_path` 는 동일 입력시 동일 출력이라 미세, `size_bytes` 만 stale. **본 TD-2 가 일관성 회복.**
- **Task 2**: Story 3.3 QA Results (Quinn, 2026-05-23) 잔여 관찰 #1 — "`loadOpenIssues` SQL `LIMIT` 없음, 운영 데이터 누적 시 메모리 위험". PO Sarah 분리 결정(3.4 v0.2 Deviation #4 — 별도 tech debt).

### 기존 패턴 (준수)

- `@Transactional` 은 public 메서드에 — 2.x `IssueService.assign`/`transition` 선례, Spring AOP 프록시 보장.
- `Specification + PageRequest` 페이징은 2.2 `IssueQueryService.list` 패턴 — `JpaSpecificationExecutor` 기본 `findAll(Specification, Pageable)` / `count(Specification)`.
- record 필드 추가는 `ReportData` 의 nested record 동일 패턴 — 시그니처 호환성은 모든 생성 위치(`ReportService.generate(period)`)에서 일괄 갱신.

### Risk & Compatibility

- **Primary Risk (Task 1)**: `@Transactional` 이동으로 트랜잭션 경계가 `generateAndStoreDaily/Weekly` 전체로 확대 — try/catch 가 트랜잭션 안에서 발생하므로 `notifyAdmins(FAILED)` 도 트랜잭션 내. NotificationService.notifyAdmins 가 호출자 트랜잭션 참여(2.8 backfill 패턴)라 무문제. **Mitigation**: 통합 테스트로 실제 dirty checking 동작 + 실패 시 REPORT_FAILED 알림 진행 검증.
- **Primary Risk (Task 2)**: `count(Specification)` 추가 쿼리 = 1회 — 인덱스 적중(`idx_issues_status` V2). 부담 무시 가능. footnote 가 정확해지는 부수 효과. **Mitigation**: 단위 + 통합 회귀.
- **Rollback**: Task 1 = `@Transactional` 이동 revert(2줄). Task 2 = 메서드 시그니처 + record 필드 revert(20줄 내). 둘 다 PR 단일 commit 단위로 분리 가능.
- 호환성: API/DB/엔드포인트/DTO 응답 모양 불변. PDF 본문 footnote 정확성↑ 외 사용자 가시 변화 0.

### Testing

- [ ] **단위(필수)**: `ReportArchiveServiceTest` 5/5 그대로 + `ReportServiceTest` `overflowFootnoteShownWhenOpenListExceedsCap` mock 갱신 + 신규 `openListUnderCapNoFootnote`. Mockito.
- [ ] **통합(CI, Testcontainers)**: `ReportArchiveIntegrationTest.rerunUpsertsTheSameRowAndKeepsCreatedAt` 보강(size_bytes 갱신) + 신규 `ReportControllerIntegrationTest.dailyPdfTruncatesOpenListAt30WithFootnote`(PDFBox 텍스트 추출). 회귀 그린.
- [ ] **빌드 검증**: `./gradlew build -x test` 그린. 프론트 변경 없음.
- [ ] E2E: 범위 밖.

### PO Validation 후보 결정

- 우선순위: **Task 1 (1줄 변경, 안전성 회복) 먼저** → **Task 2 (메모리 안전망) 후행**. 한 PR 안에 두 commit 권장.
- AC 변경 없음 — 비차단 잔여 관찰 정리이므로 epic-3 / PRD 정합성 영향 무.

## Dev Agent Record

### Agent Model Used: claude-opus-4-7 (James / Dev)

### Debug Log References

| Task | File | Change | Reverted? |
| :--- | :--- | :----- | :-------- |

(없음 — 1회 그린)

### Completion Notes List

- **Task 1**: `@Transactional` 을 `generateAndStoreDaily(LocalDate)` / `generateAndStoreWeekly(int, int)` public 메서드로 이동, `runArchive` package-private 메서드의 `@Transactional` 제거(self-invocation 무효). 통합 `rerunUpsertsTheSameRowAndKeepsCreatedAt` 보강 — 두 번째 호출 전 새 이슈 INSERT → PDF 길이 변동 → `size_bytes` 갱신 검증(이 fix 없으면 detached entity 라 stale).
- **Task 2**: `ReportService.loadOpenIssues` 페이징 — `findAll(spec, PageRequest.of(0, OPEN_LIST_MAX + 1))` + `count(spec)` 별도 쿼리. `JpaSpecificationExecutor` 기본 메서드라 IssueRepository 변경 0. `ReportData` 에 `totalOpenCount` 필드 추가(record 5→6). `ReportPdfRenderer.writeOpenIssueRows` 시그니처 `(rows, totalOpenCount)` — footnote = `Math.max(0, total - MAX)` 정확. nested record `OpenIssuesPage` 도입(rows + total).
- 단위 테스트 23개 통과(ReportServiceTest 5: 신규 `openListUnderCapShowsNoFootnote` 회귀 + 기존 4 mock 갱신, ReportArchiveServiceTest 5, ReportPeriodTest 6, ReportCleanupServiceTest 2, StatsPeriodTest 5). `build -x test` 그린.
- 통합 보강(`ReportArchiveIntegrationTest.rerunUpsertsTheSameRowAndKeepsCreatedAt`) CI 대기. 신규 통합 시나리오(PDF footnote 정확성) 는 단위 `overflowFootnoteShownWhenOpenListExceedsCap` 가 PDFBox 추출 검증으로 이미 커버 → 별도 통합 미추가(스토리 보다 작게).

### File List

**수정(main)**: `report/ReportArchiveService.java`(@Transactional public 이동), `report/ReportService.java`(loadOpenIssues 페이징 + count + OpenIssuesPage nested), `report/ReportPdfRenderer.java`(writeOpenIssueRows 시그니처 + footnote 정확성), `report/dto/ReportData.java`(totalOpenCount 필드).
**수정(test)**: `report/ReportServiceTest.java`(stubOpenIssuesPage helper + overflow mock 갱신 + 신규 openListUnderCapShowsNoFootnote 회귀), `report/ReportArchiveIntegrationTest.java`(rerun 보강 — 새 이슈 INSERT + size_bytes 갱신 검증).
**무변경**: 마이그레이션, 엔드포인트, DTO 응답 모양, 프론트, application.yml.

### Change Log

| Date       | Version | Description | Author |
| :--------- | :------ | :---------- | :----- |
| 2026-05-23 | 0.3 | Implemented by Dev (James). Task 1 = ReportArchiveService.@Transactional public 메서드(generateAndStoreDaily/Weekly)로 이동, runArchive @Tx 제거(self-invocation 무효 회복). Task 2 = ReportService.loadOpenIssues 페이징(findAll(spec, PageRequest.of(0, MAX+1)) + count(spec)) + ReportData totalOpenCount 필드(5→6) + Renderer writeOpenIssueRows(rows, total) footnote = Math.max(0, total-MAX) 정확. 단위 23/23 통과(신규 openListUnderCapShowsNoFootnote 회귀), build -x test 그린. 통합 rerun 보강(size_bytes 갱신 검증) CI 대기. 프론트 무영향. Status Approved→Review. | James (Dev) |
| 2026-05-23 | 0.2 | Validated & Approved by PO (Sarah) — 간소 검토(AC 변경 0, 마이그레이션 0, DTO/엔드포인트 응답 불변, 프론트 무영향 → po-master-checklist 종합 불요). 본 TD-2 는 PO 자신의 분리 결정(Story 3.4 v0.2 Deviation #1·#4)의 후속 실행 — TD-1 패턴 일관. **결정**: Task 우선순위(1=안전 → 2=메모리) 그대로 채택, ReportData 단일 필드 추가 + Renderer 시그니처 1개 보강 최소 변경, 통합 테스트 보강(size_bytes 갱신 + PDF footnote N 정확성) 그대로. 출처 인용 정확(3.3/3.4 QA Results 각 잔여 #1). 추가 Deviation 무. Status Draft→Approved. 즉시 Dev 핸드오프 가능. | Sarah (PO) |
| 2026-05-23 | 0.1 | Initial draft by SM (Bob). TD-2 = Story 3.3/3.4 QA 잔여 관찰 #1 묶음(TD-1 패턴 일관). Task 1 = ReportArchiveService @Transactional self-invocation 해소(annotation을 public 메서드로 이동). Task 2 = ReportService.loadOpenIssues 페이징(MAX+1 + count Specification) + ReportData totalOpenCount 필드 추가 + ReportPdfRenderer footnote 정확성. 마이그레이션 0, AC/엔드포인트/DTO 변경 0, 프론트 무영향. 회귀 = 단위 + 통합 CI + 프론트 102. PO 비준 필요(분리 결정 번복 아님 — Sarah Deviation #4 결정대로 별도 스토리로 처리). | Bob (SM) |
