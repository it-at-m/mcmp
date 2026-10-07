import type { RepositoryLockStatus } from "@/types/Repository";

export interface RepositoryAppserviceRef {
  id: number;
  name: string;
}

export interface RepositoryDetail {
  id: number;
  name: string;
  lockStatus: RepositoryLockStatus;
  isFavorite: boolean;
  appservices: RepositoryAppserviceRef[];
  canEdit: boolean;
}
