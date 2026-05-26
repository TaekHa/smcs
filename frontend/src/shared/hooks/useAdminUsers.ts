import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createAdminUser, listAdminUsers, updateAdminUser } from '../../api/adminUsers';
import type {
  UserAdminItem,
  UserCreateRequest,
  UserCreateResponse,
  UserUpdateRequest,
} from '../../types/admin-user';

export function useAdminUsers() {
  return useQuery<UserAdminItem[]>({
    queryKey: ['admin-users'],
    queryFn: listAdminUsers,
    staleTime: 60_000,
  });
}

export function useCreateAdminUser() {
  const qc = useQueryClient();
  return useMutation<UserCreateResponse, unknown, UserCreateRequest>({
    mutationFn: createAdminUser,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });
}

export function useUpdateAdminUser() {
  const qc = useQueryClient();
  return useMutation<UserAdminItem, unknown, { id: number; req: UserUpdateRequest }>({
    mutationFn: ({ id, req }) => updateAdminUser(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });
}
