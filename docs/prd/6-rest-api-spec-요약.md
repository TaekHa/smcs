# 6. REST API Spec (요약)

전체 OpenAPI 명세는 별도 문서로 분리하되, MVP 핵심 엔드포인트는 다음과 같다.

| Method | Path                                    | 설명                          | 권한          |
| :----- | :-------------------------------------- | :---------------------------- | :------------ |
| POST   | `/api/auth/login`                       | 로그인 (JWT 발급)             | Public        |
| POST   | `/api/auth/logout`                      | 로그아웃                      | 인증          |
| GET    | `/api/me`                               | 내 정보                       | 인증          |
| GET    | `/api/issues`                           | 이슈 리스트 (페이징, 필터)    | 인증          |
| POST   | `/api/issues`                           | 이슈 생성                     | AGENT, ADMIN  |
| GET    | `/api/issues/{id}`                      | 이슈 상세                     | 인증          |
| PATCH  | `/api/issues/{id}`                      | 이슈 수정                     | AGENT, ADMIN  |
| POST   | `/api/issues/{id}/assign`               | 담당자 배정                   | AGENT, ADMIN  |
| POST   | `/api/issues/{id}/transition`           | 상태 변경                     | 인증          |
| POST   | `/api/issues/{id}/comments`             | 코멘트 추가                   | 인증          |
| POST   | `/api/issues/{id}/attachments`          | 이미지 업로드                 | 인증          |
| GET    | `/api/issues/{id}/events`               | 활동 로그                     | 인증          |
| GET    | `/api/me/assigned`                      | 내게 배정된 이슈 (모바일용)   | FIELD         |
| GET    | `/api/notifications`                    | 내 알림 리스트 (페이징)       | 인증          |
| GET    | `/api/notifications/unread-count`       | 미읽음 카운트 (벨 뱃지용)     | 인증          |
| POST   | `/api/notifications/{id}/read`          | 알림 읽음 처리                | 인증          |
| POST   | `/api/notifications/read-all`           | 모든 알림 읽음 처리           | 인증          |
| GET    | `/api/reports`                          | 보고서 보관함 리스트          | ADMIN         |
| GET    | `/api/reports/daily?date=YYYY-MM-DD`    | 일간 보고서 PDF 다운로드      | ADMIN         |
| GET    | `/api/reports/weekly?week=YYYY-Www`     | 주간 보고서 PDF 다운로드      | ADMIN         |
| GET    | `/api/stats/dashboard`                  | 대시보드 통계 JSON            | 인증          |
| GET    | `/api/issues/export?format=csv`         | CSV 내보내기                  | ADMIN         |
| GET    | `/api/admin/users`                      | 사용자 관리                   | ADMIN         |
| POST   | `/api/admin/users`                      | 사용자 추가                   | ADMIN         |
| GET    | `/api/admin/categories?level=1\|2\|3`   | 카테고리(레벨별) 조회         | ADMIN         |
| POST   | `/api/admin/categories`                 | 카테고리 추가/수정            | ADMIN         |

---
