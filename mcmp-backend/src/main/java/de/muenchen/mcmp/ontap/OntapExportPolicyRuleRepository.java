package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapExportPolicyRuleRepository extends JpaRepository<OntapExportPolicyRule, Long> {
    List<OntapExportPolicyRule> findAllByPolicyIdIn(List<Long> policyIds);
}