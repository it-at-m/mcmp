package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapAggregateRepository extends JpaRepository<OntapAggregate, Long> {

    List<OntapAggregate> findAllByOntapClusterId(Long clusterId);

    void deleteAllByOntapClusterId(Long clusterId);

    @Query("SELECT a.aggregateUuid, v.volumeUuid FROM OntapAggregate a JOIN a.ontapVolumes v WHERE a.ontapCluster.id = :clusterId")
    List<Object[]> findAggregateVolumeUuids(@Param("clusterId") Long clusterId);
}