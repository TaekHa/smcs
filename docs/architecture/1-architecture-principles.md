# 1. Architecture Principles

| # | 원칙 | 적용 방식 |
|---|------|----------|
| 1 | **Holistic System Thinking** | 사용자 여정(접수 → 배정 → 조치 → 보고서)을 한 코드베이스에서 일관되게 표현 |
| 2 | **User Experience Drives Architecture** | 모바일 현장 작업자의 약한 네트워크가 1순위 제약. 이미지 클라이언트 리사이즈, 업로드 진행률, 재시도 필수 |
| 3 | **Pragmatic Technology Selection** | Spring Boot/React/Postgres = 보수적. WebSocket/Kafka/Redis Cluster = 미사용 (v2 옵션) |
| 4 | **Progressive Complexity** | 패키지 단위 도메인 분리로 v2에서 서비스 분리 시 비용 최소화 |
| 5 | **Cross-Stack Performance** | DB 인덱스(우선순위/접수일 복합), JPA fetch 전략, 프론트 TanStack Query 캐싱 |
| 6 | **Developer Experience First** | `docker compose up` 한 줄로 로컬 실행. Flyway + 시드. README 1페이지로 시작 가능 |
| 7 | **Security at Every Layer** | HTTPS → JWT → 메서드 보안 → 컬럼 암호화 → 감사 로그 → EXIF 스트립 |
| 8 | **Data-Centric Design** | 이슈 라이프사이클이 도메인 중심. IssueEvent로 audit 우선 확보 |
| 9 | **Cost-Conscious Engineering** | 단일 서버 + 단일 컨테이너. 별도 메시지 큐/Redis Cluster/외부 SMTP 비용 0 |
| 10 | **Living Architecture** | Notification 모듈을 인앱 전용으로 구현하되 추후 외부 채널 어댑터 추가 가능한 인터페이스 유지 |

---
