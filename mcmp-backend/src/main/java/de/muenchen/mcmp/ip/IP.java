package de.muenchen.mcmp.ip;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.types.IPType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "ip")
public class IP extends AbstractEntity {

    @Size(max = 45)
    @NotNull
    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "ip_type", nullable = false, columnDefinition = "ip_type")
    private IPType ipType;

    @Size(max = 100)
    @Column(name = "dns_name", length = 100)
    private String dnsName;

    @Size(max = 17)
    @Column(name = "dns_mac", length = 17)
    private String dnsMac;

}