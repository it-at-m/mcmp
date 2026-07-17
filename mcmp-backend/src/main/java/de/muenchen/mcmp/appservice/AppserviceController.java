package de.muenchen.mcmp.appservice;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/appservice")
public class AppserviceController {

    private final AppserviceService appserviceService;

    @GetMapping
    public Page<AppserviceListDTO> getAppservices(
            @RequestParam(name = "offset") final int offset,
            @RequestParam(name = "limit") final int limit,
            @RequestParam(name = "sortOrder") final String sortOrder,
            @RequestParam(name = "search", required = false) final String search,
            @RequestParam(name = "favorites", required = false, defaultValue = "false") final boolean favorites
    ) {
        return appserviceService.getVisibleAppservices(
                offset, limit, sortOrder, search, favorites
        );
    }

    @GetMapping("/{id}")
    public AppserviceDTO getAppservice(@PathVariable("id") final Long id) {
        return appserviceService.getVisibleAppservice(id);
    }

    @PutMapping("/{id}/favorite")
    public void addAppserviceToFavorites(@PathVariable("id") final Long id) {
        appserviceService.addAppserviceToFavorites(id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeAppserviceFromFavorites(@PathVariable("id") final Long id) {
        appserviceService.removeAppserviceFromFavorites(id);
    }
}
