package de.muenchen.mcmp.price;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@AllArgsConstructor
public class PriceService {

    private final PriceRepository priceRepository;
    private final PriceMapper priceMapper;

    public List<PriceDTO> getAllPrices() {
        return priceRepository.findAll().stream().map(priceMapper::toDTO).toList();
    }

    public PriceDTO createPrice(final PriceDTO priceDTO) {
        return priceMapper.toDTO(priceRepository.save(priceMapper.toEntity(priceDTO)));
    }

    public PriceDTO updatePrice(final PriceDTO priceDTO) {
        Price existingPrice = priceRepository.findByName(priceDTO.name());
        if (existingPrice == null) {
            throw new NoSuchElementException("Price with name " + priceDTO.name() + " does not exist.");
        }
        Price updatedPrice = priceMapper.toEntity(priceDTO);
        updatedPrice.setId(existingPrice.getId());
        updatedPrice.setVersion(existingPrice.getVersion());
        updatedPrice.setCreatedAt(existingPrice.getCreatedAt());
        updatedPrice.setUpdatedAt(Date.from(Instant.now()));

        return priceMapper.toDTO(priceRepository.save(updatedPrice));
    }

    public void deletePrice(final String name) {
        Price existingPrice = priceRepository.findByName(name);
        if (existingPrice == null) {
            throw new NoSuchElementException("Price with name " + name + " does not exist.");
        }
        priceRepository.delete(existingPrice);
    }
}
