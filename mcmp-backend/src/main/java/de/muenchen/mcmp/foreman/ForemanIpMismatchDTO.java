package de.muenchen.mcmp.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Data Transfer Object (DTO) that represents a mismatch between server and Foreman data.
 * This record is used to store and transfer information about discrepancies in IP addresses,
 * network interfaces, and related configurations for servers managed using Foreman.
 *
 * The properties in this object correspond to the attributes of both the server and the Foreman
 * system, allowing for detailed comparison between the two.
 */
public record ForemanIpMismatchDTO(
        @JsonProperty("server_id")
        Long serverId,

        @JsonProperty("server_name")
        String serverName,

        @JsonProperty("power_state")
        String powerState,

        @JsonProperty("foreman_host_name")
        String foremanHostName,

        @JsonProperty("foreman_fqdn")
        String foremanFDQN,

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