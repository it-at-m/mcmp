package de.muenchen.mcmp.changelog;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "changelog")
@DynamicUpdate
public class Changelog extends AbstractEntity {

    /**
     * The application version this changelog entry refers to (e.g., "1.0.4").
     */
    @Size(max = 100)
    @Column(name = "app_version", length = 100)
    private String appVersion;

    /**
     * The raw content of the changelog in Markdown format.
     */
    @Column(name = "content_markdown", length = Integer.MAX_VALUE)
    private String contentMarkdown;

    /**
     * The rendered content of the changelog in HTML format.
     */
    @Column(name = "content_html", length = Integer.MAX_VALUE)
    private String contentHtml;

    /**
     * The user who created this changelog entry.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;
}