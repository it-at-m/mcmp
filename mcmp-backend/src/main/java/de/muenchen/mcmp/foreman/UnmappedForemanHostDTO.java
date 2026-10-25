package de.muenchen.mcmp.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnmappedForemanHostDTO(
        @JsonProperty("foreman")
        String foreman,

        @JsonProperty("foreman_id")
        Long foremanId,

        @JsonProperty("name")
        String name,

        @JsonProperty("hostname")
        String hostname,

        @JsonProperty("mac")
        String mac,

        @JsonProperty("operatingsystem")
        String operatingsystem,

        @JsonProperty("uuid")
        String uuid,

        @JsonProperty("dmi_product_uuid")
        String dmiProductUUID,

        @JsonProperty("dmi_product_serial_number")
        String dmiProductSerialNumber
) {}
