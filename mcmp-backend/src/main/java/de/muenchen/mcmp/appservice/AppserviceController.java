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
            @RequestParam(name = "search", required = false) final String search
    ) {
        return appserviceService.getVisibleAppservices(
                offset, limit, sortOrder, search
        );
    }

    @GetMapping("/{id}")
    public AppserviceDTO getAppservice(@PathVariable("id") final Long id) {
        return appserviceService.getVisibleAppservice(id);
    }

   /*
    @GetMapping("/all")
    public List<AppserviceDTO> showAppservices() {

        return appserviceService.getAppservices();
    }*/
}
