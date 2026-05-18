# ADR-005: 30초 Polling 기반 인앱 알림

- **Status:** Accepted
- **Date:** 2026-05-15
- **Author:** Architect Winston
- **관련 문서:** ARCHITECTURE §8.5, PRD FR11, NFR12

## Context

PRD FR11은 인앱 알림을 요구한다 (외부 채널 없음, ADR-002). 알림 방식 결정이 필요하다:

- **목표:** 사용자에게 새 이슈 배정/코멘트/상태 변경을 적당히 빠르게 알림
- **제약:** 외부 의존성 0 (메시지 큐, 푸시 서비스 X), 1인 1개월 일정, 폐쇄망 단일 호스트

## Decision

**Frontend가 30초 주기로 백엔드 카운트 API를 polling**한다. WebSocket/SSE는 사용하지 않는다.

구체적으로:
- 알림 생성: 백엔드가 트랜잭션 내에서 `notifications` 테이블 INSERT
- 알림 수신: `GET /api/notifications/unread-count` 를 30초 주기 polling
- TanStack Query `refetchInterval: 30_000` + `refetchIntervalInBackground: false` (비활성 탭 중단)
- 카운트가 0보다 크면 벨 아이콘 뱃지 표시
- 사용자가 벨 클릭 시 `GET /api/notifications` 로 리스트 페치
- 알림 클릭 시 `POST /api/notifications/{id}/read` 로 읽음 처리

## Consequences

### 긍정적
- ✅ 구현 단순 — 표준 REST + TanStack Query만 사용, 별도 인프라 0
- ✅ 폐쇄망/방화벽 친화적 — HTTP만 사용, 추가 포트 불필요
- ✅ 실패 복구 자동 — 일시적 네트워크 오류는 다음 polling에서 회복
- ✅ 부하 예측 가능 — 70명 × 분당 2회 = 분당 140 req (DB COUNT 쿼리, 인덱스 적중)

### 부정적
- ⚠️ 지연 최대 30초 — 즉시성 부족. CS/필드 협업 시 적정 (실시간 채팅 아님)
- ⚠️ 사용자 0명이어도 트래픽 발생 — 비활성 탭에서 중단으로 완화
- ⚠️ 카운트 쿼리 효율성에 인덱스 의존 — `notifications(recipient_id, read_at)` 인덱스 필수

### 운영 메트릭
- DB COUNT 쿼리 P95 < 10ms (인덱스 적중 시)
- 백엔드 Tomcat thread 사용량 < 5% (분당 140 req)
- v2에서 WebSocket 도입 시 NotificationModule의 publisher 인터페이스만 추가 (큰 재구성 불필요)

### 진화 경로
- v2 트리거 1: 사용자 200명+ — DB COUNT 쿼리 부담 증가 시 Redis 카운터 캐시 도입
- v2 트리거 2: 실시간성 요구 — WebSocket(STOMP over SockJS) 또는 SSE 도입
- v2 트리거 3: 모바일 푸시 필요 — 네이티브 앱 + FCM (외부 의존성 도입 정책 결정 필요)

## Alternatives Considered

### 대안 1: WebSocket (STOMP)
- ❌ 기각 — 1인 1개월 일정에 부담 (연결 관리, 재연결, 인증, 로드밸런서 sticky session 등). MVP에 과함

### 대안 2: SSE (Server-Sent Events)
- ❌ 기각 — WebSocket보다는 단순하지만 Nginx 버퍼링 설정 필요, 연결 수 관리 부담. 30s polling 대비 이득 작음

### 대안 3: Long Polling
- ❌ 기각 — 일반 Polling 대비 즉시성 ↑이지만 백엔드 thread 점유 시간 ↑. Tomcat 기본 thread pool 부담

### 대안 4: 더 짧은 polling (10초)
- ❌ 기각 — 트래픽 3배(분당 420 req)인데 30초 → 10초의 UX 이득은 한계적. 30초가 sweet spot

### 대안 5: Push 알림 (FCM/APNs)
- ❌ 기각 — ADR-002 외부 의존성 0 원칙 위배. 네이티브 앱 필요
