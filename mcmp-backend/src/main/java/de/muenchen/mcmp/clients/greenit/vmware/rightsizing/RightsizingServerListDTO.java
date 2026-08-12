package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import java.util.List;

public record RightsizingServerListDTO(
            List<RightsizingServerDTO> servers
    ) {}
