package de.muenchen.mcmp.clients.infoblox;

import de.muenchen.mcmp.network.NetworkService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/infoblox")
public class InfobloxController {

    private final NetworkService networkService;

    @GetMapping("/ping")
    public String ping() {
        return "Infoblox API is accessible";
    }

    //@PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/networks")
    @ResponseStatus(HttpStatus.CREATED)
    public void processNetworks(@Valid @RequestBody final List<NetworkRequestDTO> networkRequests) {
        networkService.processNetworkList(networkRequests);
    }
}