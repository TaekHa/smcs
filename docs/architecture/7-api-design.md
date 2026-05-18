# 7. API Design

## 7.1 설계 원칙

- **REST**, 자원 중심 URL
- **버전 prefix 없음 (MVP)**. v2 도입 시 `/api/v2/...` 추가
- **인증:** 모든 `/api/*` 는 JWT 필수. `permitAll`: `/api/auth/login`, `/api/health`
- **에러 응답:** 표준 포맷
  ```json
  {
    "code": "ISSUE_NOT_FOUND",
    "message": "이슈를 찾을 수 없습니다.",
    "traceId": "abc-123"
  }
  ```
- **페이징:** `?page=0&size=50&sort=priority,desc&sort=createdAt,asc` (Spring Pageable 표준)
- **시간:** ISO-8601 + 오프셋. 서버는 UTC 저장, 응답은 KST(+09:00)
- **검증:** Bean Validation (`@NotBlank`, `@Size`). 실패 시 400 + 필드별 메시지

## 7.2 핵심 흐름별 API

(PRD §6 에 전체 표 있음. 본 문서는 설계 의도만 보강)

| 엔드포인트 | 설계 노트 |
|-----------|----------|
| `POST /api/issues` | 트랜잭션 내에서: ① 이슈 저장 ② IssueEvent(CREATED) 저장 ③ (배정자 있으면) Notification 저장 |
| `POST /api/issues/{id}/transition` | 요청 body: `{ "to": "DONE", "reason": "..." }`. 서버에서 현재 상태 확인 → 유효 전이만 허용 |
| `POST /api/issues/{id}/assign` | `{ "assigneeId": 5 }`. 트랜잭션: 배정 + status=ASSIGNED + Notification |
| `POST /api/issues/{id}/comments` | 트랜잭션: 코멘트 저장 + (작성자 외 관련자에게) Notification |
| `POST /api/issues/{id}/attachments` | Multipart. EXIF 스트립 후 디스크 저장 + DB 메타데이터 |
| `GET /api/notifications/unread-count` | 응답: `{ "count": 7 }`. 단일 COUNT 쿼리. **인덱스 적중 보장** |
| `GET /api/reports/daily?date=...` | Nginx X-Accel-Redirect로 PDF 서빙 |

## 7.3 응답 캐싱 정책

| 엔드포인트 | 캐시 |
|-----------|------|
| 정적 자원 (JS/CSS/이미지) | `Cache-Control: public, max-age=31536000, immutable` (Vite 해시 빌드) |
| `GET /api/*` | 캐시 안 함 (`Cache-Control: no-store`) — 내부 도구 신선도 우선 |
| `GET /files/*` (첨부 이미지) | `Cache-Control: private, max-age=3600` (1시간) |

---
