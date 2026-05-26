import { describe, it, expect } from 'vitest';
import { suggestCategory } from './autoCategorize';
import type { CategoryOption } from '../../types/issue';

function cat(id: number, name: string, keywords: string[] = []): CategoryOption {
  return { id, name, level: 1, keywords };
}

describe('suggestCategory', () => {
  it('returns null when no candidate keyword matches the text', () => {
    const result = suggestCategory('아무 의미 없는 내용', [
      cat(1, 'A', ['xyz']),
      cat(2, 'B', ['qqq']),
    ]);
    expect(result).toBeNull();
  });

  it('returns the id of the single matching candidate', () => {
    const result = suggestCategory('관리자 화면 이슈', [
      cat(1, 'A', ['로그인']),
      cat(2, 'B', ['관리자']),
    ]);
    expect(result).toBe(2);
  });

  it('prefers candidate with more keyword matches (AC3-a)', () => {
    const result = suggestCategory('VOIP 전화 문제', [
      cat(1, 'A', ['VOIP']), // 1 match
      cat(2, 'B', ['VOIP', '전화']), // 2 matches
    ]);
    expect(result).toBe(2);
  });

  it('on equal match count, prefers candidate with the longer matching keyword (AC3-b)', () => {
    const result = suggestCategory('비밀번호 변경 요청', [
      cat(1, 'A', ['비밀']),
      cat(2, 'B', ['비밀번호']),
    ]);
    expect(result).toBe(2);
  });

  it('is case-insensitive and NFC-normalized for Korean', () => {
    const result = suggestCategory('Wifi 끊김 보고', [
      cat(1, 'A', ['WIFI', '인터넷']),
    ]);
    expect(result).toBe(1);
  });

  it('returns null on empty text', () => {
    expect(suggestCategory('', [cat(1, 'A', ['foo'])])).toBeNull();
  });

  it('returns null on empty candidates list', () => {
    expect(suggestCategory('any text', [])).toBeNull();
  });

  it('ignores candidates whose keywords array is empty or missing', () => {
    const result = suggestCategory('foo bar', [
      cat(1, 'A', []),
      { id: 2, name: 'B', level: 1 }, // no keywords field at all
      cat(3, 'C', ['foo']),
    ]);
    expect(result).toBe(3);
  });

  it('on full tie, returns the first candidate (sortOrder natural — AC3 step 3)', () => {
    // Backend pre-sorts by sortOrder asc; this is the implicit tiebreaker.
    const result = suggestCategory('foo', [
      cat(1, 'first', ['foo']),
      cat(2, 'second', ['foo']),
    ]);
    expect(result).toBe(1);
  });

  it('ignores blank/null-ish keywords inside an otherwise-valid array', () => {
    const result = suggestCategory('test', [
      // @ts-expect-error — defensive: empty string + undefined slipping through bad ADMIN input
      cat(1, 'A', ['', undefined, 'test']),
    ]);
    expect(result).toBe(1);
  });
});
