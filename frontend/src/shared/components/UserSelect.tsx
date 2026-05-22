import { Select } from 'antd';
import { useUsers } from '../hooks/useUsers';
import type { Role } from '../../types/auth';

interface UserSelectProps {
  value?: number;
  onChange: (userId: number | undefined) => void;
  filter?: { roles?: Role[] };
  placeholder?: string;
  allowClear?: boolean;
}

/** Searchable user dropdown (§9.6.4). Loads via useUsers; `filter.roles` narrows (e.g. FIELD-only). */
export function UserSelect({
  value,
  onChange,
  filter,
  placeholder = '담당자 선택',
  allowClear = true,
}: UserSelectProps) {
  const { data, isLoading } = useUsers(filter);
  return (
    <Select
      value={value}
      onChange={onChange}
      loading={isLoading}
      allowClear={allowClear}
      showSearch
      placeholder={placeholder}
      aria-label={placeholder}
      optionFilterProp="label"
      style={{ minWidth: 200 }}
      options={(data ?? []).map((u) => ({ label: u.displayName, value: u.id }))}
    />
  );
}
