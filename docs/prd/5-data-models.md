# 5. Data Models

## 5.1 User

**Purpose:** 시스템 사용자(CS 접수자, 현장 작업자, 관리자) 정보 관리.

**Key Attributes:**
- `id`: Long — Auto-increment PK
- `username`: String — 로그인 ID (unique)
- `passwordHash`: String — BCrypt 해시
- `displayName`: String — 표시 이름
- `role`: Enum — `AGENT` / `FIELD` / `ADMIN`
- `phone`: String (optional, 암호화) — 사내 연락처
- `active`: Boolean — 활성/비활성
- `createdAt`, `updatedAt`: Timestamp

**TypeScript Interface:**
```typescript
type Role = "AGENT" | "FIELD" | "ADMIN";

interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  phone?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
```

**Relationships:**
- Has many Issues (작성자/담당자 양쪽)
- Has many Comments

## 5.2 Issue

**Purpose:** 핵심 도메인 엔티티. 고객 CS 이슈의 라이프사이클 전체를 표현.

**Key Attributes:**
- `id`: Long
- `title`: String — 이슈 제목
- `description`: Text — 상세 내용
- `callerName`: String (암호화) — 발신자명
- `callerPhone`: String (암호화) — 발신자 전화번호
- `categoryL1Id`: Long (FK) — 대분류
- `categoryL2Id`: Long (FK) — 중분류
- `categoryL3Id`: Long (FK) — 소분류
- `priority`: Enum — `LOW` / `NORMAL` / `HIGH` / `URGENT`
- `status`: Enum — `NEW` / `ASSIGNED` / `IN_PROGRESS` / `DONE` / `VERIFIED`
- `createdBy`: User (FK) — 접수자
- `assignedTo`: User (FK, nullable) — 현장 작업자
- `resolvedAt`: Timestamp (nullable)
- `createdAt`, `updatedAt`: Timestamp

**TypeScript Interface:**
```typescript
type Priority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
type IssueStatus = "NEW" | "ASSIGNED" | "IN_PROGRESS" | "DONE" | "VERIFIED";

interface CategoryRef {
  id: number;
  name: string;
}

interface Issue {
  id: number;
  title: string;
  description: string;
  callerName: string;
  callerPhone: string;
  categoryL1: CategoryRef; // 대분류
  categoryL2: CategoryRef; // 중분류
  categoryL3: CategoryRef; // 소분류
  priority: Priority;      // 색상: URGENT=red, HIGH=orange, NORMAL=blue, LOW=gray
  status: IssueStatus;
  createdBy: User;
  assignedTo?: User;
  resolvedAt?: string;
  attachments: Attachment[];
  comments: Comment[];
  createdAt: string;
  updatedAt: string;
}
```

**기본 정렬 규칙(중요):** 모든 리스트 API의 기본 정렬은 `priority DESC (URGENT 먼저) → createdAt ASC`. 인덱스: `(priority, created_at)` 복합 인덱스 권장.

**Relationships:**
- Belongs to 3 Categories (L1, L2, L3)
- Has many Attachments (이미지)
- Has many Comments
- Has many IssueEvents (상태 변경 이력 / audit log)

## 5.3 Comment

**Purpose:** 이슈 카드 내 협업 메시지 + 현장 조치 내용 기록.

**Key Attributes:**
- `id`: Long
- `issueId`: Long (FK)
- `authorId`: Long (FK)
- `body`: Text
- `kind`: Enum — `NOTE` (일반 코멘트) / `FIELD_ACTION` (현장 조치) / `SYSTEM` (자동 생성)
- `createdAt`: Timestamp

**TypeScript Interface:**
```typescript
type CommentKind = "NOTE" | "FIELD_ACTION" | "SYSTEM";

interface Comment {
  id: number;
  issueId: number;
  author: User;
  body: string;
  kind: CommentKind;
  createdAt: string;
}
```

## 5.4 Attachment

**Purpose:** 이슈에 첨부된 이미지(주로 현장 작업자가 업로드).

