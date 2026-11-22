package de.muenchen.mcmp.user;

import java.time.Instant;

public record UserHistoryDTO(
        Long id,
        String username,
        String name,
        String email,
        Instant createdAt,
        Instant updatedAt
) {}