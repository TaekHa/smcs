# SMCS Screenshots

Story 4.7 Task 4(사용자 테스트) 시점에 시드 데이터로 캡처하여 추가하는 PNG 폴더.

## 캡처 대상 (참조: `docs/user-guide.md`)

| 파일명 | 화면 | 역할 |
| :----- | :--- | :--- |
| `01-agent-issue-list.png` | 이슈 리스트 + 필터/검색 | AGENT |
| `02-agent-issue-form.png` | 이슈 등록 폼 + 자동 카테고리 제안 | AGENT |
| `03-field-mobile-home.png` | 모바일 본인 작업 카드 스택 | FIELD |
| `04-field-mobile-detail.png` | 모바일 이슈 상세 + 사진 첨부 영역 | FIELD |
| `05-admin-dashboard.png` | 대시보드 KPI + 차트 | ADMIN |
| `06-admin-reports.png` | 보고서 보관함 + PDF 미리보기 | ADMIN |
| `07-bell-dropdown.png` | 헤더 벨 드롭다운(최근 10건) | 공통 |

## 가드레일

- **PII 마스킹 필수**: 실 고객 정보 0. 시드 데이터(`agent1` 김상담1 / `field1` 이현장1 / `admin1` 관리자) 사용.
- 실 환경에서 캡처 부득이할 경우: 이름 → `***`, 전화번호 → `010-****-****` 후처리.
- 해상도: 데스크탑 1440×900, 모바일 390×844 (iPhone 14 표준) 권장.
- 파일 형식: PNG. 크기 1MB 이하로 최적화.
