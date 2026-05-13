package de.muenchen.mcmp.network;

import de.muenchen.mcmp.types.EnvironmentType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/network")

public class NetworkController {

    private final NetworkService networkService;

    @GetMapping
    public List<NetworkDTO> getNetworks() {
        return networkService.getAllNetworks();
    }

    @GetMapping("/groups")
    public List<NetworkGroupDTO> getNetworkGroups() {
        return networkService.getAllNetworkGroups();
    }

    @GetMapping("/filtered_groups")
    public List<NetworkGroupDTO> getFilteredNetworkGroups(
            @RequestParam(name = "appserviceId") final Long appserviceId,
            @RequestParam(name = "database") final boolean database
    ){
        return networkService.findAvailableNetworkGroupsForAppservice(appserviceId, database);
    }

    @PreAuthorize("hasRole('ROLE_NETWORK')")
    @PutMapping("/groups")
    public NetworkGroupDTO updateNetworkGroup(@RequestBody NetworkGroupDTO networkGroupDTO) {
        return networkService.updateNetworkGroup(networkGroupDTO);
    }

    @PreAuthorize("hasRole('ROLE_NETWORK')")
    @PostMapping("/groups")
    public NetworkGroupDTO createNetworkGroup(@RequestBody NetworkGroupDTO networkGroupDTO) {
        return networkService.createNetworkGroup(networkGroupDTO);
    }

    @PreAuthorize("hasRole('ROLE_NETWORK')")
    @DeleteMapping("/groups/{id}")
    public void deleteNetworkGroup(@PathVariable("id") Long id) {
        networkService.deleteNetworkGroup(id);
    }

    @PreAuthorize("hasRole('ROLE_NETWORK')")
    @PostMapping("/assign")
    public void assignNetworkToGroup(@RequestParam Long networkId, @RequestParam(required = false) Long groupId) {
        networkService.assignNetworkToGroup(networkId, groupId);
    }

    @PreAuthorize("hasRole('ROLE_NETWORK')")
    @PostMapping("/appservices/{networkGroupId}")
    public void assignAppservicesToNetworkGroup(
            @PathVariable Long networkGroupId,
            @RequestBody List<Long> appserviceIds) {
        networkService.assignAppservicesToNetworkGroup(networkGroupId, appserviceIds);
    }

   /*
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception exception)
    {
        //log.error("error is:", exception);
        //exception.printStackTrace();//Log annotation
        return ResponseEntity.badRequest().body("Error:" + exception.getMessage());
    }*/


}
