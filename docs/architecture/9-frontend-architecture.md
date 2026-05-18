# 9. Frontend Architecture

## 9.1 기술 결정

| 영역 | 선택 | 사유 |
|------|------|------|
| 빌드 | Vite | 빠른 dev, 단순한 설정 |
| 라우팅 | React Router 6 | 표준 |
| UI 키트 | Ant Design 5 | 폼/테이블/날짜 피커 풍부, 한국 친화 |
| 서버 상태 | TanStack Query 5 | 캐싱/리페치/스테일 관리 자동 |
| 클라이언트 상태 | Zustand | Redux보다 가벼움, MVP 충분 |
| 폼 | React Hook Form + Zod | 검증 일원화 |
| 차트 | Ant Design Charts (or recharts) | Ant 통합 또는 가벼운 대안 |
| HTTP | axios (인터셉터 사용) | JWT 자동 첨부, 401 자동 로그아웃 |
| 날짜 | dayjs | Ant Design 내부 사용. moment 대체 |
| 타입 | TypeScript 5 strict | 런타임 버그 사전 차단 |

## 9.2 핵심 패턴

### 9.2.1 API 클라이언트
```typescript
// api/client.ts
const apiClient = axios.create({ baseURL: "/api" });

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) useAuthStore.getState().logout();
    return Promise.reject(err);
  }
);
```

### 9.2.2 권한 가드
```typescript
// auth/RequireRole.tsx
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const user = useAuth();
  if (!user) return <Navigate to="/login" />;
  if (!roles.includes(user.role)) return <Forbidden />;
  return <>{children}</>;
}
```

### 9.2.3 알림 Polling 훅
```typescript
// features/notifications/useUnreadCount.ts
export function useUnreadCount() {
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: () => api.notifications.unreadCount(),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false, // 탭 비활성 시 중단
    staleTime: 25_000,
  });
}
```

### 9.2.4 우선순위 뱃지 (시각 강조 일관성)
```typescript
// shared/PriorityBadge.tsx
const COLORS: Record<Priority, string> = {
  URGENT: "#ff4d4f", HIGH: "#fa8c16", NORMAL: "#1677ff", LOW: "#8c8c8c",
};
const ICONS: Record<Priority, string> = {
  URGENT: "🔥", HIGH: "⚠️", NORMAL: "•", LOW: "○",
};
export const PriorityBadge = ({ p }: { p: Priority }) => (
  <Tag color={COLORS[p]}>
    <span aria-hidden>{ICONS[p]}</span> {LABELS[p]}
  </Tag>
);
```

## 9.3 모바일 우선 화면 (현장 작업자)

- 별도 경로 `/m/*` 로 분리하여 데스크톱 UI와 코드 분리
- 화면 너비 360px ~ 768px 최적화
- 모든 터치 영역 최소 44x44px
- iOS Safari 자동 캡션 키패드 방지: `inputMode="text"`, viewport meta `user-scalable=no`
- 카메라 접근: `<input type="file" accept="image/*" capture="environment">` — 폰 후방 카메라 직접 호출

## 9.4 클라이언트 이미지 리사이즈

```typescript
// shared/imageResize.ts
async function resize(file: File, maxDim = 1920, quality = 0.85): Promise<Blob> {
  const img = await loadImage(file);
  const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
  const canvas = new OffscreenCanvas(img.width * scale, img.height * scale);
  canvas.getContext("2d")!.drawImage(img, 0, 0, canvas.width, canvas.height);
  return canvas.convertToBlob({ type: "image/jpeg", quality });
}
```

> ⚠️ Canvas 리사이즈도 EXIF를 제거한다. 그래도 서버에서 한 번 더 EXIF 스트립을 수행해 사용자가 원본 업로드를 우회해도 안전하게 한다 (defense in depth).

## 9.5 Routes Table

| 경로 | 컴포넌트 | 필요 권한 | 모바일? | 비고 |
|------|---------|----------|---------|------|
| `/login` | `LoginView` | Public | - | 인증 미통과 시 모든 라우트가 여기로 |
| `/` | (역할별 리다이렉트) | 인증 | - | AGENT/ADMIN → `/issues`, FIELD → `/m` |
| `/issues` | `IssueListView` | AGENT, ADMIN | 데스크톱 | 기본 진입 화면 |
| `/issues/new` | `IssueFormView` | AGENT, ADMIN | 데스크톱 | 단축키 `N`으로 진입 |
| `/issues/:id` | `IssueDetailView` | 인증 (담당자 또는 AGENT/ADMIN) | 양쪽 | 권한 미달 시 403 |
| `/m` | `MobileFieldHomeView` | FIELD, ADMIN | **모바일 우선** | 카드 스택 |
| `/m/issues/:id` | `MobileFieldDetailView` | FIELD 본인 배정 + ADMIN | **모바일 우선** | 사진 첨부 + 조치 |
| `/dashboard` | `DashboardView` | ADMIN | 데스크톱 | 차트 |
| `/reports` | `ReportsListView` | ADMIN | 데스크톱 | 보고서 보관함 |
| `/reports/:kind/:date` | `ReportPreview` | ADMIN | 데스크톱 | PDF iframe + 다운로드 |
| `/notifications` | `NotificationsView` | 인증 | 양쪽 | "모두 보기" |
| `/admin/users` | `AdminUsersView` | ADMIN | 데스크톱 | 사용자 관리 |
| `/admin/categories` | `AdminCategoriesView` | ADMIN | 데스크톱 | L1/L2/L3 탭 |
| `/403` | `ForbiddenView` | - | - | 권한 부족 화면 |
| `*` | `NotFoundView` | - | - | 404 |

