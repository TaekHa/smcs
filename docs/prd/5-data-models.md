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

---
