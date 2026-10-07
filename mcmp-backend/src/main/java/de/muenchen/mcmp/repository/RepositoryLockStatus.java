package de.muenchen.mcmp.repository;

/**
 * Lock state of a repository, delivered by the repo EAI (from {@code .repostatus/locked.json} of the repo server).
 */
public enum RepositoryLockStatus {
    /** The repository may not be changed or attached at all. */
    LOCKED,
    /** The repository may only be attached by its owners (no PAKETSHOP_REPO_ATTACH_NOT_OWNED_REPO). */
    SELF_ONLY,
    /** No restrictions. */
    OPEN
}