**라우트 보호 패턴:**
```typescript
// routes.tsx
<Route path="/issues" element={
  <RequireRole roles={["AGENT", "ADMIN"]}>
    <IssueListView />
  </RequireRole>
} />
<Route path="/m" element={
  <RequireRole roles={["FIELD", "ADMIN"]}>
    <MobileFieldHomeView />
  </RequireRole>
} />
```

**Lazy Loading:**
- Admin과 Reports 라우트는 `React.lazy(() => import(...))`로 분리하여 초기 번들 감소.
- 모바일 라우트(`/m/*`)도 별도 청크로 분리(데스크톱 사용자가 다운받지 않도록).

## 9.6 핵심 공유 컴포넌트 명세

> **위치:** `frontend/src/shared/components/`
> **원칙:** props는 명시적 타입, 합리적 기본값. 내부 상태 최소화. 접근성(ARIA) 기본 내장.

### 9.6.1 `<PriorityBadge>`

```typescript
interface PriorityBadgeProps {
  priority: Priority;             // "URGENT" | "HIGH" | "NORMAL" | "LOW"
  size?: "sm" | "md";             // 기본 "md"
  showIcon?: boolean;             // 기본 true (색상 의존도 ↓, a11y)
  label?: string;                 // 기본 한글 라벨 ("긴급"/"높음"/"보통"/"낮음")
}
```
- 색상: URGENT `#ff4d4f` / HIGH `#fa8c16` / NORMAL `#1677ff` / LOW `#8c8c8c`
- ARIA: `<span role="status" aria-label="우선순위: 긴급">`
- 사용 위치: 리스트 행, 상세 페이지, 모바일 카드

### 9.6.2 `<StatusBadge>`

```typescript
interface StatusBadgeProps {
  status: IssueStatus;
  showProgress?: boolean;         // 기본 false. true면 단계 진행 바
}
```
- 색상: NEW `#13c2c2` / ASSIGNED `#1677ff` / IN_PROGRESS `#fa8c16` / DONE `#52c41a` / VERIFIED `#722ed1`
- `showProgress=true` 모드: 이슈 상세 페이지 상단 진행 바 (5단계)
- 키보드 인터랙션 없음 (표시 전용)

### 9.6.3 `<CategoryPicker>` (3단계 통합)

```typescript
interface CategoryPickerProps {
  value: { l1?: number; l2?: number; l3?: number };
  onChange: (v: { l1?: number; l2?: number; l3?: number }) => void;
  required?: boolean;             // 기본 true
  disabled?: boolean;
  layout?: "horizontal" | "vertical";
}
```
- 내부적으로 3개 Ant Design `<Select>` 렌더. 자유 조합 허용.
- 자동 카테고리 제안은 부모 컴포넌트가 `value`를 갱신하는 방식 (이 컴포넌트는 stateless).
- 각 Select에 `aria-label` 명시 ("대분류", "중분류", "소분류")

### 9.6.4 `<UserSelect>`

```typescript
interface UserSelectProps {
  value?: number;
  onChange: (userId: number | undefined) => void;
  filter?: { roles?: Role[]; activeOnly?: boolean };
  placeholder?: string;
  allowClear?: boolean;
}
```
- Ant Design `<Select>` 기반 검색 가능 드롭다운
- 내부에서 `useUsers({ roles, activeOnly })` 훅으로 데이터 로딩 (TanStack Query 캐시)
- 담당자 배정 화면에서 `filter={{ roles: ["FIELD"], activeOnly: true }}` 형태로 사용

### 9.6.5 `<IssueCard>` (모바일 우선)

```typescript
interface IssueCardProps {
  issue: IssueSummary;            // 리스트용 경량 DTO
  onClick?: () => void;
  showAssignee?: boolean;         // 기본 true
  compact?: boolean;              // 기본 false. true는 모바일 작은 카드
}
```
- 모바일 카드 스택과 데스크톱 카드 뷰에서 공통 사용
- 좌측 우선순위 색상 막대 + 제목 + 카테고리 + 접수 시각
- 터치 영역 최소 44x44px (`role="button"`, `tabIndex=0`, Enter/Space 키 처리)

### 9.6.6 기타 공유 유틸

- `<EmptyState message icon />` — 데이터 0건일 때 일관된 빈 상태 (PRD §3.2 FR10 차트 요구)
- `<ErrorBoundary />` — feature 단위로 감싸 한 화면의 에러가 전체 앱을 죽이지 않도록
- `<ConfirmModal />` — 위험 액션(재오픈, 비활성화)에 사용

## 9.7 Frontend Performance Patterns

