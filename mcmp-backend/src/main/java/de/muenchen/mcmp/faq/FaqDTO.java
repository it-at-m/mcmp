package de.muenchen.mcmp.faq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

/**
 * Data Transfer Object for transferring FAQ data between layers.
 */
@Data
@Builder
public class FaqDTO {
    private Long id;
    private Long version;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotBlank(message = "Question cannot be empty")
    private String question;

    private String answerMarkdown;
    private String answerHtml;

    @NotNull(message = "Sort order is required")
    private Integer sortOrder;

    @NotNull(message = "Published status is required")
    private Boolean isPublished;

    private Long userId;
}