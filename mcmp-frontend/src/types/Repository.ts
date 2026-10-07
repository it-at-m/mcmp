/**
 * LOCKED: repository can't be changed or attached.
 * SELF_ONLY: repository can only be attached by its owners.
 * OPEN: no restrictions.
 */
export type RepositoryLockStatus = "LOCKED" | "SELF_ONLY" | "OPEN";

export default interface Repository {
  id: number;
  name: string;
  lockStatus: RepositoryLockStatus;
  isFavorite: boolean;
  canEdit?: boolean;
}
