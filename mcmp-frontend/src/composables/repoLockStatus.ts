import type { RepositoryLockStatus } from "@/types/Repository";

import { mdiAccountLock, mdiLock, mdiLockOpenVariant } from "@mdi/js";

export const REPO_LOCK_STATUS_LABELS: Record<RepositoryLockStatus, string> = {
  LOCKED: "Gesperrt",
  SELF_ONLY: "Nur für Besitzer",
  OPEN: "Offen",
};

export const REPO_LOCK_STATUS_TOOLTIPS: Record<RepositoryLockStatus, string> = {
  LOCKED: "Repository ist gesperrt",
  SELF_ONLY:
    "Repository kann nur von den Besitzern an Server angebunden werden",
  OPEN: "Repository ist offen",
};

export const REPO_LOCK_STATUS_ICONS: Record<RepositoryLockStatus, string> = {
  LOCKED: mdiLock,
  SELF_ONLY: mdiAccountLock,
  OPEN: mdiLockOpenVariant,
};

/** Reason why a repository can't be attached by the current user, or "" if it can. */
export function repoAttachDisabledReason(repo: {
  name: string;
  lockStatus: RepositoryLockStatus;
  canEdit?: boolean;
}): string {
  if (repo.lockStatus === "LOCKED")
    return `Repository ${repo.name} ist gesperrt.`;
  if (repo.lockStatus === "SELF_ONLY" && repo.canEdit === false)
    return `Repository ${repo.name} kann nur von den Besitzern angebunden werden.`;
  return "";
}
