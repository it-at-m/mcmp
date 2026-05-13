package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapCifsShareAclRepository extends JpaRepository<OntapCifsShareAcl, Long> {
    List<OntapCifsShareAcl> findAllByShareIdIn(List<Long> shareIds);
}