**Key Attributes:**
- `id`: Long
- `issueId`: Long (FK)
- `uploaderId`: Long (FK)
- `filename`: String — 저장된 파일명
- `originalName`: String
- `mimeType`: String
- `sizeBytes`: Long
- `createdAt`: Timestamp

## 5.5 IssueEvent

**Purpose:** 이슈 상태 변경 및 주요 액션 감사 로그.

**Key Attributes:**
- `id`: Long
- `issueId`: Long (FK)
- `actorId`: Long (FK)
- `eventType`: Enum — `CREATED` / `STATUS_CHANGED` / `ASSIGNED` / `COMMENTED` / `ATTACHMENT_ADDED` / `RESOLVED`
- `fromValue`, `toValue`: String (nullable) — 상태 변경 시 사용
- `createdAt`: Timestamp

## 5.6 Category (Reference) — 3단계 계층 구조

**Purpose:** 카테고리 마스터. 단일 테이블에 자기참조(self-reference)로 3단계 계층을 표현. 키워드는 자동 카테고리 제안에 사용.

**Key Attributes:**
- `id`: Long — PK
- `parentId`: Long (FK, nullable) — 상위 카테고리. L1은 null
- `level`: Int — 1(대분류) / 2(중분류) / 3(소분류)
- `name`: String — 표시 이름
- `keywords`: String[] — 자동 분류용 키워드 (JSON 컬럼, optional)
- `sortOrder`: Int
- `active`: Boolean

**TypeScript Interface:**
```typescript
interface Category {
  id: number;
  parentId: number | null;
  level: 1 | 2 | 3;
  name: string;
  keywords?: string[];
  sortOrder: number;
  active: boolean;
}
```

**카테고리 조합 규칙(MVP 가정):**
- 이슈 등록 시 L1/L2/L3 **세 가지 모두 필수 선택**.
- L1과 L2, L2와 L3 사이에 **계층 종속성을 강제하지 않는다** — 즉, 모든 조합이 가능하다. (예: `voip/pbx × 입주민앱 × 로그인오류` 허용)
- 추후 v2에서 특정 조합만 허용하는 매트릭스 룰 도입 여지 확보.

**MVP 초기 시드 데이터:**

| Level | 항목 |
| :---- | :--- |
| L1 (대분류) | `아파트먼트v1`, `아파트먼트v2`, `voip/pbx` |
| L2 (중분류) | `관리자웹`, `입주민앱`, `단말`, `서버` |
| L3 (소분류) | `기기미동작`, `기기오동작`, `로그인오류` |

> 운영 중 Admin 화면에서 추가/수정/비활성화 가능.

> **참고: SLA 정책 모델은 v0.2에서 제거되었다.** 우선순위는 시각적 강조(색상)와 기본 정렬 키로만 사용되며, 자동 SLA 마감 시각 계산은 하지 않는다. 향후 운영하면서 SLA 기준이 필요해지면 v2에서 재도입한다.

## 5.7 Notification (인앱 알림)

**Purpose:** 사용자별 인앱 알림 보관. 외부 채널 발송 없이 시스템 내에서 확인.

**Key Attributes:**
- `id`: Long
- `recipientId`: Long (FK) — 수신자
- `kind`: Enum — `ISSUE_ASSIGNED` / `ISSUE_COMMENTED` / `ISSUE_STATUS_CHANGED` / `ISSUE_REOPENED`
- `issueId`: Long (FK) — 관련 이슈
- `actorId`: Long (FK, nullable) — 알림을 유발한 사용자
- `message`: String — 미리 렌더링된 짧은 메시지 (예: "홍길동님이 #1234 이슈를 배정했습니다")
- `readAt`: Timestamp (nullable) — 읽은 시각. null이면 미읽음
- `createdAt`: Timestamp

**TypeScript Interface:**
```typescript
type NotificationKind =
  | "ISSUE_ASSIGNED"
  | "ISSUE_COMMENTED"
  | "ISSUE_STATUS_CHANGED"
  | "ISSUE_REOPENED";

interface Notification {
  id: number;
  recipientId: number;
  kind: NotificationKind;
  issueId: number;
  actor?: User;
  message: string;
  readAt?: string;
  createdAt: string;
}
```

