package de.muenchen.mcmp.user;

import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository repository;
    private final UserMapper userMapper;

    public Optional<User> findByUsername(final String username) {
        return Optional.ofNullable(repository.findByUsername(username));
    }

    public List<UserDTO> getAdminUsers() {
        return userMapper.toDTOs(repository.findByAdminTrueOrderByUsernameAsc());
    }

    public List<UserDTO> getNotAdminUsers() {
        return userMapper.toDTOs(repository.findByAdminFalseOrderByUsernameAsc());
    }

    public void updateAdminPermission(final UserDTO userDTO) {
        User toUpdateUser = repository.findById(userDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("User mit ID " + userDTO.id() + " existiert nicht."));
        User updatedAwxConfig = userMapper.toEntity(userDTO);
        toUpdateUser.setVersion(toUpdateUser.getVersion());
        toUpdateUser.setAdmin(updatedAwxConfig.getAdmin());
        repository.save(toUpdateUser);
    }

    public List<User> findByUsernameIn(final List<String> usernames) {
        List<String> lowercaseUsernames = usernames.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        return repository.findByUsernameInIgnoreCase(lowercaseUsernames);
    }

    public User save(User user) {
        if (user == null) {
            log.debug("Cannot save null user");
            return null;
        }

        User saved = repository.save(user);
        log.info("Saved user with ID {} and username '{}'", saved.getId(), saved.getUsername());
        return saved;
    }

    public void updateDarkMode(final String username, final boolean darkMode) {
        try {
            final User user = repository.findByUsername(username);
            if (user != null) {
                if (user.getDarkMode() != darkMode) {
                    user.setDarkMode(darkMode);
                    repository.save(user);
                }
            } else {
                log.warn("User {} not found, cannot update dark mode", username);
            }
        } catch (Exception e) {
            log.error("Error while saving user dark mode", e);
        }
    }

    public boolean getDarkMode(final String username) {
        return findByUsername(username)
                .map(User::getDarkMode)
                .orElse(false);
    }

    public void updateLoginPage(String username, String loginPage) {
        User user = repository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found: " + username);
        }
        user.setLoginPage(loginPage);
        repository.save(user);
    }

    public String getLoginPage(String username) {
        User user = repository.findByUsername(username);
        return user != null ? user.getLoginPage() : null;
    }

    public List<UserAutocompleteDTO> searchUsersForAutocomplete(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return repository.findForAutocomplete(query.trim());
    }

    public List<String> searchDepartments(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return repository.findDepartmentsLike(query.trim());
    }

    @Transactional(readOnly = true)
    public Page<UserHistoryDTO> searchUsers(
            final String searchText,
            final Instant updatedAtFrom,
            final Pageable pageable
    ) {
        Specification<User> spec = buildSearchSpecification(
                searchText,
                updatedAtFrom
        );

        return repository.findAll(spec, pageable)
                .map(user -> new UserHistoryDTO(
                        user.getId(),
                        user.getUsername(),
                        user.getName(),
                        user.getEmail(),
                        user.getCreatedAt(),
                        user.getUpdatedAt()
                ));
    }

    private Specification<User> buildSearchSpecification(
            final String searchText,
            final Instant updatedAtFrom
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(searchText)) {
                String pattern = "%" + searchText.trim().toLowerCase() + "%";
                Predicate usernameLike = cb.like(cb.lower(root.get("username")), pattern);
                Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
                predicates.add(cb.or(usernameLike, nameLike, emailLike));
            }

            if (updatedAtFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), updatedAtFrom));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLastLogin(final String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }

        final int updated = repository.updateLoginTimestampDaily(username);
        if (updated > 0) {
            log.info("Login timestamp (updated_at) for user '{}' updated", username);
        } else {
            log.debug("Login timestamp for user '{}' not updated (already set today or user unknown)", username);
        }
    }
}