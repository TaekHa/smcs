# 13. AI UI Generation Prompts (Bonus)

핵심 6개 화면을 Claude Artifact / v0.dev / Lovable로 빠르게 프로토타이핑할 때 사용할 프롬프트.

## 13.1 이슈 리스트 프롬프트

> **Generate a React + TypeScript + Ant Design 5 page for an internal CS issue management system.**
>
> Layout: Top header with logo "SMCS", main nav links (Issues active, Dashboard, Reports, Admin dropdown), bell icon with badge "3", user dropdown showing "박지영". Main content area shows a page title "이슈 리스트" with a primary "+ 신규 등록 (N)" button right-aligned.
>
> Below the title, a filter bar with: search input (placeholder "제목/내용/전화번호..."), status multi-select, category cascader (3 levels), date range picker.
>
> Main: Ant Design Table with columns: ID, 제목, 카테고리 (showing "L1 > L2"), 우선순위 (Tag with color: URGENT red, HIGH orange, NORMAL blue, LOW gray + emoji), 상태 (Tag), 담당자, 접수일. Each row has a colored left border (4px) matching its priority color. Default sort: priority desc, createdAt asc.
>
> Sample data: 5 rows with mixed priorities and statuses. Korean text. Use #1677ff primary color (Ant Design default).
>
> Make it pixel-precise, professional, internal-tool aesthetic.

## 13.2 이슈 등록 폼 프롬프트

> **Generate a React + TypeScript + Ant Design 5 form page** for creating a new CS issue (Korean UI).
>
> Header: back button, title "신규 이슈 등록", right side has [취소] (secondary) and [저장 Ctrl+S] (primary) buttons.
>
> Form sections (vertical, single column except where noted):
> 1. Title (text input, autofocus, required) — placeholder "예: 1층 자판기 동전 반환 안 됨"
> 2. Two columns: 발신자명 (text) | 발신자 전화번호 (text with auto-format 010-0000-0000)
>    - Show inline info text below phone field: "💡 이전 이슈 1건 (지난 7일)"
> 3. Category: a single clickable field showing "아파트먼트v2 > 단말 > 기기미동작 🔽 변경" — clicking opens a modal
> 4. Priority radio group: 4 options with color-coded labels (🔥 긴급 red, ⚠ 높음 orange, • 보통 blue, ○ 낮음 gray)
> 5. Description (textarea, 6 rows, 5000 char counter)
> 6. Assignee select (optional, search-enabled, FIELD role users only)
>
> Footer: collapsible help text "ⓘ 단축키: Ctrl+S 저장 · Esc 취소 · Alt+1~4 우선순위 변경"
>
> Use Korean labels, Pretendard font preference, #1677ff primary.

## 13.3 모바일 현장 작업자 홈 프롬프트

> **Generate a mobile-first React page** (360px width) for field workers using Ant Design 5.
>
> Top bar: "SMCS" logo left, "김민호" user, bell icon with badge "2" right. Background white with subtle shadow.
>
> Page title: "📋 내 작업 (5)"
>
> Card stack (5 cards, vertical):
> - URGENT cards: solid red left border (8px), background tinted slight red, very visible
> - HIGH cards: orange left border
> - NORMAL: blue left border
> - LOW: gray left border
>
> Each card shows: priority badge with emoji + label / issue title (2 lines max) / category breadcrumb "L1 > L2 > L3" / time received ("⏱ 09:13 · 30분 전")
>
> Bottom: ghost button "완료된 작업 보기 ▾"
>
> Make sure touch targets are ≥ 44x44px. Korean text.

## 13.4 (나머지 화면 프롬프트도 동일한 패턴으로 작성 — Dev 단계에서 필요 시 `*generate-ui-prompt` 명령으로 확장)

---
