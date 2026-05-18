import { Select, Space } from 'antd';
import { useCategories } from '../hooks/useCategories';

export interface CategoryValue {
  l1?: number;
  l2?: number;
  l3?: number;
}

interface CategoryPickerProps {
  value: CategoryValue;
  onChange: (v: CategoryValue) => void;
  required?: boolean;
  disabled?: boolean;
  layout?: 'horizontal' | 'vertical';
}

interface LevelSelectProps {
  level: 1 | 2 | 3;
  ariaLabel: string;
  placeholder: string;
  selected?: number;
  disabled?: boolean;
  required?: boolean;
  onSelect: (id: number | undefined) => void;
}

function LevelSelect({
  level,
  ariaLabel,
  placeholder,
  selected,
  disabled,
  required,
  onSelect,
}: LevelSelectProps) {
  const { data, isLoading } = useCategories(level);
  return (
    <Select
      aria-label={ariaLabel}
      aria-required={required}
      placeholder={placeholder}
      loading={isLoading}
      disabled={disabled}
      value={selected}
      onChange={(v) => onSelect(v)}
      style={{ minWidth: 160 }}
      options={(data ?? []).map((c) => ({ label: c.name, value: c.id }))}
    />
  );
}

/**
 * Three independent level dropdowns. L1/L2/L3 carry NO parent dependency —
 * any combination is allowed (AC7); changing one does not reset the others.
 */
export function CategoryPicker({
  value,
  onChange,
  required = true,
  disabled,
  layout = 'horizontal',
}: CategoryPickerProps) {
  return (
    <Space direction={layout === 'vertical' ? 'vertical' : 'horizontal'} wrap>
      <LevelSelect
        level={1}
        ariaLabel="대분류"
        placeholder="대분류"
        selected={value.l1}
        disabled={disabled}
        required={required}
        onSelect={(id) => onChange({ ...value, l1: id })}
      />
      <LevelSelect
        level={2}
        ariaLabel="중분류"
        placeholder="중분류"
        selected={value.l2}
        disabled={disabled}
        required={required}
        onSelect={(id) => onChange({ ...value, l2: id })}
      />
      <LevelSelect
        level={3}
        ariaLabel="소분류"
        placeholder="소분류"
        selected={value.l3}
        disabled={disabled}
        required={required}
        onSelect={(id) => onChange({ ...value, l3: id })}
      />
    </Space>
  );
}
