import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listAdminCategories, upsertCategory } from '../../api/adminCategories';
import type { CategoryAdminItem, CategoryLevel, CategoryUpsertRequest } from '../../types/category';

export function useAdminCategories(level: CategoryLevel) {
  return useQuery<CategoryAdminItem[]>({
    queryKey: ['admin-categories', level],
    queryFn: () => listAdminCategories(level),
    staleTime: 60_000,
  });
}

/**
 * Invalidates both ['admin-categories', level] (admin table) AND ['categories', level]
 * (public form dropdown) on success so AC4 — "비활성화된 카테고리는 신규 이슈 등록 폼 드롭다운에서
 * 숨겨진다" — takes effect without waiting for the public lookup's 5-minute staleTime.
 */
export function useUpsertCategory() {
  const qc = useQueryClient();
  return useMutation<CategoryAdminItem, unknown, CategoryUpsertRequest>({
    mutationFn: upsertCategory,
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['admin-categories', variables.level] });
      qc.invalidateQueries({ queryKey: ['categories', variables.level] });
    },
  });
}
