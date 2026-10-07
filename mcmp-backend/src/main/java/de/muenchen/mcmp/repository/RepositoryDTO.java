package de.muenchen.mcmp.repository;

import lombok.Builder;

@Builder
public record RepositoryDTO(
    Long id,
    String name,
    RepositoryLockStatus lockStatus,
    boolean isFavorite,
    boolean canEdit
) {}
