package de.muenchen.mcmp.server;

import de.muenchen.mcmp.security.HasSpecialRole;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/server")
public class ServerController {
    private final ServerService serverService;

    @GetMapping
    public Page<ServerListDTO> getVisibleServers(@RequestParam(name = "offset") final int offset,
                                                 @RequestParam(name = "limit") final int limit,
                                                 @RequestParam(name = "sortBy") final String sortBy,
                                                 @RequestParam(name = "sortOrder") final String sortOrder,
                                                @RequestParam(name = "search", required = false) final String search,
                                                @RequestParam(name = "status", required = false) final List<String> status,
                                                @RequestParam(name = "os", required = false) final String os) {
        return serverService.getVisibleServers(offset, limit, sortBy, sortOrder, search, status, os);
    }

    @GetMapping("/{serverId}")
    public ServerFullDTO getServerById(@PathVariable("serverId") final Long serverId) {
        return serverService.getServerById(serverId);
    }

    @GetMapping("/appservice/{appserviceId}")
    public List<ServerListExtendedDTO> findServersByAppserviceId(@PathVariable("appserviceId") final Long appserviceId) {
        final List<ServerListExtendedDTO> servers = serverService.findServersByAppserviceId(appserviceId);
        if (servers.isEmpty()) {
            throw new NoSuchElementException("No servers found for the given appservice ID.");
        }
        return servers;
    }

    @HasSpecialRole
    @GetMapping("/patchnight/errors")
    public List<ServerFullDTO> getServersWithPatchnightErrors() {
        return serverService.findAllPatchnightErrorServers();
    }

    @GetMapping("/appservice/{appserviceId}/full")
    public List<ServerFullDTO> findFullServersByAppserviceId(@PathVariable("appserviceId") final Long appserviceId) {
        final List<ServerFullDTO> servers = serverService.findFullServersByAppserviceId(appserviceId);
        if (servers.isEmpty()) {
            throw new NoSuchElementException("No servers found for the given appservice ID.");
        }
        return servers;
    }

    @HasSpecialRole
    @GetMapping("/autocomplete")
    public List<ServerAutocompleteDTO> getServersForAutocomplete(@RequestParam(required = false) String query) {
        return serverService.searchServersForAutocomplete(query);
    }
}
