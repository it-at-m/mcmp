package de.muenchen.mcmp.awxConfig;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/awxConfig")
public class AwxConfigController {

    private final AwxConfigService awxConfigService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<AwxConfigDTO> getAwxConfigs() {
        return awxConfigService.getAwxConfigs();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody final AwxConfigDTO awxConfigDTO){
        awxConfigService.createAwxConfigEntry(awxConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public void update(@RequestBody final AwxConfigDTO awxConfigDTO)
    {
        awxConfigService.updateAwxConfigEntry(awxConfigDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "/{id}")
    public void delete(@PathVariable("id") Long id)
    {
        awxConfigService.deleteAwxConfigEntry(id);
    }
}
