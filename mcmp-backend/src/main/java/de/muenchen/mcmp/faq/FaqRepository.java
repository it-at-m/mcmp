package de.muenchen.mcmp.faq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link Faq} entities.
 */
@Repository
public interface FaqRepository extends JpaRepository<Faq, Long> {
}