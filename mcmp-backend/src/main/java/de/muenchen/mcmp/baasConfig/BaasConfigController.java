package de.muenchen.mcmp.baasConfig;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/baasConfig")
public class BaasConfigController {

    private final BaasConfigService baasConfigService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<BaasConfigDTO> getBaasConfigs() {
        return baasConfigService.getBaasConfigs();
    }

//    @GetMapping("/{Id}")
//    public List<BaasConfigDTO> getBaasConfigById(@PathVariable Long Id)
//    {
//        return baasConfigService.getBaasConfigById(Id);
//    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody final BaasConfigDTO baasConfigDTO){
        baasConfigService.createBaasConfigEntry(baasConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public void update(@RequestBody final BaasConfigDTO baasConfigDTO)
    {
        baasConfigService.updateBaasConfigEntry(baasConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "/{id}")
    public void delete(@PathVariable("id") Long id)
    {
        baasConfigService.deleteBaasConfigEntry(id);
    }
}
