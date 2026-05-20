package de.muenchen.mcmp.cloud;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/cloud")
public class CloudController {

    private final CloudService cloudService;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public List<CloudDTO> getClouds() {
        return cloudService.getClouds();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody final CloudDTO cloudDTO){
        cloudService.createCloudEntry(cloudDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping
    public void update(@RequestBody final CloudDTO cloudDTO)
    {
        cloudService.updateCloudEntry(cloudDTO);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "/{id}")
    public void delete(@PathVariable("id") Long id)
    {
        cloudService.deleteCloudEntry(id);
    }
}
