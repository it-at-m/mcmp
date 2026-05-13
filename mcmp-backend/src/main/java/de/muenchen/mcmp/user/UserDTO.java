package de.muenchen.mcmp.user;

import lombok.Builder;

@Builder
public record UserDTO (
        long id,
        String username,
        String department,
        boolean admin,
        String name,
        String email,
        boolean darkMode
) {}