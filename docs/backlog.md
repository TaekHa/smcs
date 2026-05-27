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
| SW-002 | **P2** | `N` 단축키(이슈 등록) 미구현 | (1) `/issues` 진입 (2) `N` 키 입력 → 아무 동작 없음 (기대: `/issues/new` 이동) | `golden-path.md` L19 + 키보드 AC6 Step 2 명시이나 hotkey 핸들러 어디에도 없음. **AC6 키보드 골든 패스 첫 진입 불가**. 우회: Tab으로 "신규" 버튼 도달 | OPEN |
| SW-003 | **P2** | V3 시드 카테고리 keywords 빈 배열 → 4.2 자동 제안 미동작 | (1) AGENT 가 새 이슈 등록 폼 진입 (2) 제목/내용에 "wifi"/"인터넷" 등 키워드 입력 → 카테고리 자동 제안 0건 | `V3__seed_categories.sql` 의도적 `'[]'::jsonb` (Story 1.2 결정). 자동 카테고리 = ADMIN 사전 키워드 시드 필요인데 **사용자 테스트 환경 준비 절차 미문서화** → 4.2 AC 검증 불가. 우회: ADMIN 이 사전 `/admin/categories` 키워드 입력 | OPEN |
| SW-004 | **P2** | Jackson UTF-8 파싱 실패 시 500 INTERNAL_ERROR (기대: 400) | (1) `POST /api/issues` 에 invalid UTF-8 byte 포함 페이로드 전송 (2) 응답 500 INTERNAL_ERROR | `HttpMessageNotReadableException`(JsonParseException) 가 `GlobalExceptionHandler` 에서 500 으로 catch. 정상 매핑은 400 BAD_REQUEST(클라이언트 오류). 우회: 정상 UTF-8 사용 시 영향 0 — 그러나 운영 환경에서 우발적 클라이언트 인코딩 버그 시 trace ID 만 보고 원인 추적 어려움 | OPEN |
| SW-005 | P3 | 시드 displayName 과 `golden-path.md`/`user-guide.md` persona 불일치 | (1) `agent1` 로그인 → 화면에 "김상담1" 표시 / 문서는 "박지영" 명시 | front-end-spec §3 페르소나(박지영/김민호/이수진) vs `LocalDataSeeder` 시드(김상담1/이현장1/관리자). 사용자 테스트 시 페르소나 이름 혼란. 우회: 시드 또는 docs 일치화 | OPEN |
| SW-006 | P3 | `golden-path.md` Stage 4 "완료 처리" 단일 액션 표기, 실은 2단계 전이 필요 | docs L49 "사진 추가 → 카메라 → 3장 → 조치 코멘트 → 완료 처리" | 실 워크플로 = (1) 시작(ASSIGNED→IN_PROGRESS) + (2) 완료(IN_PROGRESS→DONE). SW-001 fix 후 모바일 UI 가 2단계 자동 chain 하면 docs 그대로 유효. 별개로 데스크탑 IssueDetailView 는 명시적 2 클릭 필요(NEXT_STATES). | **SW-001 fix 시 동시 해소 가능** |
| SW-007 | P3 | `GET /api/categories?level=N&parentId=M` 의 `parentId` 무효 | (1) `parentId=999`(존재하지 않는 부모) 로 호출 → 빈 배열이 아니라 해당 level 의 전체 목록 반환 | parent_id 가 V3에서 NULL 통일(자유 조합 결정, Story 1.2). 그러나 컨트롤러 시그니처에 `parentId` 파라미터가 살아있어 클라이언트 측 오해 유발. 우회: 클라이언트가 level 만 사용. v2 시 시그니처 정리 권장 | OPEN |

## v2 트리거 (deferred)

- **이슈 트래커**: `docs/backlog.md` → Linear/Jira 마이그레이션 (현재 MVP 단순화, Deviation #3)
- **PDF 다운로드 한글 파일명** (Story 3.5 잔여 관찰 #3, RFC 5987)
- **antd Table jsdom warning** 제거 (라이브러리 한계, 모든 4.x 스토리 공통)
- **PreventFurtherUsage budget guardrail** 자동 감지/알림 (lesson #9)
- **Category 엔티티 timestamps + name unique** (Story 4.5 잔여 관찰 #2/#3)

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
