# 5. Data Architecture

## 5.1 ER Diagram

```mermaid
erDiagram
    USERS ||--o{ ISSUES : "creates"
    USERS ||--o{ ISSUES : "assigned_to"
    USERS ||--o{ COMMENTS : "authors"
    USERS ||--o{ ATTACHMENTS : "uploads"
    USERS ||--o{ ISSUE_EVENTS : "performs"
    USERS ||--o{ NOTIFICATIONS : "recipient"

    ISSUES ||--o{ COMMENTS : "has"
    ISSUES ||--o{ ATTACHMENTS : "has"
    ISSUES ||--o{ ISSUE_EVENTS : "has"
    ISSUES ||--o{ NOTIFICATIONS : "references"

    CATEGORIES ||--o{ CATEGORIES : "parent_of"
    CATEGORIES ||--o{ ISSUES : "L1"
    CATEGORIES ||--o{ ISSUES : "L2"
    CATEGORIES ||--o{ ISSUES : "L3"

    REPORTS {
        bigint id PK
        string kind "DAILY|WEEKLY"
        date period_start
        date period_end
        string file_path
        timestamp generated_at
    }

    USERS {
        bigint id PK
        string username UK
        string password_hash
        string display_name
        string role "AGENT|FIELD|ADMIN"
        string phone "nullable"
        boolean active
        timestamp created_at
        timestamp updated_at
    }

    ISSUES {
        bigint id PK
        string title
        text description
        bytes caller_name_enc "AES-GCM"
        bytes caller_phone_enc "AES-GCM"
        string caller_phone_hash "HMAC-SHA256, indexed"
        bigint category_l1_id FK
        bigint category_l2_id FK
        bigint category_l3_id FK
        string priority "URGENT|HIGH|NORMAL|LOW"
        string status
        bigint created_by FK
        bigint assigned_to FK "nullable"
        timestamp resolved_at "nullable"
        timestamp created_at
        timestamp updated_at
    }

    CATEGORIES {
        bigint id PK
        bigint parent_id FK "nullable"
        int level "1|2|3"
        string name
        jsonb keywords
        int sort_order
        boolean active
    }

    COMMENTS {
        bigint id PK
        bigint issue_id FK
        bigint author_id FK
        text body
        string kind "NOTE|FIELD_ACTION|SYSTEM"
        timestamp created_at
    }

    ATTACHMENTS {
        bigint id PK
        bigint issue_id FK
        bigint uploader_id FK
        string filename "UUID 기반"
        string original_name
        string mime_type
        bigint size_bytes
        timestamp created_at
    }

    ISSUE_EVENTS {
        bigint id PK
        bigint issue_id FK
        bigint actor_id FK
        string event_type
        string from_value "nullable"
        string to_value "nullable"
        timestamp created_at
    }

    NOTIFICATIONS {
        bigint id PK
        bigint recipient_id FK
        string kind
        bigint issue_id FK
        bigint actor_id FK "nullable"
        string message
        timestamp read_at "nullable"
        timestamp created_at
    }
```

## 5.2 핵심 인덱스 전략

| 테이블 | 인덱스 | 사용처 |
|--------|--------|--------|
| `issues` | `(priority, created_at)` 복합 | 기본 정렬 (PRD FR4) |
| `issues` | `(status)` | 상태 필터 |
| `issues` | `(assigned_to, status)` | 현장 작업자 모바일 홈 |
| `issues` | `(category_l1_id)`, `(category_l2_id)`, `(category_l3_id)` | 카테고리 필터 |
| `issues` | `(caller_phone_hash)` | 발신자 전화번호 검색 |
| `issues` | `(created_at)` | 보고서 집계 |
| `comments` | `(issue_id, created_at)` | 활동 로그 시간순 |
| `attachments` | `(issue_id)` | 첨부 조회 |
| `issue_events` | `(issue_id, created_at)` | audit timeline |
| `notifications` | `(recipient_id, read_at)` | 미읽음 카운트 |
| `notifications` | `(recipient_id, created_at)` | 알림 리스트 |
| `categories` | `(parent_id, level, sort_order)` | 계층 조회 |

## 5.3 검색 전략 (PRD FR15)

| 검색 대상 | 방식 | 비고 |
|----------|------|------|
| 제목 | `ILIKE '%kw%'` | PostgreSQL trigram 인덱스 검토 (선택) |
| 본문 | `ILIKE '%kw%'` | 동상 |
| 발신자 이름 | **검색 불가 (MVP)** | 평문 노출 위험 vs 검색 가치 트레이드오프. 정확 매칭만 v2에서 추가 |
| 발신자 전화번호 | **HMAC 해시 정확 매칭** | 정규화(숫자만) 후 HMAC-SHA256 → `caller_phone_hash` 컬럼 비교 |

> 부분 매칭 전화번호 검색은 보안상 미지원. "010-1234-5678" 입력 시 정확히 그 번호만 매칭.

## 5.4 시드 데이터

- **프로덕션 시드** (Flyway `V2__seed_categories.sql`):
  - L1: 아파트먼트v1, 아파트먼트v2, voip/pbx
  - L2: 관리자웹, 입주민앱, 단말, 서버
  - L3: 기기미동작, 기기오동작, 로그인오류
  - keywords는 빈 배열로 시작 → 운영하면서 Admin이 채움
- **개발 시드** (`application-local.yml` 활성 시 `DataLoader`):
  - 사용자 8명, 샘플 이슈 20건 (다양한 우선순위/카테고리/상태)

## 5.5 데이터 보존 정책

| 데이터 | 보존 기간 | 정리 방식 |
|--------|----------|----------|
| Issue, Comment, Attachment, IssueEvent | **영구** | 삭제하지 않음 |
| Notification | 90일 | 일일 cron `cleanup-old-files.sh` |
| Report PDF | 90일 | 일일 cron |
| Audit Log (별도 로그파일) | 1년 | logrotate |
| DB 백업 | 30일 | `backup-db.sh` 일일 실행, 30일치 보관 |

---
