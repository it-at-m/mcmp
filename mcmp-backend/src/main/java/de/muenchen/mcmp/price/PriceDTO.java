package de.muenchen.mcmp.price;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PriceDTO(
        String name,
        BigDecimal pricePerUnit,
        String currency,
        String description
) {}