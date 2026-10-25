package de.muenchen.mcmp.foreman;

import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.jspecify.annotations.NonNull;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "foreman_host")
public class ForemanHost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @NotNull
    @Column(name = "foreman_id", nullable = false)
    private Long foremanId;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "hostname", length = Integer.MAX_VALUE)
    private String hostname;

    @Column(name = "mac", length = Integer.MAX_VALUE)
    private String mac;

    @Column(name = "operatingsystem", length = Integer.MAX_VALUE)
    private String operatingsystem;

    @Column(name = "uuid", length = Integer.MAX_VALUE)
    private String uuid;

    @Column(name = "dmi_product_uuid", length = Integer.MAX_VALUE)
    private String dmiProductUUID;

    @Column(name = "dmi_product_serial_number", length = Integer.MAX_VALUE)
    private String dmiProductSerialNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "config_foreman_id", nullable = false)
    private ConfigForeman configForeman;

    @NonNull
    @OneToMany(mappedBy = "foremanHost")
    private Set<ForemanInterface> foremanInterfaces = new LinkedHashSet<>();
    @OneToOne(mappedBy = "foremanHost")
    private Server server;
}