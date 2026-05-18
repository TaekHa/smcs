# 9. Coding Standards

## 9.1 Critical Fullstack Rules

- **타입 공유:** API DTO와 프론트엔드 타입은 가능한 동일한 명명과 구조를 유지. 자동 생성을 사용하지 않는 대신, 수동 동기화를 위해 PRD 데이터 모델 섹션을 단일 출처(SSOT)로 사용.
- **API 호출:** 프론트엔드는 직접 fetch 호출 금지. 반드시 `apiClient` 서비스 레이어를 통해 호출(JWT 자동 첨부, 에러 핸들링 일원화).
- **인증 컨텍스트:** 모든 API 엔드포인트는 기본 인증 필수. `@PermitAll` 명시한 엔드포인트만 예외.
- **에러 응답 포맷:** 모든 백엔드 에러는 `{ "code": "ERROR_CODE", "message": "..." }` 표준 포맷.
- **DB 변경:** 모든 스키마 변경은 Flyway 마이그레이션 파일로만 진행. 수동 SQL 금지.
- **시크릿:** 비밀번호/토큰/URL 등은 코드에 하드코딩 금지. `application-prod.yml` 또는 환경변수.
- **로깅:** 개인정보(전화번호, 이름)는 로그에 평문 출력 금지. 마스킹 필수.
- **트랜잭션:** 쓰기 작업은 명시적으로 `@Transactional`. 읽기는 기본 readOnly.
- **상태 업데이트:** React 상태는 불변(immutable) 업데이트만 사용. 직접 mutate 금지.

## 9.2 Naming Conventions

| 요소 | Frontend | Backend | 예시 |
|------|----------|---------|------|
| 컴포넌트 | PascalCase | - | `IssueListView.tsx` |
| Hooks | camelCase + `use` | - | `useIssues.ts` |
| API 경로 | - | kebab-case | `/api/issues/{id}/transition` |
| DB 테이블 | - | snake_case | `issue_events` |
| Java 클래스 | - | PascalCase | `IssueService` |
| Java 패키지 | - | lowercase | `com.smcs.issue` |

---
