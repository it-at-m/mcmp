package de.muenchen.mcmp.clients.foreman;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public record PropertyUpdate<T>(
        Supplier<T> getter,
        Consumer<T> setter,
        T newValue
) {
    boolean updateIfChanged() {
        try {
            final T currentValue = getter.get();

            if (!Objects.equals(currentValue, newValue)) {
                setter.accept(newValue);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.error("PropertyUpdate failed: {}", e.getMessage(), e);
        }
        return false;
    }

}