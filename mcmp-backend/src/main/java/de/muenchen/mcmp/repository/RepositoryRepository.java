package de.muenchen.mcmp.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    String CAN_EDIT_EXPRESSION = """
        (
            (SELECT COUNT(*) FROM cmp.repository_has_appservices rce WHERE rce.repository_id = r.id) = 1
            AND (
                :isAdmin
                OR EXISTS (
                    SELECT 1
                    FROM cmp.repository_has_appservices rce2
                             JOIN cmp.appservice ace ON rce2.appservice_id = ace.id
                             JOIN cmp.group_membership gmce ON ace.change_group_id = gmce.group_id
                             JOIN cmp."user" uce ON gmce.user_id = uce.id
                    WHERE rce2.repository_id = r.id
                      AND uce.username = :username
                )
            )
        )
        """;

    Optional<Repository> findByName(String name);

    List<Repository> findAllByServersId(Long serverId);

    @Query("SELECT r FROM Repository r JOIN r.servers s WHERE s.id = :serverId ORDER BY LOWER(r.name) ASC")
    List<Repository> findAllByServersIdOrderByNameAscIgnoreCase(@Param("serverId") Long serverId);

    @Query(value = "SELECT name, id FROM cmp.repository", nativeQuery = true)
    List<RepositoryIdByName> findAllIdsByName();

    @Query(value = "SELECT id FROM cmp.repository WHERE name = :name", nativeQuery = true)
    Optional<Long> findIdByName(@Param("name") String name);


    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.repository (name, lock_status)
            VALUES (:name, 'LOCKED')
            ON CONFLICT (name) DO NOTHING
            """, nativeQuery = true)
    void insertIfNotExists(@Param("name") String name);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.repository (name, repository_url, lock_status, created_at, updated_at)
            VALUES (:name, :url, :lockStatus, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (name) DO UPDATE
            SET repository_url = EXCLUDED.repository_url,
                lock_status = EXCLUDED.lock_status,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertRepository(@Param("name") String name, @Param("url") String url, @Param("lockStatus") String lockStatus);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE cmp.repository
            SET lock_status = 'LOCKED',
                repository_url = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE name = :name
            """, nativeQuery = true)
    void lockRepository(@Param("name") String name);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cmp.repository_assignment WHERE server_id = :serverId", nativeQuery = true)
    void deleteAssignmentsByServerId(@Param("serverId") Long serverId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM cmp.repository_assignment WHERE server_id = :serverId)", nativeQuery = true)
    boolean existsAssignmentsByServerId(@Param("serverId") Long serverId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cmp.repository_assignment WHERE repository_id = :repositoryId AND server_id = :serverId", nativeQuery = true)
    void deleteAssignment(@Param("repositoryId") Long repositoryId, @Param("serverId") Long serverId);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO cmp.repository_assignment (repository_id, server_id) VALUES (:repositoryId, :serverId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void insertAssignment(@Param("repositoryId") Long repositoryId, @Param("serverId") Long serverId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM cmp.repository_assignment WHERE repository_id = :repositoryId AND server_id = :serverId)", nativeQuery = true)
    boolean existsAssignment(@Param("repositoryId") Long repositoryId, @Param("serverId") Long serverId);

    @Modifying
    @Transactional
    @Query(value = """
                UPDATE cmp.repository
                SET snow_name = :snowName,
                    snow_sys_id = :snowSysId,
                    snow_sys_class = :snowSysClass,
                    snow_last_discovered = :snowLastDiscovered,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, nativeQuery = true)
    void updateSnowFields(@Param("id") Long id,
                          @Param("snowName") String snowName,
                          @Param("snowSysId") String snowSysId,
                          @Param("snowSysClass") String snowSysClass,
                          @Param("snowLastDiscovered") OffsetDateTime snowLastDiscovered);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cmp.repository_has_appservices WHERE repository_id = :repositoryId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("repositoryId") Long repositoryId);

    @Modifying
    @Transactional
    @Query(value = """
                DELETE FROM cmp.repository_has_appservices
                WHERE repository_id = :repositoryId
                AND appservice_id NOT IN (SELECT id FROM cmp.appservice WHERE number IN :numbers)
                """, nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("repositoryId") Long repositoryId, @Param("numbers") List<String> numbers);

    @Modifying
    @Transactional
    @Query(value = """
                INSERT INTO cmp.repository_has_appservices (repository_id, appservice_id)
                SELECT :repositoryId, id FROM cmp.appservice WHERE number IN :numbers
                ON CONFLICT DO NOTHING
                """, nativeQuery = true)
    void addAppServiceAssociations(@Param("repositoryId") Long repositoryId, @Param("numbers") List<String> numbers);

    @Query("SELECT DISTINCT r FROM Repository r LEFT JOIN FETCH r.appservices")
    List<Repository> findAllWithAppservices();

    @Query("SELECT DISTINCT r FROM Repository r LEFT JOIN FETCH r.appservices WHERE r.id = :id")
    Optional<Repository> findByIdWithAppservices(@Param("id") Long id);

    @Query("SELECT DISTINCT r FROM Repository r LEFT JOIN FETCH r.appservices WHERE r.name = :name")
    Optional<Repository> findByNameWithAppservices(@Param("name") String name);

    @Query("SELECT CASE WHEN EXISTS (" +
            "SELECT 1 FROM Repository r WHERE r.id = :id AND SIZE(r.appservices) = 1" +
            ") AND (:isAdmin = TRUE OR EXISTS (" +
            "SELECT 1 FROM Repository r " +
            "JOIN r.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE r.id = :id AND u.username = :username" +
            ")) THEN TRUE ELSE FALSE END")
    Boolean canUserEditRepository(@Param("id") Long id, @Param("username") String username,
                                  @Param("isAdmin") boolean isAdmin);

    @Query(value = """
    SELECT
        r.id AS id,
        r.name AS name,
        r.lock_status AS "lockStatus",
        EXISTS (
            SELECT 1 FROM cmp.user_favorite_repository ufa
            JOIN cmp."user" u_fav ON ufa.user_id = u_fav.id
            WHERE ufa.repositoryid = r.id AND u_fav.username = :username
        ) AS "isFavorite",
        """ + CAN_EDIT_EXPRESSION + """
        AS "canEdit"
    FROM cmp.repository r
    WHERE (
        :isAdmin
        OR :isReadonly
        OR :hasLinuxRole
        OR :hasSecurityRole
        OR :hasOperatorRole
        OR EXISTS (
            SELECT 1
            FROM cmp.repository_has_appservices ra2
                     JOIN cmp.appservice a2 ON ra2.appservice_id = a2.id
                     JOIN cmp."group" g ON a2.change_group_id = g.id
                     JOIN cmp.group_membership gm ON g.id = gm.group_id
                     JOIN cmp.user u on gm.user_id = u.id
            WHERE ra2.repository_id = r.id
              AND u.username = :username
        )
    )
    AND (:search IS NULL OR :search = '' OR r.name ILIKE CONCAT('%', :search, '%'))
    AND (:favorites = FALSE OR EXISTS (
        SELECT 1 FROM cmp.user_favorite_repository ufa
        JOIN cmp."user" u_fav ON ufa.user_id = u_fav.id
        WHERE ufa.repositoryid = r.id AND u_fav.username = :username
    ))
    AND (:editableOnly = FALSE OR (""" + CAN_EDIT_EXPRESSION + """
    )
    )
    ORDER BY
        CASE WHEN EXISTS (
            SELECT 1 FROM cmp.user_favorite_repository ufa
            JOIN cmp."user" u_fav ON ufa.user_id = u_fav.id
            WHERE ufa.repositoryid = r.id AND u_fav.username = :username
        ) THEN 0 ELSE 1 END ASC,
        CASE WHEN :sortOrder = 'asc' THEN r.name END ASC,
        CASE WHEN :sortOrder = 'desc' THEN r.name END DESC
    """, countQuery = """
        SELECT COUNT(r.id)
        FROM cmp.repository r
        WHERE (
            :isAdmin
            OR :isReadonly
            OR :hasLinuxRole
            OR :hasSecurityRole
            OR :hasOperatorRole
            OR EXISTS (
                SELECT 1
                FROM cmp.repository_has_appservices ra2
                         JOIN cmp.appservice a2 ON ra2.appservice_id = a2.id
                         JOIN cmp."group" g ON a2.change_group_id = g.id
                         JOIN cmp.group_membership gm ON g.id = gm.group_id
                         JOIN cmp.user u on gm.user_id = u.id
                WHERE ra2.repository_id = r.id
                  AND u.username = :username
            )
        )
        AND (:search IS NULL OR :search = '' OR r.name ILIKE CONCAT('%', :search, '%'))
        AND (:favorites = FALSE OR EXISTS (
            SELECT 1 FROM cmp.user_favorite_repository ufa
            JOIN cmp."user" u_fav ON ufa.user_id = u_fav.id
            WHERE ufa.repositoryid = r.id AND u_fav.username = :username
        ))
        AND (:editableOnly = FALSE OR (""" + CAN_EDIT_EXPRESSION + """
        )
        )
    """, nativeQuery = true)
    Page<RepositoryListProjection> findVisibleRepositories(@Param("username") String username,
                                                @Param("isAdmin") boolean isAdmin,
                                                @Param("isReadonly") boolean isReadonly,
                                                @Param("hasLinuxRole") boolean hasLinuxRole,
                                                @Param("hasSecurityRole") boolean hasSecurityRole,
                                                @Param("hasOperatorRole") boolean hasOperatorRole,
                                                @Param("search") String search,
                                                @Param("favorites") boolean favorites,
                                                @Param("editableOnly") boolean editableOnly,
                                                @Param("sortOrder") String sortOrder,
                                                Pageable pageable);


    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM cmp.repository r
            WHERE r.id = :id
              AND (
                :isAdmin
                OR :isReadonly
                OR :hasLinuxRole
                OR :hasSecurityRole
                OR :hasOperatorRole
                OR EXISTS (
                    SELECT 1
                    FROM cmp.repository_has_appservices ra2
                             JOIN cmp.appservice a2 ON ra2.appservice_id = a2.id
                             JOIN cmp.group_membership gm ON a2.change_group_id = gm.group_id
                             JOIN cmp."user" u ON gm.user_id = u.id
                    WHERE ra2.repository_id = r.id
                      AND u.username = :username
                )
              )
        )
    """, nativeQuery = true)
    boolean canUserViewRepository(@Param("id") Long id,
                                  @Param("username") String username,
                                  @Param("isAdmin") boolean isAdmin,
                                  @Param("isReadonly") boolean isReadonly,
                                  @Param("hasLinuxRole") boolean hasLinuxRole,
                                  @Param("hasSecurityRole") boolean hasSecurityRole,
                                  @Param("hasOperatorRole") boolean hasOperatorRole);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM cmp.user_favorite_repository ufr
            JOIN cmp."user" u ON ufr.user_id = u.id
            WHERE ufr.repositoryid = :repositoryId AND u.username = :username
        )
    """, nativeQuery = true)
    boolean isFavorite(@Param("repositoryId") Long repositoryId, @Param("username") String username);

    @Query(value = """
    SELECT
        r.id AS id,
        r.name AS name,
        r.lock_status AS "lockStatus",
        EXISTS (
            SELECT 1 FROM cmp.user_favorite_repository ufa
            JOIN cmp."user" u_fav ON ufa.user_id = u_fav.id
            WHERE ufa.repositoryid = r.id AND u_fav.username = :username
        ) AS "isFavorite",
        """ + CAN_EDIT_EXPRESSION + """
        AS "canEdit"
    FROM cmp.repository r
    JOIN cmp.repository_has_appservices rha ON rha.repository_id = r.id
    WHERE rha.appservice_id = :appserviceId
    AND (
        :isAdmin
        OR :isReadonly
        OR :hasLinuxRole
        OR :hasSecurityRole
        OR :hasOperatorRole
        OR EXISTS (
            SELECT 1
            FROM cmp.repository_has_appservices ra2
                     JOIN cmp.appservice a2 ON ra2.appservice_id = a2.id
                     JOIN cmp."group" g ON a2.change_group_id = g.id
                     JOIN cmp.group_membership gm ON g.id = gm.group_id
                     JOIN cmp.user u on gm.user_id = u.id
            WHERE ra2.repository_id = r.id
              AND u.username = :username
        )
    )
    ORDER BY LOWER(r.name)
    """, nativeQuery = true)
    List<RepositoryListProjection> findVisibleByAppserviceId(@Param("appserviceId") Long appserviceId,
                                                             @Param("username") String username,
                                                             @Param("isAdmin") boolean isAdmin,
                                                             @Param("isReadonly") boolean isReadonly,
                                                             @Param("hasLinuxRole") boolean hasLinuxRole,
                                                             @Param("hasSecurityRole") boolean hasSecurityRole,
                                                             @Param("hasOperatorRole") boolean hasOperatorRole);

    @Modifying
    @Query(value = """
        INSERT INTO cmp.user_favorite_repository (user_id, repositoryid)
        SELECT u.id, :repositoryId FROM cmp.user u WHERE u.username = :username
        ON CONFLICT DO NOTHING
    """, nativeQuery = true)
    void addRepositoryToFavorites(@Param("repositoryId") Long repositoryId, @Param("username") String username);

    @Modifying
    @Query(value = """
        DELETE FROM cmp.user_favorite_repository ufr
        WHERE ufr.repositoryid = :repositoryId
          AND ufr.user_id = (SELECT u.id FROM cmp.user u WHERE u.username = :username)
    """, nativeQuery = true)
    void removeRepositoryFromFavorites(@Param("repositoryId") Long repositoryId, @Param("username") String username);

}