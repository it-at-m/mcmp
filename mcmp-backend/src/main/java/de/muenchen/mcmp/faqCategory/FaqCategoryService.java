package de.muenchen.mcmp.faqCategory;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service class for managing FAQ categories.
 */
@Slf4j
@Service
@AllArgsConstructor
public class FaqCategoryService {

    private final FaqCategoryRepository repository;

    /**
     * Retrieves all FAQ categories sorted by their sort order.
     * @return a list of all FAQ categories
     */
    @Transactional(readOnly = true)
    public List<FaqCategory> getAllCategories() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"));
    }

    /**
     * Retrieves an FAQ category by its ID.
     * @param id the ID of the category
     * @return an Optional containing the category if found
     */
    @Transactional(readOnly = true)
    public Optional<FaqCategory> getCategoryById(final Long id) {
        return repository.findById(id);
    }

    /**
     * Saves an FAQ category.
     * @param category the category entity to save
     * @return the saved category
     */
    @Transactional
    public FaqCategory saveCategory(FaqCategory category) {
        log.info("Saving FAQ category: {}", category.getName());
        return repository.save(category);
    }

    /**
     * Deletes an FAQ category by its ID.
     * @param id the ID of the category to delete
     * @throws EntityNotFoundException if the category does not exist
     */
    @Transactional
    public void deleteCategory(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("FAQ Category with ID " + id + " not found.");
        }
        log.info("Deleting FAQ category with id: {}", id);
        repository.deleteById(id);
    }
}