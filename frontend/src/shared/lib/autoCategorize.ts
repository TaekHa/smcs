import type { CategoryOption } from '../../types/issue';

/**
 * Story 4.2 — rule-based category suggestion. Pure function, no I/O.
 *
 * Given the form's combined text (title + description) and the candidate categories for a
 * single level, returns the best-match category id, or null when no keyword matches.
 *
 * Ranking (AC3):
 *   1. matchCount DESC   — the candidate with the most matching keywords wins.
 *   2. maxKeywordLength DESC — tiebreaker: longer keyword is more specific.
 *   3. sortOrder ASC (natural — candidates already come pre-sorted from the backend).
 *
 * AC5 — no ML/AI. Plain substring match after NFC normalization + case fold.
 * AC6 — zero matches → null (caller MUST treat null as no-op, not as clear).
 */
export function suggestCategory(text: string, candidates: CategoryOption[]): number | null {
  if (!text || candidates.length === 0) {
    return null;
  }
  const normalizedText = text.toLowerCase().normalize('NFC');

  let best: { id: number; matchCount: number; maxLen: number } | null = null;
  for (const c of candidates) {
    const keywords = c.keywords;
    if (!keywords || keywords.length === 0) {
      continue;
    }
    let matchCount = 0;
    let maxLen = 0;
    for (const kw of keywords) {
      if (!kw) continue;
      const needle = kw.toLowerCase().normalize('NFC');
      if (normalizedText.includes(needle)) {
        matchCount += 1;
        if (needle.length > maxLen) {
          maxLen = needle.length;
        }
      }
    }
    if (matchCount === 0) {
      continue;
    }
    if (
      best === null ||
      matchCount > best.matchCount ||
      (matchCount === best.matchCount && maxLen > best.maxLen)
    ) {
      best = { id: c.id, matchCount, maxLen };
    }
    // tie on matchCount AND maxLen → keep current (first by sortOrder wins — AC3 step 3).
  }
  return best?.id ?? null;
}
