package de.muenchen.mcmp.repository;

import lombok.Builder;

import java.util.List;

@Builder
public record RepositoryDetailDTO(
        Long id,
        String name,
        RepositoryLockStatus lockStatus,
        boolean isFavorite,
        List<RepositoryAppserviceRefDTO> appservices,
        boolean canEdit
) {}
