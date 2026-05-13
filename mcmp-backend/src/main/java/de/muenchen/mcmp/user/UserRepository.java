package de.muenchen.mcmp.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

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
}
