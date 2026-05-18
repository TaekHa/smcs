# Epic 1: Foundation & Auth (Week 1)

**Goal:** Spring Boot + React 프로젝트를 셋업하고, JWT 기반 인증과 3가지 역할(`AGENT`, `FIELD`, `ADMIN`)을 동작시키며, 사용자가 로그인하여 빈 대시보드를 볼 수 있는 상태에 도달한다. 이 Epic의 끝에서 시스템은 배포 가능하며, 이후 Epic의 기반이 된다.

## Story 1.1 프로젝트 셋업 및 헬스 체크

As a 개발자,
I want Spring Boot 백엔드와 React 프론트엔드를 모노레포로 셋업하고 헬스 체크 엔드포인트를 노출하기를,
so that 이후 모든 기능 개발의 기반이 마련된다.

### Acceptance Criteria

- 1: `backend/` 디렉터리에 Spring Boot 3.x + Gradle + Java 21 프로젝트가 셋업되어 있다.
- 2: `frontend/` 디렉터리에 Vite + React 18 + TypeScript 프로젝트가 셋업되어 있다.
- 3: 백엔드 `GET /api/health` 가 `{ "status": "UP" }` 반환한다.
- 4: 프론트엔드 `/` 접속 시 "SMCS" 타이틀과 헬스체크 결과를 표시하는 캔버스 페이지가 보인다.
- 5: Docker Compose로 backend + PostgreSQL을 띄울 수 있다.
- 6: Flyway 가 셋업되어 있고, 빈 마이그레이션 파일 1개로 부팅 가능하다.
- 7: README에 로컬 실행 방법이 1페이지로 정리되어 있다.

## Story 1.2 DB 스키마 및 시드 데이터

As a 개발자,
I want 핵심 엔티티(User, Issue, Comment, Attachment, IssueEvent, Category(L1/L2/L3), Notification) DB 테이블을 Flyway로 생성하고 시드 데이터를 자동 로딩하기를,
so that 이후 개발과 테스트에 즉시 사용할 수 있다.

### Acceptance Criteria

- 1: Flyway 마이그레이션으로 7개 테이블이 생성된다: `users`, `issues`, `comments`, `attachments`, `issue_events`, `categories` (self-reference), `notifications`.
- 2: 외래키, 인덱스가 정확히 설정되어 있다. 특히 `issues(priority, created_at)` 복합 인덱스, `notifications(recipient_id, read_at)` 인덱스 포함.
- 3: **카테고리 시드 데이터(프로덕션 포함)**: L1 3건(`아파트먼트v1`, `아파트먼트v2`, `voip/pbx`), L2 4건(`관리자웹`, `입주민앱`, `단말`, `서버`), L3 3건(`기기미동작`, `기기오동작`, `로그인오류`).
- 4: `application-local.yml` 프로파일에서 추가 시드(사용자 8명: AGENT 3 + FIELD 4 + ADMIN 1, 샘플 이슈 20건)가 로딩된다.
- 5: 프로덕션 프로파일에서는 사용자/이슈 시드는 로딩되지 않는다(카테고리는 로딩됨).
- 6: `SlaPolicy` 테이블은 만들지 않는다 (v0.2에서 제거).

## Story 1.3 JWT 인증과 역할 기반 권한

As a 사용자,
I want 사용자명/비밀번호로 로그인하여 JWT 토큰을 받고, 내 역할에 따라 화면 접근이 제한되기를,
so that 보안과 적절한 권한 분리가 보장된다.

### Acceptance Criteria

- 1: `POST /api/auth/login` 이 username/password로 JWT를 발급한다.
- 2: JWT에 `userId`, `role`, `exp` 클레임이 포함된다(만료 8시간).
- 3: 비밀번호는 BCrypt 해시로 저장된다.
- 4: `GET /api/me` 가 JWT를 검증하고 현재 사용자 정보를 반환한다.
- 5: Spring Security가 `@PreAuthorize("hasRole('ADMIN')")` 같은 메서드 보안을 지원한다.
- 6: 인증 실패 시 401, 권한 부족 시 403을 표준 에러 포맷으로 반환한다.
- 7: **로그인 Rate Limiting:** 동일 username 또는 동일 IP에 대해 `/api/auth/login` 실패 5회 누적 시 10분 lockout. 성공 시 카운터 초기화.
- 8: **일반 API Rate Limiting:** 동일 사용자(JWT sub) 기준 `/api/*` 분당 300 요청 제한 (Polling을 고려한 여유).

## Story 1.4 로그인 화면 및 인증 컨텍스트(프론트)

As a 사용자,
I want 로그인 화면에서 인증한 후 토큰이 자동으로 모든 API 호출에 첨부되기를,
so that 내가 매번 토큰을 신경 쓰지 않고 작업할 수 있다.

### Acceptance Criteria

- 1: 로그인 화면이 username/password 폼과 에러 메시지 영역을 제공한다.
- 2: 로그인 성공 시 JWT를 메모리 + localStorage에 저장하고 메인 화면으로 이동한다.
- 3: `AuthProvider` 컨텍스트가 현재 사용자 정보를 앱 전체에 제공한다.
- 4: API 클라이언트가 모든 요청에 `Authorization: Bearer <token>` 헤더를 자동 첨부한다.
- 5: 토큰 만료/401 응답 시 자동 로그아웃 + 로그인 화면 리다이렉트.
- 6: 로그아웃 버튼이 동작한다.

## Story 1.5 역할별 빈 메인 화면 라우팅

As a 사용자,
I want 내 역할에 따라 로그인 후 적절한 메인 화면(빈 페이지여도)으로 안내되기를,
so that 이후 Epic 2의 기능이 올바른 곳에 배치된다.

### Acceptance Criteria

- 1: AGENT/ADMIN은 로그인 후 `/issues` (빈 리스트 화면)로 이동한다.
- 2: FIELD는 로그인 후 `/m` (빈 모바일 홈 화면)으로 이동한다.
- 3: 권한 없는 라우트 접근 시 403 화면을 표시한다.
- 4: 상단 네비게이션 바에 사용자명, 역할, 로그아웃 버튼이 표시된다.

---
