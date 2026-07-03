package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LbPoolMemberRepository extends JpaRepository<LbPoolMember, Long> {

    @Query(value = """
            SELECT
                lvs.id        AS vsId,
                lvs.domains->>0 AS vsDomain,
                lp.name       AS poolName,
                lpm.ip        AS memberIp,
                lpm.port      AS memberPort
            FROM cmp.lb_pool_member lpm
            JOIN cmp.lb_pool lp ON lpm.pool_id = lp.id
            JOIN cmp.lb_virtual_server_pool_ref lvspr ON lvspr.lb_pool_id = lp.id
            JOIN cmp.lb_virtual_server lvs ON lvs.id = lvspr.lb_virtual_server_id
            WHERE lpm.server_id = :serverId
            ORDER BY lvs.name, lp.name
            """, nativeQuery = true)
    List<LbServerMembershipProjection> findMembershipsByServerId(@Param("serverId") Long serverId);
}
