package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapCifsShareRepository extends JpaRepository<OntapCifsShare, Long> {
    List<OntapCifsShare> findAllByVolumeIdIn(List<Long> volumeIds);

    List<OntapCifsShare> findAllByQtreeIdIn(List<Long> qtreeIds);
}