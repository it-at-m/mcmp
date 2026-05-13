package de.muenchen.mcmp.price;

import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/price")
public class PriceController {

    private final PriceService priceService;

    @GetMapping
    public List<PriceDTO> getAllPrices() {
        return priceService.getAllPrices();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    public PriceDTO createPrice(@RequestBody final PriceDTO priceDTO) {
        return priceService.createPrice(priceDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public PriceDTO updatePrice(@RequestBody final PriceDTO priceDTO) {
        return priceService.updatePrice(priceDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/{name}")
    public void deletePrice(@PathVariable("name") final String name) {
        priceService.deletePrice(name);
    }
}
