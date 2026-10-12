package de.muenchen.mcmp.ip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IPRepository extends JpaRepository<IP, Long> {

    @Query(value = """
            SELECT ia.nic_id AS "nicId",
                   i.ip      AS "ip"
            FROM cmp.ip_assignment ia
            JOIN cmp.ip i ON i.id = ia.ip_id
            ORDER BY ia.nic_id, i.ip
            """, nativeQuery = true)
    List<IPNicDTO> findAllNicIdWithIp();
}
