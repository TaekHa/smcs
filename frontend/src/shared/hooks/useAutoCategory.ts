import { useEffect } from 'react';
import { suggestCategory } from '../lib/autoCategorize';
import { useCategories } from './useCategories';

type Level = 1 | 2 | 3;
type Touched = Record<Level, boolean>;

interface UseAutoCategoryArgs {
  /** Combined form text — typically `${title} ${description}`. Empty disables matching. */
  text: string;
  /** Per-level "user has touched this field" flag — never auto-overwrite a touched level. */
  touched: Touched;
  /** Called when an auto suggestion is computed for a level (AC6: null result is skipped). */
  apply: (level: Level, id: number) => void;
  /** Debounce window in ms. Default 300 (consistent with IssueListView search). */
  debounceMs?: number;
}

/**
 * Story 4.2 — debounced auto category suggestion. Runs three independent matches
 * (L1/L2/L3, AC2) and calls {@link apply} only for levels that
 *   1) the user has NOT touched (AC4), and
 *   2) produced a non-null match (AC6 — null result is a no-op, never a clear).
 */
export function useAutoCategory({ text, touched, apply, debounceMs = 300 }: UseAutoCategoryArgs) {
  const l1 = useCategories(1);
  const l2 = useCategories(2);
  const l3 = useCategories(3);

  useEffect(() => {
    if (!text.trim()) {
      return;
    }
    const handle = setTimeout(() => {
      ([
        { level: 1 as const, data: l1.data },
        { level: 2 as const, data: l2.data },
        { level: 3 as const, data: l3.data },
      ] satisfies { level: Level; data: typeof l1.data }[]).forEach(({ level, data }) => {
        if (touched[level] || !data) return;
        const suggested = suggestCategory(text, data);
        if (suggested != null) {
          apply(level, suggested);
        }
      });
    }, debounceMs);

    return () => clearTimeout(handle);
    // `apply` is wrapped in useCallback by callers, but we intentionally re-run only on the
    // inputs that should trigger a match (text + touched + fetched candidate lists).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, touched[1], touched[2], touched[3], l1.data, l2.data, l3.data, debounceMs]);
}
