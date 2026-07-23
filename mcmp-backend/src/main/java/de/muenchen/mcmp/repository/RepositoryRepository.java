package de.muenchen.mcmp.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    Optional<Repository> findByName(String name);
    List<Repository> findAllByServersId(Long serverId);

    @Query(value = "SELECT name, id FROM cmp.repository", nativeQuery = true)
    List<RepositoryIdByName> findAllIdsByName();

    @Query(value = "SELECT id FROM cmp.repository WHERE name = :name", nativeQuery = true)
    Optional<Long> findIdByName(@Param("name") String name);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.repository (name, locked)
            VALUES (:name, TRUE)
            ON CONFLICT (name) DO NOTHING
            """, nativeQuery = true)
    void insertIfNotExists(@Param("name") String name);

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
}