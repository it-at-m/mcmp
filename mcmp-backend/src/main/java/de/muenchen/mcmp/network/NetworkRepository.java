package de.muenchen.mcmp.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkRepository extends JpaRepository<Network, Long> {
    Optional<Network> findByCidr(String cidr);
    
    /**
     * Retrieves all available network groups for a specific application service based on authorization rules.
     *
     * This method implements complex authorization logic that determines which network groups are accessible
     * for a given application service. The authorization is based on three different scenarios:
     *
     * 1. Restricted groups: Network groups that are explicitly assigned to the app service and have restrict=true
     * 2. Application groups: General application network groups available to all services in the same environment
     * 3. Database groups: Database-specific network groups available when requesting database access
     *
     * The query uses a Common Table Expression (CTE) to first determine the environment of the specified
     * application service, then applies the authorization rules through multiple OR conditions.
     *
     * Authorization Rules:
     * - If database=false: Returns restricted groups assigned to the app service + general application groups
     * - If database=true: Returns ONLY database-specific groups (no restricted groups included)
     * - All results are filtered by matching environment types
     *
     * Example usage:
     * <pre>
     * // Get available network groups for application service ID 123 (non-database request)
     * // Returns: restricted groups + application groups
     * List&lt;NetworkGroup&gt; appGroups = networkRepository.findAvailableNetworkGroupsForAppservice(123L, false);
     *
     * // Get available network groups for application service ID 123 (database request)
     * // Returns: ONLY database groups
     * List&lt;NetworkGroup&gt; dbGroups = networkRepository.findAvailableNetworkGroupsForAppservice(123L, true);
     * </pre>
     *
     * @param appserviceId The ID of the application service for which to retrieve available network groups.
     *                     Must be a valid existing application service ID.
     * @param database Flag indicating the type of request:
     *                 - false: Request for application network groups (includes restricted + application groups)
     *                 - true: Request for database network groups (includes ONLY database groups)
     * @return List of NetworkGroup entities that are authorized for the specified application service.
     *         Returns empty list if no authorized network groups are found.
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    @Query(value = """
            WITH env AS (
                SELECT environment FROM appservice WHERE id = :appserviceId
            )
            SELECT ng.*
            FROM cmp.network_group ng
                    LEFT JOIN appservice_network_group_assignment anga ON anga.network_group_id = ng.id
                    LEFT JOIN appservice a ON anga.appservice_id = a.id
                    CROSS JOIN env
            WHERE (
                anga.appservice_id = :appserviceId
                    AND ng.environment = a.environment
                    AND ng.restrict = TRUE AND :database = FALSE
                )
               OR (
                :database = FALSE
                    AND ng.application = TRUE
                    AND ng.environment = env.environment
                )
               OR (
                :database = TRUE
                    AND ng.database = TRUE
                    AND ng.environment = env.environment
                )
            """, nativeQuery = true)
    List<NetworkGroup> findAvailableNetworkGroupsForAppservice(
            @Param("appserviceId") Long appserviceId,
            @Param("database") Boolean database);

    /**
     * Verifies if a specific network group is authorized for use by a given application service.
     *
     * This method performs authorization validation to ensure that an application service has
     * permission to use a specific network group. The validation follows the same business rules
     * as the findAvailableNetworkGroupsForAppservice method but returns a simple boolean result
     * for a single network group.
     *
     * The authorization check considers:
     * - Environment matching between the app service and network group
     * - Network group type (application, database, or restricted)
     * - Explicit assignments through appservice_network_group_assignment table
     * - The database flag to determine which type of access is being requested
     *
     * Authorization Logic:
     * - Restricted groups: Must be explicitly assigned to the app service via assignment table (only for database=false)
     * - Application groups: Available to all services in the same environment when database=false
     * - Database groups: Available to all services in the same environment when database=true
     *
     * Note: When database=true, restricted groups are NOT included in the authorization check.
     * Only database-specific groups are considered valid.
     *
     * Example usage:
     * <pre>
     * // Check if app service 123 can use network group 456 for application purposes
     * // This checks: restricted groups + application groups
     * Boolean isAuthorized = networkRepository.isNetworkGroupAuthorizedForAppservice(123L, 456L, false);
     * if (isAuthorized) {
     *     // Proceed with network group assignment
     * } else {
     *     // Deny access or show error message
     * }
     *
     * // Check if app service 123 can use network group 789 for database purposes
     * // This checks: ONLY database groups (restricted groups are ignored)
     * Boolean canUseDbGroup = networkRepository.isNetworkGroupAuthorizedForAppservice(123L, 789L, true);
     * </pre>
     *
     * @param appserviceId The ID of the application service requesting access.
     *                     Must be a valid existing application service ID.
     * @param networkGroupId The ID of the network group to validate access for.
     *                       Must be a valid existing network group ID.
     * @param database Flag indicating the type of access being requested:
     *                 - false: Application-level network access (checks restricted + application groups)
     *                 - true: Database-level network access (checks ONLY database groups)
     * @return Boolean.TRUE if the network group is authorized for the application service,
     *         Boolean.FALSE if access is denied or no authorization exists.
     *         Never returns null - always returns a definitive boolean result.
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    @Query(value = """
            WITH env AS (
                SELECT environment FROM cmp.appservice WHERE id = :appserviceId
            )
            SELECT COUNT(1) > 0 AS berechtigt
            FROM network_group ng
                    LEFT JOIN appservice_network_group_assignment anga ON anga.network_group_id = ng.id
                    LEFT JOIN appservice a ON anga.appservice_id = a.id
                    CROSS JOIN env
            WHERE ng.id = :networkGroupId
              AND (
                (anga.appservice_id = :appserviceId
                    AND ng.environment = a.environment
                    AND ng.restrict = TRUE
                    AND :database = FALSE)
                    OR
                (:database = FALSE
                    AND ng.application = TRUE
                    AND ng.environment = env.environment)
                    OR
                (:database = TRUE
                    AND ng.database = TRUE
                    AND ng.environment = env.environment)
                )
            """, nativeQuery = true)
    Boolean isNetworkGroupAuthorizedForAppservice(
            @Param("appserviceId") Long appserviceId,
            @Param("networkGroupId") Long networkGroupId,
            @Param("database") Boolean database);
}
