package de.muenchen.mcmp.nic;

import de.muenchen.mcmp.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/nic")
public class NicController {
    private final NicService nicService;
    private final UserService userService;

    @GetMapping("/{serverId}")
    public List<NicDTO> getNicsByServerId(@PathVariable("serverId") final Long serverId) {
        return nicService.getNicsByServerId(serverId);
    }
}

