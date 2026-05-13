package de.muenchen.mcmp.server;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "server_custom_attribute")
public class ServerCustomAttribute extends AbstractEntity {
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "name", columnDefinition = "text")
    private String name;

    @Column(name = "value", columnDefinition = "text")
    private String value;
}
