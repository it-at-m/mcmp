package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table(name = "app_config")
public class AppConfig extends AbstractEntity {

    @NotNull
    @Column(name = "config_key", nullable = false, length = Integer.MAX_VALUE)
    private String configKey;

    @NotNull
    @Column(name = "config_value", nullable = false, length = Integer.MAX_VALUE)
    private String configValue;

    @NotNull
    @Column(name = "updated_by", nullable = false, length = Integer.MAX_VALUE)
    private String updatedBy;
}