| 기법 | 적용 위치 | 비고 |
|------|----------|------|
| **Code Splitting (route-level)** | `routes.tsx`에서 `React.lazy(() => import(...))` | Admin/Reports/모바일은 별도 청크 |
| **Vendor Splitting** | `vite.config.ts`의 `manualChunks` | `react`, `antd`, `tanstack-query`를 별도 청크 |
| **Lazy Loading (이미지)** | `<img loading="lazy">` | 첨부 이미지 갤러리에서 |
| **TanStack Query 캐싱** | 모든 GET 요청 | `staleTime` 적정값(이슈 리스트 30s, 카테고리 5min, 사용자 5min) |
| **Polling 최적화** | 알림 카운트 polling | `refetchIntervalInBackground: false` — 비활성 탭 중단 |
| **React.memo** | `<IssueCard>`, `<PriorityBadge>` | 리스트 재렌더 방지 |
| **Virtualization** | `<IssueListTable>` (50건/페이지면 불필요) | v2 페이지당 1000건 필요 시 `react-window` 도입 |
| **Ant Design Icons Tree-shaking** | 개별 import (`import { BellOutlined } from '@ant-design/icons'`) | 전체 import 금지 |
| **이미지 클라이언트 리사이즈** | `attachment-upload` feature | 모바일 업로드 트래픽 ↓ |
| **HTTP/2 + gzip + brotli** | Nginx 설정 | 정적 자원 압축 |

**번들 크기 가드레일:**
- 초기 진입 청크: < 300 KB (gzip 후)
- 라우트별 lazy 청크: < 150 KB
- Vite build 시 `--report` 또는 `rollup-plugin-visualizer`로 시각화

## 9.8 Accessibility Implementation Guide

> **목표:** WCAG 2.1 Level A (PRD §3.4)
> **원칙:** Ant Design 기본 ARIA를 신뢰하되, 커스텀 컴포넌트는 명시적 ARIA 작성.

### 9.8.1 시맨틱 HTML

- 페이지 헤더: `<header>`, 본문: `<main>`, 사이드: `<aside>`, 푸터: `<footer>`
- 클릭 가능한 요소는 `<button>` 또는 `<a>`. 절대 `<div onClick>` 사용 금지.
- 폼은 반드시 `<form>` + `<label htmlFor>` + 명시적 `id`

### 9.8.2 키보드 네비게이션

| 요구사항 | 구현 |
|---------|------|
| 모든 인터랙티브 요소에 키보드 도달 가능 | `tabIndex=0`. 비활성은 `tabIndex=-1` |
| Tab 순서가 시각적 순서와 일치 | DOM 순서 = 시각 순서. CSS `order` 사용 금지 |
| 포커스 표시 | Ant Design 기본 outline 유지. `outline: none` 금지 |
| 모달 진입 시 자동 포커스 | Ant Design `<Modal>` 기본 동작. 닫힐 때 호출자로 복귀 |
| Esc로 모달 닫기 | Ant Design 기본 |
| 단축키 가이드 | `?` 또는 `Ctrl+/` 누르면 단축키 도움말 모달 |

### 9.8.3 ARIA 가이드

- **명명(Naming):** 아이콘만 있는 버튼은 `aria-label` 필수 (예: 벨 아이콘 `aria-label="알림"`)
- **상태(State):** 토글/탭 등은 `aria-selected`, `aria-expanded` 명시
- **라이브 영역:** Toast/Notification은 `role="status"` 또는 `aria-live="polite"`
- **숨김:** 시각적으로만 보이고 스크린리더 무시할 요소는 `aria-hidden="true"`. 반대는 `.sr-only` 클래스 (visually-hidden)
- **랜드마크:** `<header>`, `<nav>`, `<main>`, `<aside>`, `<footer>` 사용 (또는 `role` 명시)

### 9.8.4 색상 대비 & 시각

- 텍스트 대비비 최소 **4.5:1** (Level AA 목표는 v2, MVP는 A인 3:1을 초과하되 가능하면 4.5)
- **색상에 의존하지 않는 정보 전달:** 우선순위/상태는 색상 + 텍스트 라벨 + 아이콘 3중 표시 (PRD FR16)
- 다크모드는 v2. MVP는 라이트만.

### 9.8.5 자동 테스트

- **개발 모드:** `@axe-core/react`로 콘솔 경고. `main.tsx`:
  ```typescript
  if (process.env.NODE_ENV === "development") {
    import("@axe-core/react").then(({ default: axe }) =>
      axe(React, ReactDOM, 1000)
    );
  }
  ```
- **수동 검증:** 키보드만으로 골든 패스(로그인 → 이슈 등록 → 조치 → 완료) 통과 확인 (Story 4.7 AC 추가)
- **스크린리더 검증:** macOS VoiceOver 또는 Windows Narrator 1회 통과 권장 (MVP 필수 아님)

### 9.8.6 모바일 특화

- 터치 영역 최소 **44x44px** (WCAG 2.5.5)
- `<input type="tel">`, `inputMode="numeric"` 등 키보드 힌트 명시
- 뷰포트: `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">` (user-scalable 강제 X — a11y 원칙)

---