**동작 방식 (MVP):**
- 알림 생성: 이슈 배정/코멘트/상태 변경/재오픈 시 백엔드가 트랜잭션 내에서 자동 INSERT.
- 알림 수신: 프론트엔드가 **30초 주기 polling** (`GET /api/notifications/unread-count` + 필요시 `GET /api/notifications`).
- WebSocket/SSE 미사용 (MVP). v2에서 도입 검토.
- 미읽음 카운트는 헤더 벨 아이콘 뱃지에 표시.
- 알림 클릭 시 해당 이슈 상세 페이지로 이동 + `readAt` 자동 업데이트.

**[Epic 3 개정 — V7 마이그레이션(Story 3.4), Architect 2026-05-22]** 보고서 자동 생성 알림(Story 3.4 AC6/AC7)을 위해 본 테이블을 확장한다(분리 테이블 대신 기존 벨/폴링/리스트 인프라 재사용):
- `issue_id` → **nullable** 로 변경(보고서 알림은 관련 이슈 없음).
- `kind` CHECK 에 **`REPORT_READY`, `REPORT_FAILED`** 추가.
- `NotificationKind` TS 타입에 동일 2종 추가. `Notification.issueId` → `number | null`.
- 알림 클릭 동작 분기: `issueId != null → /issues/:id`, **`issueId == null → /reports`**(보고서 알림).
- 수신자 패턴 신규: 보고서 알림은 **전체 ADMIN** 에게(`recipient = each ADMIN`, 이슈 이해관계자 아님). `NotificationService.notifyAdmins(kind, message)` 추가.
- Epic 2 의 "V2 스키마 무변경(ddl-auto=validate)" 원칙은 Story 3.1 의 **V5(resolved_at 인덱스)** 에서 이미 종료됐고, 본 notifications 확장은 **V7(Story 3.4)** 다. **PO 비준됨 2026-05-22 (Sarah): 기존 테이블 확장 방식 채택**(분리 테이블 아님 — 2.8 인프라 재사용). **PO 정정 2026-05-22 (Sarah): 마이그레이션 버전 V5→V7 — V5/V6 는 각각 Story 3.1 resolved_at 인덱스 / Story 3.4 reports 테이블이 선점.**

## 5.8 Report (자동 생성 보고서) — [Epic 3 신설, V6 — Story 3.4]

**Purpose:** 일/주간 자동 생성 PDF 보고서의 메타데이터. 파일은 디스크(`/var/smcs/files` 또는 `smcs_reports` 볼륨), 메타는 DB.

**Key Attributes:**
- `id`: Long
- `kind`: Enum — `DAILY` / `WEEKLY`
- `periodKey`: String — 일간 `YYYY-MM-DD`(KST 일자), 주간 `YYYY-Www`(ISO 주차). idempotent 키(동일 기간 재생성 시 덮어쓰기 — Story 3.4 AC4).
- `filePath`: String — 저장 상대경로(`reports/{kind}/{periodKey}.pdf` 등, UUID 불요 — periodKey 가 유일)
- `sizeBytes`: Long
- `createdAt`: Timestamp

**TypeScript Interface:**
```typescript
type ReportKind = "DAILY" | "WEEKLY";

interface Report {
  id: number;
  kind: ReportKind;
  periodKey: string;   // "2026-05-21" | "2026-W21"
  url: string;         // 보안 서빙 경로 (ADMIN 전용, 직접 스트림 — 2.6 FileController 패턴)
  sizeBytes: number;
  createdAt: string;
}
```

**동작 방식 (MVP):**
- 생성: `@Scheduled`(08:00 KST 일간 / 월요일 주간) → `ReportService.generateDaily(date)`/`generateWeekly(week)`(시간 비의존 — 단위 테스트 대상). PDFBox 3.0.3 + 나눔고딕 임베드.
- idempotent: `(kind, periodKey)` UNIQUE — 재실행 시 파일 덮어쓰기 + 메타 upsert(Story 3.4 AC4).
- 서빙: `GET /api/reports/daily?date=`·`/weekly?week=`·보관함 리스트 = **ADMIN 전용**, 직접 스트림(2.6 패턴, X-Accel 배포 swap-in).
- 정리: 90일 경과 보고서 일일 cleanup 잡으로 파일+메타 삭제(Story 3.5 AC5).
- 집계: 대시보드(3.1)와 **동일한 `StatsService` 공유**(3.3 PDF 도 동일 데이터).

