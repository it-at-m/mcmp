package de.muenchen.mcmp.ontap;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class OntapExportPolicyListDto {
    private Long exportPolicyId;
    private String name;
    private Set<OntapExportPolicyRuleListDto> ontapExportPolicyRules;
}
