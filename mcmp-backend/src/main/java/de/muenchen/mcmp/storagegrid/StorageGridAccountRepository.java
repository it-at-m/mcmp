package de.muenchen.mcmp.storagegrid;

import de.muenchen.mcmp.ontap.OntapVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StorageGridAccountRepository extends JpaRepository<StorageGridAccount, Long> {

    List<StorageGridAccount> findAllByConfigStorageGridId(Long configStorageGridId);

    void deleteAllByConfigStorageGridId(Long configStorageGridId);
}
