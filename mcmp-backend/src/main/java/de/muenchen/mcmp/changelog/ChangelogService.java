package de.muenchen.mcmp.changelog;

import de.muenchen.mcmp.markdown.MarkdownService;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service class for managing application changelogs.
 */
@Slf4j
@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class ChangelogService {

    private final ChangelogRepository repository;
    private final UserService userService;
    private final MarkdownService markdownService;

    /**
     * Retrieves a paged list of all published changelogs.
     * @param pageable pagination information
     * @return a page of published changelogs
     */
    public Page<Changelog> getPublishedChangelogs(final Pageable pageable) {
        return repository.findAllByIsPublishedTrue(pageable);
    }

    /**
     * Retrieves a paged list of all changelogs.
     * @param pageable pagination information
     * @return a page of changelogs
     */
    public Page<Changelog> getAllChangelogs(final Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * Retrieves a changelog by its ID.
     * @param id the ID of the changelog
     * @return an Optional containing the changelog if found
     */
    public Optional<Changelog> getChangelogById(final Long id) {
        return repository.findById(id);
    }

    /**
     * Saves a changelog entry. If no user is assigned, the current authenticated user is set.
     * Converts Markdown content to sanitized HTML before saving.
     * @param changelog the changelog entity to save
     * @return the saved changelog
     */
    @Transactional
    public Changelog saveChangelog(Changelog changelog) {
        if (changelog.getUser() == null) {
            String username = AuthUtils.getCurrentUserRoles().getUsername();
            userService.findByUsername(username).ifPresent(changelog::setUser);
        }

        if (changelog.getIsPublished() == null) {
            changelog.setIsPublished(false);
        }

        // Convert Markdown to HTML via MarkdownService
        if (changelog.getContentMarkdown() != null) {
            changelog.setContentHtml(markdownService.convertToHtml(changelog.getContentMarkdown()));
        }

        log.info("Saving changelog entry for version: {}", changelog.getAppVersion());
        return repository.save(changelog);
    }

    /**
     * Deletes a changelog entry by its ID.
     * @param id the ID of the changelog to delete
     * @throws EntityNotFoundException if the changelog does not exist
     */
    @Transactional
    public void deleteChangelog(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Changelog with ID " + id + " not found.");
        }
        log.info("Deleting changelog entry with id: {}", id);
        repository.deleteById(id);
    }

}