**V6 마이그레이션 신규 테이블**(Story 3.4 — 보관함 적재가 소유; Story 3.3 은 on-demand PDF 생성 엔진으로 테이블 불요. PO 정정 2026-05-22 Sarah) `reports`: `id BIGSERIAL PK, kind VARCHAR(10) CHECK(DAILY|WEEKLY), period_key VARCHAR(10) NOT NULL, file_path VARCHAR(200) NOT NULL, size_bytes BIGINT NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW(), UNIQUE(kind, period_key)`. 인덱스 `(kind, created_at DESC)`(보관함 최신순).

## 5.9 Stats (계산 모델 — 비영속) — [Epic 3, Architect 설계]

**Purpose:** 대시보드(Story 3.1/3.2)와 PDF 보고서(3.3)가 **공유하는 집계 결과**. 영속 엔티티가 아니라 `StatsService`가 이슈 데이터로 계산하는 read-only DTO. 단일 출처로 대시보드·PDF가 동일 수치를 보장(3.1 AC).

**계산 모델 (`DashboardStats`):**
```typescript
interface DashboardStats {
  kpi: {
    newCount: number;          // period 내 created_at
    resolvedCount: number;     // period 내 resolved_at
    openCount: number;         // 현재 status ∉ {DONE, VERIFIED} (period 무관)
    avgResolveMinutes: number; // period 내 해결분의 avg(resolved_at − created_at)
  };
  byCategory: { name: string; count: number }[];      // category_l1 그룹
  byAssignee: { name: string; resolved: number }[];   // assigned_to 그룹 (period 내 해결)
  byPriority: { priority: Priority; count: number }[];
  trend: { date: string; newCount: number; resolvedCount: number }[]; // KST 일자별
}
```

**설계 규약 (Architect 2026-05-22):**
- **API**: `GET /api/stats/dashboard?period=today|week|month` (인증; 대시보드는 ADMIN 라우트지만 §6 stats=인증 — 화면 가드로 ADMIN 한정). P95 < 500ms (3.1 AC4).
- **KST 경계**: period → `[day.atStartOfDay(KST) → exclusive end].toInstant()` (2.2 `IssueQueryService.startOfDay` 패턴 재사용). UTC 저장값을 KST 경계로 필터.
- **`resolved_at` = 통계의 핵심 축**: "처리 건수/평균 처리시간"은 `resolved_at` 기반. 2.4(→DONE set)/2.7(재오픈 clear)이 관리. **PO 확정 2026-05-22 (Sarah): 처리시간 = 접수→최종 완료**(§13 KPI "접수→완료" 부합). 재오픈→재완료 시 `resolved_at`은 최종 완료 시각으로 갱신(현 구현). 첫 완료 기준 아님 — 추가 추적 불요.
- **추세는 native SQL**: `(created_at AT TIME ZONE 'Asia/Seoul')::date` 그룹핑은 JPQL 표현 불가 → `JdbcTemplate`(LocalDataSeeder 선례). 단순 count/group은 JPQL/Specification.
- **단위 테스트(3.1 AC5)**: 집계 메서드 단위. 계산 로직과 쿼리 분리.
- **V5 인덱스(P95 근거)**: `issues(created_at)`, `issues(resolved_at)`, `issues(status)`, `issues(assigned_to)`, `issues(category_l1_id)` 가 P95 근거 인덱스다. **PO 확인 2026-05-22 (Sarah): V2 에 `created_at`·`status`·`(assigned_to,status)`·`category_l1_id` 가 이미 존재 → V5 에서 신규 추가되는 것은 `resolved_at` 하나뿐**(Story 3.1 Task 1, 실제 V2 마이그레이션 대조 검증). MVP 데이터량은 작으나 AC4가 명시 성능기준이라 인덱스 근거를 남긴다.

---
