package de.muenchen.mcmp.user;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public void updateAdminPermission(final UserDTO userDTO)
    {
        User toUpdateUser = repository.findById(userDTO.id()).orElseThrow(
                () -> new IllegalArgumentException("User mit ID " + userDTO.id() + " existiert nicht."));
        User updatedAwxConfig =  userMapper.toEntity(userDTO);
        toUpdateUser.setVersion(toUpdateUser.getVersion());
        toUpdateUser.setAdmin(updatedAwxConfig.getAdmin());
        repository.save(toUpdateUser);
    }

    public List<User> findByUsernameIn(final List<String> usernames) {
        // Konvertiere alle Usernames zu lowercase für case-insensitive Suche
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

    public List<UserAutocompleteDTO> searchUsersForAutocomplete(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return repository.findForAutocomplete(query.trim());
    }
}
