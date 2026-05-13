package de.muenchen.mcmp.ontap;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OntapExportPolicyRuleListDto {
    private Long policyId;
    private List<String> clients;
    private List<String> rwRules;
}
