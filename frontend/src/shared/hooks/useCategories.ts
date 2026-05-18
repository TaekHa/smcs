import { useQuery } from '@tanstack/react-query';
import { listCategories } from '../../api/categories';
import type { CategoryOption } from '../../types/issue';

/** Category options for one level. Cached 5min (categories change rarely). */
export function useCategories(level: 1 | 2 | 3) {
  return useQuery<CategoryOption[]>({
    queryKey: ['categories', level],
    queryFn: () => listCategories(level),
    staleTime: 5 * 60_000,
  });
}
