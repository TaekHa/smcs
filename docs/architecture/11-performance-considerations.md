# 11. Performance Considerations

## 11.1 목표 vs 예상

| 메트릭 | PRD 목표 | 아키텍처 분석 | 충분성 |
|--------|---------|-------------|--------|
| 동시 사용자 | 70명 | Spring Boot 단일 인스턴스 (Tomcat 200 thread default) | ✅ 충분 |
| 페이지 응답 P95 | < 1.5초 | DB 인덱스 적중 + Nginx HTTP/2 | ✅ 충분 |
| 통계 API P95 | < 500ms | 일/주 단위 집계, 인덱스 사용, 캐시 없음 | ✅ MVP 충분, v2에서 materialized view 검토 |
| 모바일 사진 업로드 | 진행률 표시 + 재시도 | 클라이언트 리사이즈 + axios onUploadProgress | ✅ |
| 이슈 등록 → 알림 카운트 반영 | < 30초 (polling) | 동일 트랜잭션 INSERT + 30s polling | ✅ |
| **월간 가동률** | **99%** (NFR5) | 단일 호스트 + 일일 백업 + RTO 4h | ✅ 99% 달성 가능 (99.9%+ 불가 — 단일 호스트 SPOF) |
| **RTO** | **4시간** | Docker Compose 재기동 + pg_restore + 파일 rsync | ✅ |
| **RPO** | **24시간** | 일일 02:00 백업 | ✅ |

## 11.2 잠재 병목

- **이슈 리스트 ILIKE 검색** — 50,000건 이상 시 trigram 인덱스 도입 검토 (v2)
- **30초 polling 부하** — 70명 × 분당 2회 = 140 req/min. 부하 무시 가능
- **PDF 생성** — 70명 1일치 데이터로 PDF는 1초 미만 예상. 1년 누적 시 점진적 슬로우 가능 → 보고서 데이터 캐싱(v2)
- **이미지 업로드 트래픽** — 평균 1~2MB(리사이즈 후) × 1일 50건 = 무시 가능

## 11.3 성능 모니터링

| 도구 | 용도 |
|------|------|
| Spring Boot Actuator | `/actuator/health`, `/actuator/metrics` (내부 토큰 보호) |
| PostgreSQL `pg_stat_statements` | 슬로우 쿼리 추적 |
| Nginx access log | 응답 시간 P50/P95 모니터링 |
| 외부 APM | **미사용** (외부 의존성 0 원칙) |

---
