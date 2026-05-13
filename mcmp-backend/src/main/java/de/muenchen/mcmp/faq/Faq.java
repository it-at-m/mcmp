package de.muenchen.mcmp.faq;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.faqCategory.FaqCategory;
import de.muenchen.mcmp.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Entity representing a Frequently Asked Question (FAQ).
 */
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "faq")
@DynamicUpdate
public class Faq extends AbstractEntity {

    /**
     * The category this FAQ belongs to.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    private FaqCategory category;

    /**
     * The question text.
     */
    @Column(name = "question", length = Integer.MAX_VALUE)
    private String question;

    /**
     * The answer in Markdown format.
     */
    @Column(name = "answer_markdown", length = Integer.MAX_VALUE)
    private String answerMarkdown;

    /**
     * The answer rendered as HTML.
     */
    @Column(name = "answer_html", length = Integer.MAX_VALUE)
    private String answerHtml;

    /**
     * Order in which the FAQ should be displayed.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /**
     * Whether the FAQ is visible to users.
     */
    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;

    /**
     * The user who last edited or created this FAQ.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;


}