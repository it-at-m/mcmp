package de.muenchen.mcmp.foreman;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ForemanHostRepository extends JpaRepository<ForemanHost, Long> {

    Optional<ForemanHost> findByForemanIdAndConfigForemanId(Long foremanId, Long configForemanId);

    /**
     * Loads all Foreman hosts that belong to any of the given {@code config_foreman} entries.
     * Used for the bulk synchronization of the Foreman import.
     */
    List<ForemanHost> findAllByConfigForemanIdIn(Collection<Long> configForemanIds);

    @Query("""
            select new de.muenchen.mcmp.foreman.UnmappedForemanHostDTO(
                c.fqdn as foreman,
                f.foremanId,
                f.name,
                f.hostname,
                f.mac,
                f.operatingsystem,
                f.uuid,
                f.dmiProductUUID,
                f.dmiProductSerialNumber
            )
            from ForemanHost f
            join f.configForeman c
            left join Server s on s.foremanHost = f
            where s is null
            order by f.name
            """)
    List<UnmappedForemanHostDTO> findUnmappedForemanHosts();

    /**
     * Retrieves rows that represent IP mismatches between servers and their respective foreman configurations.
     * A mismatch is identified if the IP information in the CMP (Configuration Management Platform)
     * and Foreman systems does not align. This includes scenarios where:
     * - A server exists only in CMP or Foreman.
     * - There are discrepancies in assigned IPv4 addresses between the two systems.
     * - Certain required configurations (e.g., NIC, IPv4) are missing.
     *
     * The query joins multiple tables to compare server and foreman configurations, identifying mismatches
     * and categorizing them under different status conditions. The results are ordered primarily by server name
     * and then by the available IPs in CMP and Foreman.
     *
     * @return A list of {@code ForemanIpMismatchDTO} objects containing details of servers with IP mismatches or
     *         configuration issues. Each item includes server name, power state, foreman host details,
     *         CMP and Foreman IP addresses, and a status describing the type of mismatch.
     */
    @Query(value = """

            WITH server_basis AS (
              SELECT s.id                     AS server_id,
                     s.name                   AS server_name,
                     s.power_state            AS power_state,
                     fh.name                  AS foreman_host_name,
                     cf.fqdn                  as foreman_fqdn,
                     EXISTS (SELECT 1 FROM cmp.nic n
                             WHERE n.server_id = s.id)                       AS has_nic,
                     EXISTS (SELECT 1 FROM cmp.nic n
                                               JOIN cmp.ip_assignment ia ON ia.nic_id = n.id
                                               JOIN cmp.ip i ON i.id = ia.ip_id
                             WHERE n.server_id = s.id AND i.ip_type = 'IPv4') AS has_nic_ipv4,
                     EXISTS (SELECT 1 FROM cmp.foreman_interface fi
                             WHERE fi.foreman_host_id = fh.id)               AS has_foreman_interface,
                     EXISTS (SELECT 1 FROM cmp.foreman_interface fi
                             WHERE fi.foreman_host_id = fh.id
                               AND fi.ip IS NOT NULL
                               AND btrim(fi.ip) ~ '^[0-9]{1,3}(\\.[0-9]{1,3}){3}$') AS has_foreman_ipv4
              FROM cmp.server s
                       JOIN cmp.foreman_host fh ON fh.id = s.foreman_host_id
                       JOIN cmp.config_foreman cf ON fh.config_foreman_id = cf.id
            ),
               cmp_ipv4 AS (
                   SELECT s.id  AS server_id,
                          n.device AS server_if,
                          n.tools_network_name AS server_network,
                          i.ip  AS server_ip
                   FROM cmp.server s
                            JOIN cmp.nic n            ON n.server_id = s.id
                            JOIN cmp.ip_assignment ia ON ia.nic_id = n.id
                            JOIN cmp.ip i             ON i.id = ia.ip_id
                   WHERE s.foreman_host_id IS NOT NULL
                     AND i.ip_type = 'IPv4'
               ),
               foreman_ipv4 AS (
                   SELECT s.id         AS server_id,
                          fi.identifier AS foreman_if,
                          fi.subnet_name AS foreman_network,
                          btrim(fi.ip) AS foreman_ip
                   FROM cmp.server s
                            JOIN cmp.foreman_host fh      ON fh.id = s.foreman_host_id
                            JOIN cmp.foreman_interface fi ON fi.foreman_host_id = fh.id
                   WHERE fi.ip IS NOT NULL
                     AND btrim(fi.ip) ~ '^[0-9]{1,3}(\\.[0-9]{1,3}){3}$'
               ),
               vergleich AS (
                   SELECT COALESCE(c.server_id, f.server_id) AS server_id,
                          c.server_if,
                          f.foreman_if,
                          c.server_network,
                          f.foreman_network,
                          c.server_ip,
                          f.foreman_ip,
                          CASE
                              WHEN c.server_id IS NULL THEN 'nur in Foreman'
                              WHEN f.server_id IS NULL THEN 'nur in MCMP'
                              ELSE 'match'
                              END AS status
                   FROM cmp_ipv4 c
                            FULL OUTER JOIN foreman_ipv4 f
                                            ON c.server_id = f.server_id
                                                AND c.server_ip    = f.foreman_ip
               ),
               ergebnis AS (
                   SELECT b.server_id,
                          b.server_name,
                          b.power_state,
                          b.foreman_host_name,
                          v.server_if,
                          v.foreman_if,
                          v.server_network,
                          v.foreman_network,
                          b.foreman_fqdn,
                          v.server_ip,
                          v.foreman_ip,
                          CASE
                              WHEN v.status IS NOT NULL                        THEN v.status
                              WHEN NOT b.has_nic                               THEN 'kein NIC'
                              WHEN NOT b.has_foreman_interface                 THEN 'kein Foreman Interface'
                              WHEN NOT b.has_nic_ipv4 AND NOT b.has_foreman_ipv4 THEN 'keine IPv4'
                              WHEN NOT b.has_nic_ipv4                          THEN 'keine IPv4 MCMP'
                              WHEN NOT b.has_foreman_ipv4                      THEN 'keine IPv4 Foreman'
                              ELSE 'kein Vergleich'
                              END AS status
                   FROM server_basis b
                            LEFT JOIN vergleich v ON v.server_id = b.server_id
               )
            SELECT
              server_id,
              server_name,
              power_state,
              foreman_host_name,
              foreman_fqdn,
              server_ip,
              foreman_ip,
              status,
              server_if,
              foreman_if,
              server_network,
              foreman_network
            FROM ergebnis
            WHERE status <> 'match'
            ORDER BY server_name, server_ip NULLS LAST, foreman_ip NULLS LAST
                """, nativeQuery = true)
    List<ForemanIpMismatchDTO> findForemanIpMismatchRows();

    /**
     * Identifies and retrieves a list of Foreman IP mismatches grouped by server.
     * This method aggregates data on discrepancies between the IPs known in the Foreman system
     * and the IPs recorded in the associated CMP system. The mismatches can occur due to differences
     * in network interfaces, missing configurations, or other inconsistencies.
     *
     * @return a list of {@code ForemanIpMismatchServerDTO} instances, each representing
     *         a server with mismatched IP information from the Foreman and CMP systems.
     */
    default List<ForemanIpMismatchServerDTO> findForemanIpMismatches() {
        return ForemanIpMismatchServerDTO.groupByServer(findForemanIpMismatchRows());
    }
}