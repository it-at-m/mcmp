package de.muenchen.mcmp.changelog;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Data Transfer Object for {@link Changelog} entities.
 * Used for transferring changelog data between the server and clients.
 */
@Data
@Builder
public class ChangelogDTO {
    private Long id;
    private String appVersion;
    private String contentMarkdown;
    private String contentHtml;
    private String authorName;
    private OffsetDateTime createdAt;
    private Boolean isPublished;
}