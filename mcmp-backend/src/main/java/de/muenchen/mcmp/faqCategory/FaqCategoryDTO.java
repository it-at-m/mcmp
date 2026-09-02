package de.muenchen.mcmp.faqCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for {@link FaqCategory} entities.
 * Used for transferring FAQ category data between the server and clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaqCategoryDTO {
    /**
     * Unique identifier of the category.
     */
    private Long id;
    private Long version;

    /**
     * Name of the category.
     */
    @NotBlank
    @Size(max = 100)
    private String name;

    /**
     * Brief description of the category.
     */
    private String description;

    /**
     * Order in which the category should be displayed.
     */
    @NotNull
    private Integer sortOrder;
}