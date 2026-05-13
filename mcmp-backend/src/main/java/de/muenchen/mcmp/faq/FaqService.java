package de.muenchen.mcmp.faq;

import de.muenchen.mcmp.faqCategory.FaqCategoryRepository;
import de.muenchen.mcmp.markdown.MarkdownService;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service class for managing FAQ entries.
 */
@Slf4j
@Service
@AllArgsConstructor
public class FaqService {

    private final FaqRepository repository;
    private final FaqCategoryRepository categoryRepository;
    private final MarkdownService markdownService;

    /**
     * Retrieves all FAQ entries sorted by their defined sort order.
     * @return a list of all FAQs
     */
    @Transactional(readOnly = true)
    public List<Faq> getAllFaqs() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"));
    }

    /**
     * Finds a specific FAQ by its ID.
     * @param id the FAQ ID
     * @return an optional containing the FAQ if found
     */
    @Transactional(readOnly = true)
    public Optional<Faq> getFaqById(final Long id) {
        return repository.findById(id);
    }

    /**
     * Saves or updates a FAQ entry.
     * @param faq the entity to save
     * @return the saved entity
     */
    @Transactional
    public Faq saveFaq(Faq faq) {
        log.info("Saving FAQ entry: {}", faq.getQuestion());

        if (faq.getAnswerMarkdown() != null) {
            faq.setAnswerHtml(markdownService.convertToHtml(faq.getAnswerMarkdown()));
        }

        if (faq.getCategory() != null && faq.getCategory().getId() != null) {
            var managedCategory = categoryRepository.findById(faq.getCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found"));
            faq.setCategory(managedCategory);
        }

        return repository.save(faq);
    }

    /**
     * Deletes a FAQ entry by ID.
     * @param id the ID of the FAQ to delete
     * @throws EntityNotFoundException if the FAQ doesn't exist
     */
    @Transactional
    public void deleteFaq(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("FAQ with ID " + id + " not found.");
        }
        log.info("Deleting FAQ entry with id: {}", id);
        repository.deleteById(id);
    }
}