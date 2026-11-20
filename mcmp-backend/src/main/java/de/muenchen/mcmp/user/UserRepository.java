package de.muenchen.mcmp.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    User findByUsername(final String username);

    User findBySysId(final String sysId);

    List<User> findByAdminTrueOrderByUsernameAsc();

    List<User> findByAdminFalseOrderByUsernameAsc();

    @Query("SELECT u FROM User u WHERE u.username NOT IN :usernames AND u.admin = false AND u.specialRole = false")
    List<User> findNonSpecialUsersNotInUsernames(@Param("usernames") Set<String> usernames);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) IN :usernames")
    List<User> findByUsernameInIgnoreCase(@Param("usernames") List<String> usernames);

    @Query("SELECT u.username FROM User u WHERE u.admin = true OR u.specialRole = true")
    List<String> findSpecialUsernames();

    @Query("SELECT new de.muenchen.mcmp.user.UserAutocompleteDTO(u.id, u.username, u.name) FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY u.username")
    List<UserAutocompleteDTO> findForAutocomplete(@Param("query") String query);

    @Query("SELECT DISTINCT LOWER(u.department) FROM User u WHERE LOWER(u.department) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY LOWER(u.department)")
    List<String> findDepartmentsLike(@Param("query") String query);

    /**
     * Updates the login timestamp of the user with the given username to the current timestamp
     * if it has not yet been updated during the current day in the Europe/Berlin time zone.
     *
     * @param username the username of the user whose login timestamp should be updated
     * @return the number of rows updated
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
                UPDATE cmp."user"
                   SET updated_at = CURRENT_TIMESTAMP(3)
                 WHERE username = :username
                   AND (updated_at IS NULL
                        OR updated_at < date_trunc('day', now() AT TIME ZONE 'Europe/Berlin')
                                          AT TIME ZONE 'Europe/Berlin')
                """, nativeQuery = true)
    int updateLoginTimestampDaily(@Param("username") String username);
}