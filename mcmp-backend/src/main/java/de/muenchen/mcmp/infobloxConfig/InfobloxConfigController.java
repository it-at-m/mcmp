package de.muenchen.mcmp.infobloxConfig;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/infobloxConfig")
public class InfobloxConfigController {

    private final InfobloxConfigService infobloxConfigService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<InfobloxConfigDTO> getInfobloxConfigs() {
        return infobloxConfigService.getInfobloxConfigs();
    }

//    @GetMapping("/{Id}")
//    public List<InfobloxConfigDTO> getInfobloxConfigById(@PathVariable Long Id)
//    {
//        return infobloxConfigService.getInfobloxConfigById(Id);
//    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody final InfobloxConfigDTO infobloxConfigDTO){
        infobloxConfigService.createInfobloxConfigEntry(infobloxConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public void update(@RequestBody final InfobloxConfigDTO infobloxConfigDTO)
    {
        infobloxConfigService.updateInfobloxConfigEntry(infobloxConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "/{id}")
    public void delete(@PathVariable("id") Long id)
    {
        infobloxConfigService.deleteInfobloxConfigEntry(id);
    }
}
