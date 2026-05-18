# 13. Migration & Future Evolution Path

| 가능성 | v2 트리거 | 아키텍처 변경 |
|--------|----------|--------------|
| 사용자 1,000명+ | 성장 | Backend 수평 확장 (Stateless 유지, JWT 변경 불필요), DB read replica |
| 실시간 알림 필요 | UX 피드백 | WebSocket(STOMP) 또는 SSE 추가. NotificationModule에 publisher 인터페이스 추가 |
| 외부 채널 통합 | 사용자 요청 | `NotificationChannel` 인터페이스에 KakaoworkChannel/SlackChannel 구현 추가 |
| SSO | 보안 정책 | Spring Security OAuth2 Resource Server 추가, 자체 인증과 병행 가능 |
| 보고서 데이터 폭증 | 1년+ 누적 | `report_daily_snapshot` 테이블 도입, 매일 집계 미리 저장 |
| 카테고리 매트릭스 룰 | 운영 분석 | `category_combination` 테이블 도입 |
| GPS/현장 위치 | v2 요구 | `issue_location` 별도 테이블, EXIF 보존 정책 재검토 (현재는 스트립) |
| 마이크로서비스 분리 | 팀 규모 증가 | `report`, `notification` 부터 분리. 이미 도메인 패키지 경계 명확 |

> **핵심:** MVP는 단순하게 만들었지만, **도메인 경계가 명확**하므로 모든 진화 경로의 비용이 낮다.

---
