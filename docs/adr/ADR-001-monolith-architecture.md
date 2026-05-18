# ADR-001: Monolith 아키텍처 채택

- **Status:** Accepted
- **Date:** 2026-05-15
- **Author:** Architect Winston
- **관련 문서:** PRD §4.2, ARCHITECTURE §3

## Context

SMCS는 1인 풀스택 개발자가 1개월 내에 MVP를 완성해야 하는 내부 도구다. 사용자 규모는 70명(CS팀 20 + 현장 50)이며, 사내 폐쇄망 단일 호스트에 배포된다. 도메인은 이슈 라이프사이클, 인증, 알림, 보고서, 카테고리 관리로 비교적 단순하다.

## Decision

**Spring Boot 단일 애플리케이션(Monolith) + 단일 저장소(Monorepo)**로 시작한다. 마이크로서비스, 서버리스, BFF 패턴 등은 도입하지 않는다.

다만 **도메인 패키지 분리 원칙**을 엄격히 적용하여 향후 분리 비용을 낮춘다:
- 각 도메인(`issue`, `user`, `comment`, `notification`, `report` 등)은 자기 완결적 패키지로 구성
- 도메인 간 의존은 Service 레이어를 통해서만 (다른 도메인의 Repository/Entity 직접 접근 금지)

## Consequences

### 긍정적
- ✅ 1인 운영에 단순 — 단일 배포, 단일 로그, 단일 DB 트랜잭션
- ✅ 트랜잭션 경계 명확 (이슈 등록 + IssueEvent + Notification을 한 트랜잭션에 묶음)
- ✅ 폐쇄망 운영 비용 0 (메시지 큐, 서비스 디스커버리 등 불필요)
- ✅ 개발자 경험 최상 (`docker compose up` 한 번에 로컬 실행)

### 부정적
- ⚠️ 단일 호스트 SPOF — 호스트 장애 시 전체 중단 (NFR5 99% 가용성에 적정. 99.9%+는 불가)
- ⚠️ 수직 확장 한계 — 70명 → 1,000명 이상 성장 시 분리 필요
- ⚠️ 단일 코드베이스에서 도메인 경계 위반 위험 → 코드 리뷰 또는 ArchUnit 같은 도구로 검증 (v2)

### 진화 경로
- v2에서 `report`, `notification`이 다른 도메인보다 부담을 줄 때 가장 먼저 분리
- DB는 같은 PostgreSQL에서 스키마 분리 → 별도 DB → 별도 호스트 단계로 점진적 진화

## Alternatives Considered

### 대안 1: 마이크로서비스 (Issue/Notification/Report 분리)
- ❌ 기각 — 1인 1개월 일정에 운영 부담(서비스 디스커버리, 분산 트랜잭션, 다중 배포)이 가치 대비 너무 큼
- 70명 규모에서 분리의 이득(독립 확장)이 거의 없음

### 대안 2: BFF + 백엔드 분리
- ❌ 기각 — Backend for Frontend는 다중 클라이언트(웹/모바일 앱 등)가 있을 때 유용. SMCS는 단일 SPA + 모바일 반응형이므로 불필요

### 대안 3: Serverless (AWS Lambda 등)
- ❌ 기각 — 폐쇄망 + 외부 의존성 0 원칙에 위배
