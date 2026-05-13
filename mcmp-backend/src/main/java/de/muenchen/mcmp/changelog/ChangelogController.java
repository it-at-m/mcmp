package de.muenchen.mcmp.changelog;

import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.IsAdmin;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing {@link Changelog} entities.
 * Provides endpoints for creating, reading, updating, and deleting changelog entries.
 */
@RestController
@RequestMapping(value = "/changelogs")
@AllArgsConstructor
public class ChangelogController {

    private final ChangelogService changeLogService;
    private final ChangelogMapper changelogMapper;

    /**
     * Retrieves a paginated list of all changelogs.
     *
     * @param offset the starting offset for pagination (default is 0)
     * @param limit  the maximum number of entries to return (default is 5).
     *               If set to -1, returns all entries unpaged.
     * @return a {@link Page} of {@link ChangelogDTO} objects
     */
    @GetMapping
    public Page<ChangelogDTO> getAllChangelogs(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        final Pageable pageable = (limit == -1)
                ? Pageable.unpaged()
                : new OffsetBasedPageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "id"));
        if (!AuthUtils.isAdmin()) {
            return changeLogService.getPublishedChangelogs(pageable).map(changelogMapper::toDTO);
        }
        return changeLogService.getAllChangelogs(pageable).map(changelogMapper::toDTO);
    }

    /**
     * Retrieves a specific changelog by its ID.
     *
     * @param id the ID of the changelog to retrieve
     * @return a {@link ResponseEntity} containing the {@link ChangelogDTO} if found,
     *         or a 404 Not Found response
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChangelogDTO> getChangelogById(@PathVariable Long id) {
        return changeLogService.getChangelogById(id)
                .filter(changelog -> changelog.getIsPublished() || AuthUtils.isAdmin())
                .map(changelogMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new changelog entry.
     * <p>
     * Restricted to users with administrative privileges.
     *
     * @param changelogDTO the data transfer object containing the changelog details
     * @return a {@link ResponseEntity} containing the created {@link ChangelogDTO} and 201 Created status
     */
    @PostMapping
    @IsAdmin
    public ResponseEntity<ChangelogDTO> createChangelog(@Valid @RequestBody ChangelogDTO changelogDTO) {
        Changelog changelog = changelogMapper.toEntity(changelogDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(changelogMapper.toDTO(changeLogService.saveChangelog(changelog)));
    }

    /**
     * Updates an existing changelog entry.
     * <p>
     * Restricted to users with administrative privileges.
     *
     * @param id           the ID of the changelog to update
     * @param changelogDTO the updated changelog data
     * @return a {@link ResponseEntity} containing the updated {@link ChangelogDTO} if found,
     *         or a 404 Not Found response
     */
    @PutMapping("/{id}")
    @IsAdmin
    public ResponseEntity<ChangelogDTO> updateChangelog(@PathVariable Long id, @Valid @RequestBody ChangelogDTO changelogDTO) {
        return changeLogService.getChangelogById(id)
                .map(existing -> {
                    changelogMapper.updateEntityFromDto(changelogDTO, existing);
                    return ResponseEntity.ok(changelogMapper.toDTO(changeLogService.saveChangelog(existing)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a changelog entry by its ID.
     * <p>
     * Restricted to users with administrative privileges.
     *
     * @param id the ID of the changelog to delete
     * @return a {@link ResponseEntity} with 204 No Content status
     */
    @DeleteMapping("/{id}")
    @IsAdmin
    public ResponseEntity<Void> deleteChangelog(@PathVariable Long id) {
        changeLogService.deleteChangelog(id);
        return ResponseEntity.noContent().build();
    }
}
