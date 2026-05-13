package de.muenchen.mcmp.faqCategory;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Entity representing a category for FAQ entries.
 * Extends {@link AbstractEntity} for common fields like ID and timestamps.
 */
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "faq_category")
@DynamicUpdate
public class FaqCategory extends AbstractEntity {

    /**
     * The name of the category.
     */
    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * A detailed description of the category.
     */
    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    /**
     * Numeric value used to define the display order.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;


}