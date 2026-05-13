package de.muenchen.mcmp.disk;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/disk")
public class DiskController {
    private final DiskService diskService;

    @GetMapping("/{serverId}")
    public List<DiskDTO> getDisksByServerId(@PathVariable("serverId") final Long serverId) {
        return diskService.getDisksByServerId(serverId);
    }
}

