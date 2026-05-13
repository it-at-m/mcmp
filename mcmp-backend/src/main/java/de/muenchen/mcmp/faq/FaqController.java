package de.muenchen.mcmp.faq;

import de.muenchen.mcmp.security.IsAdmin;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing FAQ entries.
 */
@RestController
@RequestMapping(value = "/faqs")
@AllArgsConstructor
public class FaqController {

    private final FaqService faqService;
    private final FaqMapper faqMapper;

    /**
     * Retrieves all FAQ entries.
     * @return list of FAQ DTOs
     */
    @GetMapping
    public List<FaqDTO> getAllFaqs() {
        return faqMapper.toDTOList(faqService.getAllFaqs());
    }

    /**
     * Retrieves a single FAQ by ID.
     * @param id the FAQ ID
     * @return the FAQ or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<FaqDTO> getFaqById(@PathVariable Long id) {
        return faqService.getFaqById(id)
                .map(faqMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new FAQ entry. Requires Admin privileges.
     * @param dto the data for the new FAQ
     * @return the created FAQ
     */
    @PostMapping
    @IsAdmin
    public ResponseEntity<FaqDTO> createFaq(@Valid @RequestBody FaqDTO dto) {
        Faq entity = faqMapper.toEntity(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(faqMapper.toDTO(faqService.saveFaq(entity)));
    }

    /**
     * Updates an existing FAQ entry. Requires Admin privileges.
     * @param id the ID of the FAQ to update
     * @param dto the new data
     * @return the updated FAQ or 404 Not Found
     */
    @PutMapping("/{id}")
    @IsAdmin
    public ResponseEntity<FaqDTO> updateFaq(@PathVariable Long id, @Valid @RequestBody FaqDTO dto) {
        return faqService.getFaqById(id)
                .map(existing -> {
                    faqMapper.updateEntityFromDto(dto, existing);
                    return ResponseEntity.ok(faqMapper.toDTO(faqService.saveFaq(existing)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a FAQ entry. Requires Admin privileges.
     * @param id the ID of the FAQ to delete
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @IsAdmin
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        faqService.deleteFaq(id);
        return ResponseEntity.noContent().build();
    }
}