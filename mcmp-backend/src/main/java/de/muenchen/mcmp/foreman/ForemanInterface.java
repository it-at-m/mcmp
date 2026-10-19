package de.muenchen.mcmp.foreman;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "foreman_interface")
public class ForemanInterface {
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "foreman_host_id", nullable = false)
    private ForemanHost foremanHost;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "execution", nullable = false)
    private Boolean execution;

    @Column(name = "fqdn", length = Integer.MAX_VALUE)
    private String fqdn;

    @NotNull
    @Column(name = "foreman_id", nullable = false)
    private Long foremanId;

    @Column(name = "identifier", length = Integer.MAX_VALUE)
    private String identifier;

    @Column(name = "ip", length = Integer.MAX_VALUE)
    private String ip;

    @Column(name = "ip6", length = Integer.MAX_VALUE)
    private String ip6;

    @Column(name = "mac", length = Integer.MAX_VALUE)
    private String mac;

    @Column(name = "mtu")
    private Integer mtu;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "managed", nullable = false)
    private Boolean managed;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "\"primary\"", nullable = false)
    private Boolean primary;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "provision", nullable = false)
    private Boolean provision;

    @Column(name = "subnet_name", length = Integer.MAX_VALUE)
    private String subnetName;

    @Column(name = "type", length = Integer.MAX_VALUE)
    private String type;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "virtual", nullable = false)
    private Boolean virtual;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "linux", nullable = false)
    private Boolean linux;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "windows", nullable = false)
    private Boolean windows;


}