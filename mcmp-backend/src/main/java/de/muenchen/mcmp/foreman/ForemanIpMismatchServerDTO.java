package de.muenchen.mcmp.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing a server with mismatched IP address information between Foreman
 * and another source. This class encapsulates details about the server and its associated
 * mismatched IP entries.
 *
 * @param serverId        Unique identifier of the server.
 * @param serverName      Name of the server.
 * @param powerState      Power state of the server.
 * @param foremanHostName Hostname of the server as recorded in Foreman.
 * @param foremanFQDN     Fully Qualified Domain Name of the server as recorded in Foreman.
 * @param ips             List of IP mismatches associated with the server.
 */
public record ForemanIpMismatchServerDTO(
        @JsonProperty("server_id")
        Long serverId,

        @JsonProperty("server_name")
        String serverName,

        @JsonProperty("power_state")
        String powerState,

        @JsonProperty("foreman_host_name")
        String foremanHostName,

        @JsonProperty("foreman_fqdn")
        String foremanFQDN,

        @JsonProperty("ips")
        List<MismatchIp> ips
) {
    public ForemanIpMismatchServerDTO {
        ips = ips == null ? List.of() : List.copyOf(ips);
    }

    /**
     * Groups a list of ForemanIpMismatchDTO records by server properties and aggregates
     * them into ForemanIpMismatchServerDTO objects. Each server is uniquely identified
     * by its associated properties such as serverId, serverName, and foremanHostName.
     *
     * @param rows the list of {@code ForemanIpMismatchDTO} objects to be grouped;
     *             if the list is null or empty, an empty list is returned
     * @return a list of {@code ForemanIpMismatchServerDTO} objects containing grouped
     *         and aggregated mismatch information
     */
    public static List<ForemanIpMismatchServerDTO> groupByServer(final List<ForemanIpMismatchDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        final Map<ServerKey, List<MismatchIp>> grouped = new LinkedHashMap<>();
        for (final ForemanIpMismatchDTO row : rows) {
            if (row == null) {
                continue;
            }
            final ServerKey key = new ServerKey(row.serverId(), row.serverName(), row.powerState(), row.foremanHostName(), row.foremanFDQN());
            grouped.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new MismatchIp(row.serverIp(), row.foremanIp(), row.status(), row.serverIF(), row.foremanIF(), row.serverNetwork(), row.foremanNetwork()));
        }

        return grouped.entrySet().stream()
                .map(entry -> new ForemanIpMismatchServerDTO(
                        entry.getKey().serverId(),
                        entry.getKey().serverName(),
                        entry.getKey().powerState(),
                        entry.getKey().foremanHostName(),
                        entry.getKey().foremanFQDN(),
                        entry.getValue()))
                .toList();
    }

    /**
     * Represents a unique key used to identify a server based on its properties.
     * This record encapsulates the server's basic attributes required for grouping
     * operations or comparisons.
     *
     * The {@code ServerKey} is generally utilized to uniquely identify a server
     * in contexts where servers are grouped or differentiated by their core
     * properties like {@code serverId}, {@code serverName}, and others.
     *
     * Fields:
     * - serverId: A unique identifier for the server.
     * - serverName: The name of the server.
     * - powerState: The current power state of the server (e.g., on, off).
     * - foremanHostName: The hostname of the server as managed by Foreman.
     * - foremanFQDN: The fully qualified domain name of the server in Foreman.
     */
    private record ServerKey(Long serverId, String serverName, String powerState, String foremanHostName, String foremanFQDN) {
    }

    /**
     * Represents an IP address mismatch between a server and a Foreman-managed system.
     * This record encapsulates the properties needed to describe discrepancies
     * between the server's and Foreman's IP configurations.
     *
     * Fields:
     * - serverIp: The IP address of the server as reported by the CMP system.
     * - foremanIp: The IP address of the server as reported by Foreman.
     * - status: The status of the mismatch (e.g., resolved, unresolved).
     * - serverIF: The name of the network interface on the server.
     * - foremanIF: The name of the network interface reported by Foreman.
     * - serverNetwork: The network identifier or name associated with the server's IP.
     * - foremanNetwork: The network identifier or name associated with Foreman's IP.
     */
    public record MismatchIp(
            @JsonProperty("server_ip")
            String serverIp,

            @JsonProperty("foreman_ip")
            String foremanIp,

            @JsonProperty("status")
            String status,

            @JsonProperty("server_if")
            String serverIF,

            @JsonProperty("foreman_if")
            String foremanIF,

            @JsonProperty("server_network")
            String serverNetwork,

            @JsonProperty("foreman_network")
            String foremanNetwork
    ) {}
}