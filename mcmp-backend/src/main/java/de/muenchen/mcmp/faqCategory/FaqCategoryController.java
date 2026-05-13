package de.muenchen.mcmp.faqCategory;

import de.muenchen.mcmp.security.IsAdmin;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing FAQ categories.
 * Provides endpoints to list, create, update, and delete categories.
 */
@RestController
@RequestMapping(value = "/faq-categories")
@AllArgsConstructor
public class FaqCategoryController {

    private final FaqCategoryService faqCategoryService;
    private final FaqCategoryMapper faqCategoryMapper;

    /**
     * Returns a list of all categories sorted by sort order.
     *
     * @return a list of category DTOs
     */
    @GetMapping
    public List<FaqCategoryDTO> getAllCategories() {
        return faqCategoryMapper.toDTOList(faqCategoryService.getAllCategories());
    }

    /**
     * Returns a single category by its ID.
     *
     * @param id the ID of the category
     * @return the category DTO or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<FaqCategoryDTO> getCategoryById(@PathVariable Long id) {
        return faqCategoryService.getCategoryById(id)
                .map(faqCategoryMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new FAQ category.
     * Restricted to administrators.
     *
     * @param dto the category data
     * @return the created category DTO
     */
    @PostMapping
    @IsAdmin
    public ResponseEntity<FaqCategoryDTO> createCategory(@Valid @RequestBody FaqCategoryDTO dto) {
        FaqCategory entity = faqCategoryMapper.toEntity(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(faqCategoryMapper.toDTO(faqCategoryService.saveCategory(entity)));
    }

    /**
     * Updates an existing category.
     * Restricted to administrators.
     *
     * @param id  the ID of the category to update
     * @param dto the new category data
     * @return the updated category DTO or 404 Not Found
     */
    @PutMapping("/{id}")
    @IsAdmin
    public ResponseEntity<FaqCategoryDTO> updateCategory(@PathVariable Long id, @Valid @RequestBody FaqCategoryDTO dto) {
        return faqCategoryService.getCategoryById(id)
                .map(existing -> {
                    faqCategoryMapper.updateEntityFromDto(dto, existing);
                    return ResponseEntity.ok(faqCategoryMapper.toDTO(faqCategoryService.saveCategory(existing)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a category by its ID.
     * Restricted to administrators.
     *
     * @param id the ID of the category to delete
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @IsAdmin
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        faqCategoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}