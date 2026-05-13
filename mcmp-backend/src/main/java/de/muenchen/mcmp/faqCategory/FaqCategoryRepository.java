package de.muenchen.mcmp.faqCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link FaqCategory} entities.
 * Provides standard CRUD operations and database access.
 */
@Repository
public interface FaqCategoryRepository extends JpaRepository<FaqCategory, Long> {
}
