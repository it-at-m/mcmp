package de.muenchen.mcmp.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkGroupRepository extends JpaRepository<NetworkGroup, Long> {
    Optional<NetworkGroup> findByName(String name);
    @Query("SELECT DISTINCT ng FROM NetworkGroup ng LEFT JOIN FETCH ng.appservices ORDER BY ng.name ASC")
    List<NetworkGroup> findAllWithAppservices();

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

}