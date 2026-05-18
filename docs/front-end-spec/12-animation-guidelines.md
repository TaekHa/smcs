# 12. Animation Guidelines

| 변화 | Duration | Easing |
|------|----------|--------|
| 모달 열기/닫기 | 200ms | `ease-in-out` |
| 드롭다운 열기 | 150ms | `ease-out` |
| 카드 호버 | 150ms | `ease-out` |
| 페이지 전환 | 250ms 페이드 | `ease-in-out` |
| 상태 진행 바 채우기 | 300ms | `ease-in-out` |
| 알림 펄스 | 600ms 1회 | `ease-in-out` |
| Skeleton shimmer | 1500ms 반복 | `linear` |

**원칙:**
- 사용자가 의도한 동작에만 애니메이션 (자발성)
- 300ms를 넘는 애니메이션은 가능한 피함
- `prefers-reduced-motion: reduce` 사용자에게는 모든 애니메이션 즉시 완료 (CSS 0ms 오버라이드)

---
