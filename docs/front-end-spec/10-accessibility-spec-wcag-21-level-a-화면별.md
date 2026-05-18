# 10. Accessibility Spec (WCAG 2.1 Level A 화면별)

| 화면 | 체크 항목 |
|------|----------|
| **로그인** | username 자동 포커스 / Enter로 제출 / 비밀번호 보기 토글 / 에러 메시지 `role="alert"` |
| **이슈 리스트** | 테이블 헤더 `<th scope="col">` / 정렬 변경 시 `aria-sort` / 행 선택 키보드 가능 / 우선순위 색상 + 텍스트 라벨 |
| **등록 폼** | 모든 input `<label>` 명시 / 필수 표시 `aria-required="true"` / 에러 메시지 `aria-describedby` / 카테고리 모달 진입 시 자동 포커스, 닫힐 때 트리거 버튼으로 복귀 |
| **이슈 상세** | 진행 바 `role="progressbar"` + `aria-valuenow` / 활동 로그 `role="log"` `aria-live="polite"` (실시간 추가 시) |
| **모바일 카드** | `role="button"` + `tabIndex=0` + Enter/Space 키 처리 / 우선순위 `aria-label="긴급 우선순위"` |
| **모바일 상세** | 사진 추가 버튼 `aria-label="사진 추가"` / textarea `<label>` 명시 / 완료 버튼 비활성 상태 `aria-disabled="true"` + 이유 안내 |
| **대시보드** | 차트는 표 형태 데이터 대체 제공 (`<details>` 안에 `<table>`) / KPI 화살표는 텍스트 라벨 병기 ("증가/감소") |
| **알림 드롭다운** | `<Dropdown>` 키보드 네비게이션 / `role="menuitem"` / 새 알림 도착 시 `role="status"` 라이브 영역 |
| **모달** | 진입 시 자동 포커스 / Esc 닫기 / 닫힐 때 트리거로 복귀 / focus trap |

**색상 대비 검증:**
- 모든 텍스트 vs 배경 4.5:1 이상 (Level AA 권장. MVP Level A는 3:1)
- Ant Design 기본 토큰은 대부분 충족 — 단, gray-6 (`#8c8c8c`) 텍스트는 흰 배경에서 4:1로 경계선. **본문에는 gray-9 사용 권고**

---
