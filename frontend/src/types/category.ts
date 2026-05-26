export type CategoryLevel = 1 | 2 | 3;

export interface CategoryAdminItem {
  id: number;
  level: CategoryLevel;
  name: string;
  sortOrder: number;
  active: boolean;
  keywords: string[];
}

export interface CategoryUpsertRequest {
  id?: number | null;
  level: CategoryLevel;
  name: string;
  keywords?: string[];
  sortOrder?: number | null;
  active?: boolean | null;
}
