package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "ontap_export_policy_rule")
public class OntapExportPolicyRule extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "policy_id", nullable = false)
    private OntapExportPolicy policy;

    @Column(name = "policy_id", insertable = false, updatable = false)
    private Long policyId;

    @Column(name = "index")
    private Long index;

    @Column(name = "clients")
    private List<String> clients;

    @Column(name = "protocols")
    private List<String> protocols;

    @Column(name = "rw_rules")
    private List<String> rwRules;

    @Column(name = "ro_rules")
    private List<String> roRules;
}
