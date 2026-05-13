package de.muenchen.mcmp.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Data

public class UserRoles {
    private final String username;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasUserRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasAdminRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasReadonlyRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasWindowsRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasLinuxRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasOracleRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasNonOracleRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasSecurityRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasOperatorRole;
    @Accessors(fluent = true)
    @Getter
    private final boolean hasNetworkRole;
}
