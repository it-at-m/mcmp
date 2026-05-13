package de.muenchen.mcmp.snowConfig;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/snowConfig")
public class SnowConfigController {

    private final SnowConfigService snowConfigService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<SnowConfigDTO> getSnowConfigs() {
        return snowConfigService.getSnowConfigs();
    }
    
//    @GetMapping("/{Id}")
//    public List<SnowConfigDTO> getSnowConfigById(@PathVariable Long Id)
//    {
//        return snowConfigService.getSnowConfigById(Id);
//    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody final SnowConfigDTO snowConfigDTO){
        snowConfigService.createSnowConfigEntry(snowConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public void update(@RequestBody final SnowConfigDTO snowConfigDTO)
    {
        snowConfigService.updateSnowConfigEntry(snowConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "/{id}")
    public void delete(@PathVariable("id") Long id)
    {
        snowConfigService.deleteSnowConfigEntry(id);
    }
}
